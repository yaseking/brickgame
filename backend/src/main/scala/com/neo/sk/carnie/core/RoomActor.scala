package com.neo.sk.carnie.core

import java.awt.event.KeyEvent

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.carnie.paperClient.Protocol._
import org.slf4j.LoggerFactory
import com.neo.sk.carnie.paperClient._
import com.neo.sk.carnie.Boot.roomManager
import com.neo.sk.carnie.core.GameRecorder.RecordData

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import concurrent.duration._
import com.neo.sk.carnie.Boot.{executor, scheduler, timeout, tokenActor}
import com.neo.sk.carnie.core.TokenActor.AskForToken
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.utils.EsheepClient

import scala.concurrent.Future

/**
  * Created by dry on 2018/10/12.
  **/
object RoomActor {

  private val log = LoggerFactory.getLogger(this.getClass)
  val border = Point(BorderSize.w, BorderSize.h)
  private val fullSize = (BorderSize.w - 2) * (BorderSize.h - 2)

  private final case object BehaviorChangeKey

  private final case object SyncKey

  sealed trait Command

  case class UserActionOnServer(id: String, action: Protocol.UserAction) extends Command

  case class JoinRoom(id: String, name: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command

  case class LeftRoom(id: String, name: String) extends Command

  case class UserDead(users: List[(String, Int, Int)]) extends Command with RoomManager.Command // (id, kill, area)

  private case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  case class WatchGame(playerId: String, userId: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command

  case class WatcherLeftRoom(userId: String) extends Command

  final case class UserLeft[U](actorRef: ActorRef[U]) extends Command

  private case object Sync extends Command

  case class UserInfo(name: String, startTime: Long, stopTime: Long)

  final case class SwitchBehavior(
                                   name: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error")
                                 ) extends Command

  case class TimeOut(msg: String) extends Command

  def create(roomId: Int): Behavior[Command] = {
    log.debug(s"Room Actor-$roomId start...")
    Behaviors.setup[Command] { ctx =>
      Behaviors.withTimers[Command] {
        implicit timer =>
          val subscribersMap = mutable.HashMap[String, ActorRef[WsSourceProtocol.WsMsgSource]]()
          val userMap = mutable.HashMap[String, UserInfo]()
          val watcherMap = mutable.HashMap[String, String]()
          val grid = new GridOnServer(border)
          val winStandard = fullSize * 0.1//0.4
          //            implicit val sendBuffer = new MiddleBufferInJvm(81920)
          timer.startPeriodicTimer(SyncKey, Sync, Protocol.frameRate millis)
          idle(roomId, grid, userMap, mutable.Set.empty[String], watcherMap, subscribersMap, 0L, mutable.ArrayBuffer[(Long, GameEvent)](), winStandard)
      }
    }
  }

  def idle(
            roomId: Int, grid: GridOnServer,
            userMap: mutable.HashMap[String, UserInfo],
            userDeadList: mutable.Set[String],
            watcherMap: mutable.HashMap[String, String], //(watchId, playerId)
            subscribersMap: mutable.HashMap[String, ActorRef[WsSourceProtocol.WsMsgSource]],
            tickCount: Long,
            gameEvent: mutable.ArrayBuffer[(Long, GameEvent)],
            winStandard: Double
          )(
            implicit timer: TimerScheduler[Command]
          ): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case m@JoinRoom(id, name, subscriber) =>
          log.info(s"got JoinRoom $m")
          userMap.put(id, UserInfo(name, System.currentTimeMillis(), -1L))
          subscribersMap.put(id, subscriber)
          log.debug(s"subscribersMap: $subscribersMap")
          ctx.watchWith(subscriber, UserLeft(subscriber))
          grid.addSnake(id, roomId, name)
          dispatchTo(subscribersMap, id, Protocol.Id(id))
//          val gridData = grid.getGridData
//          dispatch(subscribersMap, gridData)
//          idle(roomId, grid, userMap, userDeadList, watcherMap, subscribersMap, tickCount + 1, gameEvent, winStandard)
          gameEvent += ((grid.frameCount, JoinEvent(id, name)))
          Behaviors.same

        case m@WatchGame(playerId, userId, subscriber) =>
          log.info(s"got: $m")
          val truePlayerId = if (playerId == "unknown") userMap.head._1 else playerId
          watcherMap.put(userId, truePlayerId)
          subscribersMap.put(userId, subscriber)
          ctx.watchWith(subscriber, WatcherLeftRoom(userId))
          dispatchTo(subscribersMap, userId, Protocol.Id(truePlayerId))
          val gridData = grid.getGridData
          dispatch(subscribersMap, gridData)
          Behaviors.same

        case UserDead(users) =>
          users.foreach { u =>
            val id = u._1
            if(userMap.get(id).nonEmpty) {
              val name = userMap(id).name
              val startTime = userMap(id).startTime
              gameEvent += ((grid.frameCount, LeftEvent(id, name)))
              dispatchTo(subscribersMap, id, DeadPage(id, BaseScore(u._2, u._3, startTime, System.currentTimeMillis())))
            }
            userDeadList += id
          }
          Behaviors.same

        case LeftRoom(id, name) =>
          log.debug(s"LeftRoom:::$id")
          if(userDeadList.contains(id)) userDeadList -= id
          grid.removeSnake(id)
          subscribersMap.get(id).foreach(r => ctx.unwatch(r))
          userMap.remove(id)
          subscribersMap.remove(id)
          watcherMap.filter(_._2 == id).keySet.foreach { i =>
            subscribersMap.remove(i)
          }
          if (!userDeadList.contains(id)) gameEvent += ((grid.frameCount, LeftEvent(id, name))) else userDeadList -= id
          if (userMap.isEmpty) Behaviors.stopped else Behaviors.same

        case WatcherLeftRoom(uid) =>
          log.debug(s"WatcherLeftRoom:::$uid")
          subscribersMap.get(uid).foreach(r => ctx.unwatch(r))//
          subscribersMap.remove(uid)
          watcherMap.remove(uid)
          Behaviors.same

        case UserLeft(actor) =>
          log.debug(s"UserLeft:::")
          val subscribersOpt = subscribersMap.find(_._2.equals(actor))
          if(subscribersOpt.nonEmpty){
            val (id, _) = subscribersOpt.get
            log.debug(s"got Terminated id = $id")
            //            if(userDeadList.contains(id)) userDeadList -= id
            val name = userMap.get(id).head.name
            subscribersMap.remove(id)
            userMap.remove(id)
            grid.removeSnake(id).foreach { s => dispatch(subscribersMap, Protocol.SnakeLeft(id, s.name)) }
            roomManager ! RoomManager.UserLeft(id)
            gameEvent += ((grid.frameCount, LeftEvent(id, name)))
            if (!userDeadList.contains(id)) gameEvent += ((grid.frameCount, LeftEvent(id, name))) else userDeadList -= id
          }
          if (userMap.isEmpty) Behaviors.stopped else Behaviors.same

        case UserActionOnServer(id, action) =>
          action match {
            case Key(_, keyCode, frameCount, actionId) =>
              if (keyCode == KeyEvent.VK_SPACE && userDeadList.contains(id)) {
                grid.addSnake(id, roomId, userMap.getOrElse(id, UserInfo("", -1L, -1L)).name)
                gameEvent += ((grid.frameCount, JoinEvent(id, userMap(id).name)))
                watcherMap.filter(_._2 == id).foreach { w =>
                  dispatchTo(subscribersMap, w._1, Protocol.ReStartGame)
                }
                gameEvent += ((grid.frameCount, SpaceEvent(id)))
                userDeadList -= id
              } else {
                val realFrame = if (frameCount >= grid.frameCount) frameCount else grid.frameCount
                grid.addActionWithFrame(id, keyCode, realFrame)
                dispatch(subscribersMap, Protocol.SnakeAction(id, keyCode, realFrame, actionId))
              }
            case SendPingPacket(_, createTime) =>
              dispatchTo(subscribersMap, id, Protocol.ReceivePingPacket(createTime))

            case NeedToSync(_) =>
              dispatchTo(subscribersMap, id, grid.getGridData)

            case _ =>
          }
          Behaviors.same


        case Sync =>
          val frame = grid.frameCount //即将执行改帧的数据
          val shouldNewSnake = if (grid.waitingListState) true else if (tickCount % 20 == 5) true else false
          val finishFields = grid.updateInService(shouldNewSnake) //frame帧的数据执行完毕
          val newData = grid.getGridData
          var newField: List[FieldByColumn] = Nil
          val killedSkData = grid.getKilledSkData

          newData.killHistory.foreach { i =>
            if (i.frameCount + 1 == newData.frameCount) {
              dispatch(subscribersMap, Protocol.SomeOneKilled(i.killedId, userMap(i.killedId).name, i.killerName))
            }
          }

          killedSkData.killedSkInfo.foreach { i =>
            val msgFuture: Future[String] = tokenActor ? AskForToken
            msgFuture.map { token =>
              EsheepClient.inputBatRecord(i.id, i.nickname, i.killing, 1, i.score, "", i.startTime, i.endTime, token)
            }
          }
          grid.cleanKilledSkData()

          if (shouldNewSnake) dispatch(subscribersMap, newData)
          else if (finishFields.nonEmpty) {
            val finishUsers = finishFields.map(_._1)
            finishUsers.foreach(u => dispatchTo(subscribersMap, u, newData))
            newField = finishFields.map { f =>
              FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
                ScanByColumn(y.toInt, Tool.findContinuous(target.map(_.x.toInt).toArray.sorted))
              }.toList)
            }
            userMap.filterNot(user => finishUsers.contains(user._1)).foreach(u => dispatchTo(subscribersMap, u._1, NewFieldInfo(grid.frameCount, newField)))
          }

          if (grid.currentRank.nonEmpty && grid.currentRank.head.area >= winStandard) { //判断是否胜利
            log.info(s"currentRank: ${grid.currentRank}")
            log.debug("winwinwinwin!!")
            val finalData = grid.getGridData
            grid.cleanData()
            dispatch(subscribersMap, Protocol.SomeOneWin(userMap(grid.currentRank.head.id).name, finalData))
            gameEvent += ((grid.frameCount, Protocol.SomeOneWin(userMap(grid.currentRank.head.id).name, finalData)))
            userMap.foreach { u =>
              gameEvent += ((grid.frameCount, LeftEvent(u._1, u._2.name)))
              userDeadList += u._1
            }
          }

          if(finishFields.nonEmpty && shouldNewSnake) {
            newField = finishFields.map { f =>
              FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
                ScanByColumn(y.toInt, Tool.findContinuous(target.map(_.x.toInt).toArray.sorted))
              }.toList)
            }

//            if (grid.currentRank.nonEmpty && grid.currentRank.head.area >= winStandard) { //判断是否胜利
//              log.debug("winwinwinwin!!!!!!!!!!")
//              val finalData = grid.getGridData
//              grid.cleanData()
//              gameEvent += ((grid.frameCount, Protocol.SomeOneWin(userMap(grid.currentRank.head.id).name, finalData)))
//              userMap.foreach { u =>
//                gameEvent += ((grid.frameCount, LeftEvent(u._1, u._2.name)))
//              }
//            }
          }

          if (tickCount % 10 == 3) dispatch(subscribersMap, Protocol.Ranks(grid.currentRank))
          val newWinStandard = if (grid.currentRank.nonEmpty) { //胜利条件的跳转
            val maxSize = grid.currentRank.head.area
            if ((maxSize + fullSize * 0.1) < winStandard) Math.max(fullSize * (0.4 - userMap.size * 0.05), 0.15) else winStandard
          } else winStandard

          //for gameRecorder...
          val actionEvent = grid.getDirectionEvent(frame)
          val joinOrLeftEvent = gameEvent.filter(_._1 == frame)
//            .map {
//            case (f, JoinEvent(id, None)) => (f, JoinEvent(id, grid.snakes.get(id)))
//            case other => other
//          }
          val baseEvent = if (tickCount % 10 == 3) RankEvent(grid.currentRank) :: (actionEvent ::: joinOrLeftEvent.map(_._2).toList) else actionEvent ::: joinOrLeftEvent.map(_._2).toList
          gameEvent --= joinOrLeftEvent
          val snapshot = Snapshot(newData.snakes, newData.bodyDetails, newData.fieldDetails, newData.killHistory)
          //          val snapshot = Snapshot(newData.snakes, newData.bodyDetails, newData.fieldDetails, newData.killHistory)
          val recordData = if (finishFields.nonEmpty) RecordData(frame, (EncloseEvent(newField) :: baseEvent, snapshot)) else RecordData(frame, (baseEvent, snapshot))
          getGameRecorder(ctx, roomId, grid) ! recordData
          idle(roomId, grid, userMap, userDeadList, watcherMap, subscribersMap, tickCount + 1, gameEvent, newWinStandard)

        case ChildDead(child, childRef) =>
          log.debug(s"roomActor 不再监管 gameRecorder:$child,$childRef")
          ctx.unwatch(childRef)
          Behaviors.same

        case _ =>
          log.warn(s"${ctx.self.path} recv a unknow msg=$msg")
          Behaviors.same
      }
    }

  }

  def dispatchTo(subscribers: mutable.HashMap[String, ActorRef[WsSourceProtocol.WsMsgSource]], id: String, gameOutPut: Protocol.GameMessage): Unit = {
    subscribers.get(id).foreach {
      _ ! gameOutPut
    }
  }

  def dispatch(subscribers: mutable.HashMap[String, ActorRef[WsSourceProtocol.WsMsgSource]], gameOutPut: Protocol.GameMessage) = {
    //    log.info(s"dispatch:::$gameOutPut")
    subscribers.values.foreach {
      _ ! gameOutPut
    }
  }

  private[this] def switchBehavior(ctx: ActorContext[Command],
                                   behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
                                  (implicit stashBuffer: StashBuffer[Command],
                                   timer: TimerScheduler[Command]) = {
    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }

  private def getGameRecorder(ctx: ActorContext[Command], roomId: Int, grid: GridOnServer): ActorRef[GameRecorder.Command] = {
    val childName = "gameRecorder"
    ctx.child(childName).getOrElse {
      val newData = grid.getGridData
      val actor = ctx.spawn(GameRecorder.create(roomId, Snapshot(newData.snakes, newData.bodyDetails, newData.fieldDetails, newData.killHistory),
        GameInformation(roomId, System.currentTimeMillis(), 0, grid.frameCount)), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.upcast[GameRecorder.Command]
  }


}
