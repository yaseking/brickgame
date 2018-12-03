package com.neo.sk.carnie.core

import java.awt.event.KeyEvent

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.carnie.paperClient.Protocol._
import org.slf4j.LoggerFactory
import com.neo.sk.carnie.paperClient._
import com.neo.sk.carnie.Boot.roomManager
import com.neo.sk.carnie.core.GameRecorder.RecordData
import com.neo.sk.carnie.common.AppSettings

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

  private val upperLimit = AppSettings.upperLimit.toFloat

  private val lowerLimit = AppSettings.lowerLimit.toFloat

  private val fullSize = (BorderSize.w - 2) * (BorderSize.h - 2)

  private val classify = 5

  private final case object BehaviorChangeKey

  private final case object SyncKey

  sealed trait Command

  case class UserActionOnServer(id: String, action: Protocol.UserAction) extends Command

  case class JoinRoom(id: String, name: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource], img: Int) extends Command

  case class LeftRoom(id: String, name: String) extends Command

  case class UserDead(users: List[(String, Int, Int)]) extends Command with RoomManager.Command // (id, kill, area)

  private case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  case class WatchGame(playerId: String, userId: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command

  case class WatcherLeftRoom(userId: String) extends Command

  final case class UserLeft[U](actorRef: ActorRef[U]) extends Command

  private case object Sync extends Command

  case class UserInfo(name: String, startTime: Long, group: Long)

  final case class SwitchBehavior(
                                   name: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error")
                                 ) extends Command

  case class TimeOut(msg: String) extends Command

  def create(roomId: Int, mode: Int): Behavior[Command] = {
    log.debug(s"Room Actor-$roomId start...")
    Behaviors.setup[Command] { ctx =>
      Behaviors.withTimers[Command] {
        implicit timer =>
          val subscribersMap = mutable.HashMap[String, ActorRef[WsSourceProtocol.WsMsgSource]]()
          val userMap = mutable.HashMap[String, UserInfo]()
          val watcherMap = mutable.HashMap[String, (String, Long)]()
          val grid = new GridOnServer(border)
          val winStandard = fullSize * 0.1//0.4
          //            implicit val sendBuffer = new MiddleBufferInJvm(81920)
          val frameRate = mode match {
            case 2 => Protocol.frameRate2
            case _ => Protocol.frameRate1
          }
          log.info(s"frameRate: $frameRate")
          timer.startPeriodicTimer(SyncKey, Sync, frameRate millis)
          idle(0L, roomId, mode, grid, userMap, mutable.HashMap[Long, Set[String]](), mutable.Set.empty[String], watcherMap, subscribersMap, 0L, mutable.ArrayBuffer[(Long, GameEvent)](), winStandard)
      }
    }
  }

  def idle( index: Long,
            roomId: Int,
            mode: Int,
            grid: GridOnServer,
            userMap: mutable.HashMap[String, UserInfo],
            userGroup: mutable.HashMap[Long, Set[String]],
            userDeadList: mutable.Set[String],
            watcherMap: mutable.HashMap[String, (String, Long)], //(watchId, (playerId, GroupId))
            subscribersMap: mutable.HashMap[String, ActorRef[WsSourceProtocol.WsMsgSource]],
            tickCount: Long,
            gameEvent: mutable.ArrayBuffer[(Long, GameEvent)],
            winStandard: Double,
            firstComeList: List[String] = List.empty[String],
            headImgList: mutable.HashMap[String, Int] = mutable.HashMap.empty[String, Int]
          )(
            implicit timer: TimerScheduler[Command]
          ): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case m@JoinRoom(id, name, subscriber, img) =>
          log.info(s"got JoinRoom $m")
          userMap.put(id, UserInfo(name, System.currentTimeMillis(), index%classify))
          subscribersMap.put(id, subscriber)
          log.debug(s"subscribersMap: $subscribersMap")
//          ctx.watchWith(subscriber, UserLeft(subscriber))
          grid.addSnake(id, roomId, name, img)
          dispatchTo(subscribersMap, id, Protocol.Id(id))
          userGroup.get(index%classify) match {
            case Some(s)=> userGroup.update(index%classify, s + id)
            case None => userGroup.put(index%classify, Set(id))
          }
//          val gridData = grid.getGridData
//          dispatch(subscribersMap, gridData)
//          idle(roomId, grid, userMap, userDeadList, watcherMap, subscribersMap, tickCount + 1, gameEvent, winStandard)
          gameEvent += ((grid.frameCount, JoinEvent(id, name)))
          headImgList.put(id, img)
//          log.debug(s"headImgList after join:$headImgList")
          idle(index + 1, roomId, mode, grid, userMap, userGroup, userDeadList, watcherMap, subscribersMap, tickCount, gameEvent, winStandard, id::firstComeList, headImgList)

        case m@WatchGame(playerId, userId, subscriber) =>
          log.info(s"got: $m")
          val truePlayerId = if (playerId == "unknown") userMap.head._1 else playerId
          watcherMap.put(userId, (truePlayerId, index%classify))
          subscribersMap.put(userId, subscriber)
          ctx.watchWith(subscriber, WatcherLeftRoom(userId))
          dispatchTo(subscribersMap, userId, Protocol.Id(truePlayerId))
          val gridData = grid.getGridData
          dispatch(subscribersMap, gridData)
          userGroup.get(index%classify) match {
            case Some(s)=> userGroup.update(index%classify, s + userId)
            case None => userGroup.put(index%classify, Set(userId))
          }
//          Behaviors.same
          idle(index + 1, roomId, mode, grid, userMap, userGroup, userDeadList, watcherMap, subscribersMap, tickCount, gameEvent, winStandard, firstComeList, headImgList)

        case UserDead(users) =>
          users.foreach { u =>
            val id = u._1
            if(userMap.get(id).nonEmpty) {
              val name = userMap(id).name
              val startTime = userMap(id).startTime
              grid.cleanSnakeTurnPoint(id)
              gameEvent += ((grid.frameCount, LeftEvent(id, name)))
              log.debug(s"user ${id} dead===kill::${u._2}, area::${u._3}, starTime:$startTime")
              val endTime = System.currentTimeMillis()
              dispatchTo(subscribersMap, id, Protocol.DeadPage(id, u._2, u._3, startTime, endTime))
              //上传战绩
              val msgFuture: Future[String] = tokenActor ? AskForToken
              msgFuture.map { token =>
                EsheepClient.inputBatRecord(id, name, u._2, 1, u._3.toFloat*100 / fullSize, "", startTime, endTime, token)
              }
            }
            userDeadList += id

          }
          Behaviors.same

        case LeftRoom(id, name) =>
          log.debug(s"LeftRoom:::$id")
          if(userDeadList.contains(id)) userDeadList -= id
          grid.removeSnake(id)
          userMap.filter(_._1 == id).foreach{ u =>
            userGroup.get(u._2.group) match {
              case Some(s) => userGroup.update(u._2.group,s - id)
              case None => userGroup.put(u._2.group, Set.empty)
            }
          }
          subscribersMap.get(id).foreach(r => ctx.unwatch(r))
          userMap.remove(id)
          subscribersMap.remove(id)
          dispatch(subscribersMap, Protocol.UserLeft(id))
          watcherMap.filter(_._2._1 == id).keySet.foreach { i =>
            subscribersMap.remove(i)
          }
          if (headImgList.contains(id)) headImgList.remove(id)

          if (!userDeadList.contains(id)) gameEvent += ((grid.frameCount, LeftEvent(id, name))) else userDeadList -= id
          if (userMap.isEmpty) Behaviors.stopped else Behaviors.same

        case WatcherLeftRoom(uid) =>
          log.debug(s"WatcherLeftRoom:::$uid")
          subscribersMap.get(uid).foreach(r => ctx.unwatch(r))//
          subscribersMap.remove(uid)
          watcherMap.remove(uid)
          Behaviors.same

        case UserLeft(actor) =>
//          ctx.unwatch(actor)
          log.debug(s"UserLeft:::")
          val subscribersOpt = subscribersMap.find(_._2.equals(actor))
          if(subscribersOpt.nonEmpty){
            val (id, _) = subscribersOpt.get
            log.debug(s"got Terminated id = $id")
            //            if(userDeadList.contains(id)) userDeadList -= id
            val name = userMap.get(id).head.name
            userMap.filter(_._1 == id).foreach{ u =>
              userGroup.get(u._2.group) match {
                case Some(s) => userGroup.update(u._2.group,s - id)
                case None => userGroup.put(u._2.group, Set.empty)
              }
            }
            if (headImgList.contains(id)) headImgList.remove(id)
            subscribersMap.remove(id)
            userMap.remove(id)
            grid.cleanSnakeTurnPoint(id)
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
                val name = userMap.getOrElse(id, UserInfo("unknown", -1, -1)).name
                userMap.put(id, UserInfo(name, System.currentTimeMillis(), -1L))
                log.debug(s"headImgList::$headImgList")
                if (headImgList.contains(id)) {
                  grid.addSnake(id, roomId, userMap.getOrElse(id, UserInfo("", -1L, -1L)).name, headImgList(id))
                } else {
                  log.error(s"can not find headImg of $id")
                  grid.addSnake(id, roomId, userMap.getOrElse(id, UserInfo("", -1L, -1L)).name, 0)
                }
                gameEvent += ((grid.frameCount, JoinEvent(id, userMap(id).name)))
                watcherMap.filter(_._2._1 == id).foreach { w =>
                  dispatchTo(subscribersMap, w._1, Protocol.ReStartGame)
                }
                gameEvent += ((grid.frameCount, SpaceEvent(id)))
                userDeadList -= id
              } else {
                val realFrame = if (frameCount >= grid.frameCount) frameCount else grid.frameCount
                grid.addActionWithFrame(id, keyCode, realFrame)
//                userMap.foreach(u => dispatchTo(subscribersMap, u._1, Protocol.SnakeAction(id, keyCode, realFrame, actionId)))
                dispatch(subscribersMap, Protocol.SnakeAction(id, keyCode, realFrame, actionId))
              }
            case SendPingPacket(_, createTime) =>
              dispatchTo(subscribersMap, id, Protocol.ReceivePingPacket(createTime))

            case NeedToSync(_) =>
//              log.debug("receive NeedToSync")
              dispatchTo(subscribersMap, id, grid.getGridData)

            case _ =>
          }
          Behaviors.same


        case Sync =>
          val frame = grid.frameCount //即将执行改帧的数据
          val shouldNewSnake = if (grid.waitingListState) true else false
          val shouldSync = if (tickCount % 20 == 1) true else false
//          val waitingSnakesList = grid.waitingJoinList
          val finishFields = grid.updateInService(shouldNewSnake) //frame帧的数据执行完毕
          val newData = grid.getGridData
          var newField: List[FieldByColumn] = Nil
//          val killedSkData = grid.getKilledSkData
          grid.killHistory.map(k => Kill(k._1, k._2._1, k._2._2, k._2._3)).toList.foreach {
            i =>
              if (i.frameCount + 1 == newData.frameCount) {
                dispatch(subscribersMap, Protocol.SomeOneKilled(i.killedId, userMap(i.killedId).name, i.killerName))
                gameEvent += ((grid.frameCount, Protocol.SomeOneKilled(i.killedId, userMap(i.killedId).name, i.killerName)))
              }
          }

//          killedSkData.killedSkInfo.foreach { i =>
//            val msgFuture: Future[String] = tokenActor ? AskForToken
//            msgFuture.map { token =>
//              EsheepClient.inputBatRecord(i.id, i.nickname, i.killing, 1, i.score, "", i.startTime, i.endTime, token)
//            }
//          }
//          grid.cleanKilledSkData()
          if(grid.newInfo.nonEmpty) {
            newField = grid.newInfo.map(n => (n._1, n._3)).map { f =>
              FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
                ScanByColumn(y.toInt, Tool.findContinuous(target.map(_.x.toInt).toArray.sorted))//read
              }.toList)
            }
            dispatch(subscribersMap, NewSnakeInfo(grid.frameCount, grid.newInfo.map(_._2), newField))
            grid.newInfo = Nil
          }

          firstComeList.foreach { id =>
            dispatchTo(subscribersMap, id, newData)
          }


          //错峰发送
          if (shouldSync) {
            val chooseGroup = (tickCount % 100) / 20
            userGroup.get(chooseGroup).foreach {g =>
              if (g.nonEmpty) {
                dispatch(subscribersMap.filter(s => g.contains(s._1)), newData)
              }
            }
//            dispatch(subscribersMap, newData)
          }
          if (finishFields.nonEmpty) {
            val finishUsers = finishFields.map(_._1)
            //test
//            finishUsers.foreach(u => dispatchTo(subscribersMap, u, newData))
//            watcherMap.filter(u => finishUsers.contains(u._1)).foreach(u => dispatchTo(subscribersMap, u._1, newData))
            newField = finishFields.map { f =>
              FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
                ScanByColumn(y.toInt, Tool.findContinuous(target.map(_.x.toInt).toArray.sorted))//read
              }.toList)
            }
//            userMap.filterNot(user => finishUsers.contains(user._1)).foreach(u => dispatchTo(subscribersMap, u._1, NewFieldInfo(grid.frameCount, newField)))
            //test
            userMap.foreach(u => dispatchTo(subscribersMap, u._1, NewFieldInfo(grid.frameCount, newField)))
            watcherMap.foreach(u => dispatchTo(subscribersMap, u._1, NewFieldInfo(grid.frameCount, newField)))
          }

          if (grid.currentRank.nonEmpty && grid.currentRank.head.area >= winStandard) { //判断是否胜利
            log.info(s"currentRank: ${grid.currentRank}")
            log.debug("winwinwinwin!!")
            val finalData = grid.getGridData
            grid.cleanData()
            dispatch(subscribersMap, Protocol.SomeOneWin(userMap(grid.currentRank.head.id).name, finalData))
            dispatchTo(subscribersMap, grid.currentRank.head.id, Protocol.WinnerBestScore(grid.currentRank.head.area))
            gameEvent += ((grid.frameCount, Protocol.SomeOneWin(userMap(grid.currentRank.head.id).name, finalData)))
            userMap.foreach { u =>
              if (!userDeadList.contains(u._1)) {
                gameEvent += ((grid.frameCount, LeftEvent(u._1, u._2.name)))
                userDeadList += u._1
              }

            }
          }

//          if(finishFields.nonEmpty && shouldSync) {
//            newField = finishFields.map { f =>
//              FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
//                ScanByColumn(y.toInt, Tool.findContinuous(target.map(_.x.toInt).toArray.sorted))
//              }.toList)
//            }
//          }

          val count = tickCount % 10
           if(count % 2 == 0) {
             userGroup.get(count / 2).foreach {g =>
               if (g.nonEmpty) {
                 dispatch(subscribersMap.filter(s => g.contains(s._1)), Protocol.Ranks(grid.currentRank))
               }
           }
          }
//          if (tickCount % 10 == 3) dispatch(subscribersMap, Protocol.Ranks(grid.currentRank))
          val newWinStandard = if (grid.currentRank.nonEmpty) { //胜利条件的跳转
            val maxSize = grid.currentRank.head.area
            if ((maxSize + fullSize * 0.1) < winStandard) Math.max(fullSize * (upperLimit - userMap.size * lowerLimit), 0.15) else winStandard
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
          val snapshot = Snapshot(newData.snakes, newData.bodyDetails, newData.fieldDetails)
          //          val snapshot = Snapshot(newData.snakes, newData.bodyDetails, newData.fieldDetails, newData.killHistory)
          val recordData = if (finishFields.nonEmpty) RecordData(frame, (EncloseEvent(newField) :: baseEvent, snapshot)) else RecordData(frame, (baseEvent, snapshot))
          if (grid.snakes.nonEmpty || ctx.child("gameRecorder").nonEmpty) getGameRecorder(ctx, roomId, grid, mode) ! recordData
          idle(index, roomId, mode, grid, userMap, userGroup, userDeadList, watcherMap, subscribersMap, tickCount + 1, gameEvent, newWinStandard, headImgList = headImgList)

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

  private def getGameRecorder(ctx: ActorContext[Command], roomId: Int, grid: GridOnServer, mode: Int): ActorRef[GameRecorder.Command] = {
    val childName = "gameRecorder"
    ctx.child(childName).getOrElse {
      val newData = grid.getGridData
      val actor = ctx.spawn(GameRecorder.create(roomId, Snapshot(newData.snakes, newData.bodyDetails, newData.fieldDetails),
        GameInformation(roomId, System.currentTimeMillis(), 0, grid.frameCount, mode)), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.upcast[GameRecorder.Command]
  }


}
