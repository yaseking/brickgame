package com.neo.sk.carnie.core

import java.awt.event.KeyEvent

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.carnie.paperClient.Protocol._
import org.slf4j.LoggerFactory
import com.neo.sk.carnie.paperClient._
import org.seekloud.byteobject._
import com.neo.sk.carnie.Boot.roomManager

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import concurrent.duration._

/**
  * Created by dry on 2018/10/12.
  **/
object RoomActor {

  private val log = LoggerFactory.getLogger(this.getClass)
  val border = Point(BorderSize.w, BorderSize.h)
  private val fullSize = (BorderSize.w - 2) * (BorderSize.h - 2)
  private var winStandard = (BorderSize.w - 2) * (BorderSize.h - 2) * 0.7

  private final val InitTime = Some(5.minutes)
  private final case object BehaviorChangeKey
  private final case object SyncKey

  sealed trait Command
  case class UserActionOnServer(id: Long, action: Protocol.UserAction) extends Command
  case class JoinRoom(id:Long, name:String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command
  case class LeftRoom(id:Long, name:String) extends Command


  final case class UserLeft[U](actorRef:ActorRef[U]) extends Command

  private case object Sync extends Command


  final case class SwitchBehavior(
                                   name: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error")
                                 ) extends Command

  case class TimeOut(msg:String) extends Command

  private[this] def switchBehavior(ctx: ActorContext[Command],
                                   behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None,timeOut: TimeOut  = TimeOut("busy time error"))
                                  (implicit stashBuffer: StashBuffer[Command],
                                   timer:TimerScheduler[Command]) = {
    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey,timeOut,_))
    stashBuffer.unstashAll(ctx,behavior)
  }

  def create(roomId: Int):Behavior[Command] ={
    log.debug(s"Room Actor-${roomId} start...")
    Behaviors.setup[Command]{
      ctx =>
        Behaviors.withTimers[Command]{
          implicit timer =>
            val subscribersMap = mutable.HashMap[Long,ActorRef[WsSourceProtocol.WsMsgSource]]()
            val userMap = mutable.HashMap[Long, String]()
            val grid = new GridOnServer(border)
//            implicit val sendBuffer = new MiddleBufferInJvm(81920)
            timer.startPeriodicTimer(SyncKey, Sync, Protocol.frameRate millis)
            idle(roomId, grid, userMap, subscribersMap, 0L)
        }
    }
  }

  def idle(
            roomId: Int, grid: GridOnServer,
            userMap:mutable.HashMap[Long,String],
            subscribersMap:mutable.HashMap[Long,ActorRef[WsSourceProtocol.WsMsgSource]],
            tickCount:Long
          )(
            implicit timer:TimerScheduler[Command]
          ):Behavior[Command] = {
    Behaviors.receive{(ctx,msg) =>
      msg match {
        case JoinRoom(id, name, subscriber) =>
          log.info(s"got $msg")
          userMap.put(id,name)
          subscribersMap.put(id, subscriber)
          ctx.watchWith(subscriber, UserLeft(subscriber))
          grid.addSnake(id, roomId, name)
          dispatchTo(subscribersMap, id, Protocol.Id(id))
          val gridData = grid.getGridData
          dispatch(subscribersMap, gridData)
          Behaviors.same

        case LeftRoom(id, name) =>
          grid.removeSnake(id)
          subscribersMap.get(id).foreach(r=>ctx.unwatch(r))
          userMap.remove(id)
          subscribersMap.remove(id)
          if(userMap.isEmpty) Behaviors.stopped else Behaviors.same

        case UserLeft(actor) =>
          subscribersMap.find(_._2.equals(actor)).foreach { case (id, _) =>
            log.debug(s"got Terminated id = $id")
            val name = userMap.get(id).head
            subscribersMap.remove(id)
            userMap.remove(id)
            grid.removeSnake(id).foreach{s => dispatch(subscribersMap, Protocol.SnakeLeft(id, s.name))}
            roomManager ! RoomManager.UserLeft(id)
          }
          if(userMap.isEmpty) Behaviors.stopped else Behaviors.same


        case UserActionOnServer(id, action) =>
          action match {
            case Key(_, keyCode, frameCount, actionId) =>
              if (keyCode == KeyEvent.VK_SPACE) {
                grid.addSnake(id, roomId, userMap.getOrElse(id, "Unknown"))
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
//          log.debug("syncccccccccccc")
          if(userMap.nonEmpty){
            val shouldNewSnake =
              if(grid.waitingListState) true
              else if(tickCount % 20 == 5) true else false
//            log.debug(s"shouldNewSnake:::$shouldNewSnake")
            val finishFields = grid.updateInService(shouldNewSnake)
            val newData = grid.getGridData
            newData.killHistory.foreach { i =>
              if (i.frameCount + 1 == newData.frameCount) dispatch(subscribersMap, Protocol.SomeOneKilled(i.killedId, userMap(i.killedId), i.killerName))
            }
            if (shouldNewSnake) dispatch(subscribersMap, newData)
            else if (finishFields.nonEmpty) {
              val finishUsers = finishFields.map(_._1)
              finishUsers.foreach(u => dispatchTo(subscribersMap, u, newData))
              val newField = finishFields.map { f =>
                FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
                  ScanByColumn(y.toInt, Tool.findContinuous(target.map(_.x.toInt).toArray.sorted))
                }.toList)
              }
              userMap.filterNot(user => finishUsers.contains(user._1)).foreach(u => dispatchTo(subscribersMap, u._1, NewFieldInfo(grid.frameCount, newField)))
            }
            if(tickCount % 10 == 3) dispatch(subscribersMap, Protocol.Ranks(grid.currentRank))
            if(grid.currentRank.nonEmpty){
              val maxSize = grid.currentRank.head.area
              if((maxSize+fullSize*0.1)<winStandard)
                winStandard = fullSize * (0.2 - userMap.size * 0.05)
            }
            if (grid.currentRank.nonEmpty && grid.currentRank.head.area >= winStandard) {
              val finalData=grid.getGridData
              grid.cleanData()
              dispatch(subscribersMap, Protocol.SomeOneWin(userMap(grid.currentRank.head.id),finalData))
            }

          }


          idle(roomId, grid, userMap, subscribersMap, tickCount + 1)


        case _ =>
          log.warn(s"${ctx.self.path} recv a unknow msg=${msg}")
          Behaviors.same


      }
    }

  }

  def dispatchTo(subscribers:mutable.HashMap[Long,ActorRef[WsSourceProtocol.WsMsgSource]], id: Long, gameOutPut: Protocol.GameMessage): Unit = {
    subscribers.get(id).foreach { _ ! gameOutPut}
  }

  def dispatch(subscribers:mutable.HashMap[Long,ActorRef[WsSourceProtocol.WsMsgSource]], gameOutPut: Protocol.GameMessage) = {
    log.info(s"dispatch:::$gameOutPut")
    subscribers.values.foreach { _ ! gameOutPut}
  }

}
