package org.seekloud.brickgame.core

import java.util.concurrent.atomic.AtomicInteger
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import org.seekloud.brickgame.paperClient.Protocol._
import org.slf4j.LoggerFactory
import org.seekloud.brickgame.paperClient._
import org.seekloud.brickgame.Boot.roomManager
import org.seekloud.brickgame.common.AppSettings
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

  //  private val classify = 5

  private final case object SyncKey

  sealed trait Command

  case class UserActionOnServer(id: Int, action: Protocol.UserAction) extends Command

  case class JoinRoom(id: Int, name: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command

  case class LeftRoom(id: Int) extends Command

  case class UserDead(roomId: Int, mode: Int, users: List[(String, Short, Short)]) extends Command with RoomManager.Command // (id, kill, area)

  private case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  case class WatchGame(playerId: String, userId: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command

  case class WatcherLeftRoom(userId: String) extends Command

  case class CloseWs(userId: String) extends Command

  private case object Sync extends Command

  case class UserInfo(name: String, startTime: Long, joinFrame: Long, img: Int)

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
          val grid = new GridOnServer(border)
          timer.startPeriodicTimer(SyncKey, Sync, 100 millis)
          idle(roomId, grid, tickCount = 0l)
      }
    }
  }

  def idle(roomId: Int,
           grid: GridOnServer,
           userMap: mutable.HashMap[String, UserInfo] = mutable.HashMap[String, UserInfo](),
           subscribersMap: mutable.HashMap[Int, ActorRef[WsSourceProtocol.WsMsgSource]] = mutable.HashMap[Int, ActorRef[WsSourceProtocol.WsMsgSource]](),
           tickCount: Long
          )(implicit timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case m@JoinRoom(id, name, subscriber) =>
          log.info(s"got JoinRoom $m")
//          userMap.put(id, UserInfo(name, System.currentTimeMillis(), tickCount, img))
          subscribersMap.put(id, subscriber)
          log.debug(s"subscribersMap: $subscribersMap")
          //          ctx.watchWith(subscriber, UserLeft(subscriber))
          grid.addPlayer(id, name)
          dispatchTo(subscribersMap, id, Protocol.Id(id))

          idle(roomId, grid, userMap, subscribersMap, tickCount)

        case LeftRoom(id) =>
          log.debug(s"LeftRoom:::$id")
          dispatch(subscribersMap, Protocol.UserLeft(id))
          subscribersMap.remove(id)
          if(subscribersMap.isEmpty) {
            Behaviors.stopped
          } else {
            Behaviors.same
          }

        case UserActionOnServer(id, action) =>
          action match {
            case Key(keyCode, frameCount) =>
              if (grid.players.get(id).nonEmpty) {
                val realFrame = grid.checkActionFrame(id, frameCount)
                grid.addActionWithFrame(id, keyCode, realFrame)

                dispatch(subscribersMap, Protocol.SnakeAction(id, keyCode, realFrame)) //发送自己的action改为给所有的人发送

//                dispatch(subscribersMap.filterNot(_._1 == id),
//                  Protocol.OtherAction(grid.snakes(id).carnieId, keyCode, realFrame)) //给其他人发送消息，合并
              }

            case InitAction(id) =>
              grid.initAction(id)
              dispatchTo(subscribersMap, id, StartGame)

            case SendPingPacket(pingId) =>
              dispatchTo(subscribersMap, id, Protocol.ReceivePingPacket(pingId))

            case NeedToSync =>
              val data = Data4TotalSync2(grid.frameCount, grid.players)
              dispatchTo(subscribersMap, id, data)

            case _ =>
          }
          Behaviors.same

        case Sync =>
          val shouldNewSnake = if (grid.waitingListState) true else false
          val shouldSync = if (tickCount % 50 == 1) true else false//5s发送一次全量数据
          grid.updateInService(shouldNewSnake) //frame帧的数据执行完毕
          if(shouldSync) {
            val data = Data4TotalSync2(grid.frameCount, grid.players)
            dispatch(subscribersMap, data)
          }

          idle(roomId, grid, userMap, subscribersMap, tickCount + 1)

        case ChildDead(child, childRef) =>
          log.debug(s"roomActor 不再监管 gameRecorder:$child,$childRef")
          ctx.unwatch(childRef)
          Behaviors.same

        case _ =>
          log.warn(s"${ctx.self.path} recv a unknow msg=$msg")
          Behaviors.unhandled
      }
    }

  }

  def dispatchTo(subscribers: mutable.HashMap[Int, ActorRef[WsSourceProtocol.WsMsgSource]], id: Int, gameOutPut: Protocol.GameMessage): Unit = {
    subscribers.get(id).foreach {
      _ ! gameOutPut
    }
  }

  def dispatch(subscribers: mutable.HashMap[Int, ActorRef[WsSourceProtocol.WsMsgSource]], gameOutPut: Protocol.GameMessage): Unit = {
    subscribers.values.foreach {
      _ ! gameOutPut
    }
  }


}
