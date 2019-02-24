package org.seekloud.brickgame.core

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import org.seekloud.brickgame.paperClient.Protocol._
import org.slf4j.LoggerFactory
import org.seekloud.brickgame.paperClient.{Protocol, _}
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
//  val border = Point(BorderSize.w, BorderSize.h)

  //  private val classify = 5

  private final case object SyncKey

  sealed trait Command

  case class UserActionOnServer(id: Int, action: Protocol.UserAction) extends Command

  case class JoinRoom(id: Int, name: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command

  case class LeftRoom(id: Int) extends Command

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
          val grid = new GridOnServer()
          timer.startPeriodicTimer(SyncKey, Sync, 100 millis)
          idle(roomId, grid, tickCount = 0l)
      }
    }
  }

  def idle(roomId: Int,
           grid: GridOnServer,
           userMap: mutable.HashMap[Int, String] = mutable.HashMap[Int, String](),
           subscribersMap: mutable.HashMap[Int, ActorRef[WsSourceProtocol.WsMsgSource]] = mutable.HashMap[Int, ActorRef[WsSourceProtocol.WsMsgSource]](),
           tickCount: Long
          )(implicit timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case m@JoinRoom(id, name, subscriber) =>
          log.info(s"got JoinRoom $m")
          userMap.put(id, name)
          subscribersMap.put(id, subscriber)
          log.debug(s"subscribersMap: $subscribersMap")
          //          ctx.watchWith(subscriber, UserLeft(subscriber))
          grid.addPlayer(id, name) //waitingList
          dispatchTo(subscribersMap, id, Protocol.Id(id))

          idle(roomId, grid, userMap, subscribersMap, tickCount)

        case LeftRoom(id) =>
          log.debug(s"LeftRoom:::$id")
          //todo
//          dispatch(subscribersMap, Protocol.UserLeft(id)) 发送其他死亡信息
          subscribersMap.remove(id)
          userMap.remove(id)
          grid.players -= id
          if(userMap.nonEmpty) {
            val winnerId = userMap.keys.head
            grid.players = Map.empty[Int, PlayerDt]
            dispatch(subscribersMap, WinPage(winnerId))
          }
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

              }

            case PressSpace =>
              //name从userMap中获取
              if(userMap.get(id).nonEmpty) {
                val name = userMap(id)
                grid.addPlayer(id, name)
                dispatchTo(subscribersMap, id, ReStartGame)
              }

            case InitAction =>
              grid.initAction(id)
              dispatchTo(subscribersMap, id, StartGame)

            case SendPingPacket(pingId) =>
              dispatchTo(subscribersMap, id, Protocol.ReceivePingPacket(pingId))

            case NeedToSync =>
              val data = Data4TotalSync2(grid.frameCount, grid.players)
              dispatchTo(subscribersMap, id, data)

            case Protocol.SendExpression(num) =>
              dispatch(subscribersMap, ReceiveExpression(id, num))

            case _ =>
          }
          Behaviors.same

        case Sync =>
          val shouldNewSnake = if (grid.waitingListState) true else false //玩家匹配，当玩家数为2的时候才产生
          val shouldSync = if (tickCount % 200 == 1) true else false//20s发送一次全量数据
          val newPlayers = grid.getNewPlayers
          val result = grid.updateInService(shouldNewSnake) //frame帧的数据执行完毕
          val deadList = result._1
          val stateChangeList = result._2
          if(shouldNewSnake) grid.gameDuration=0
          if(tickCount % 10 == 5 && grid.gameDuration < 60) { //60
            grid.gameDuration += 1
            dispatch(subscribersMap, GameDuration(grid.gameDuration.toShort))
          } else if(grid.gameDuration == 60 && grid.players.nonEmpty) {
            //游戏结束，清算
            val winnerId = grid.players.maxBy(_._2.score)._1
            grid.players = Map.empty[Int, PlayerDt]
            dispatch(subscribersMap, WinPage(winnerId))
          }

          newPlayers.foreach {id =>
            val data = Data4TotalSync2(grid.frameCount, grid.players)
            dispatchTo(subscribersMap, id, data)
          }

          if(grid.gameStateMap.nonEmpty) {
            grid.gameStateMap.foreach {p=>
              if(grid.frameCount-100==p._2) {//火球效果持续10s
                grid.gameStateMap -= p._1
                val playerInfo = grid.players(p._1)
                grid.players += p._1 -> playerInfo.copy(state = 0)
                dispatch(subscribersMap, Protocol.UpdatePlayerInfo(grid.players(p._1)))
              }
            }
          }

          deadList.foreach {id => //复活
//            dispatchTo(subscribersMap, id, DeadPage)
            grid.gameStateMap -= id
            val playerInfo = grid.players(id)
            val newField = grid.reBornPlank(id)
            grid.players += id -> playerInfo.copy(location = plankOri, velocityX = 0, velocityY = 0, ballLocation = Point(10, 28), field = newField, state = 0)
            dispatch(subscribersMap, Reborn(id))
          }

          stateChangeList.foreach {id=>
//            val newState = grid.players(id).state
//            dispatch(subscribersMap, ChangeState(id, newState))
//            val data = Data4TotalSync2(grid.frameCount, grid.players)
            dispatch(subscribersMap, Protocol.UpdatePlayerInfo(grid.players(id)))
          }

          if(shouldSync) {
            val data = Data4TotalSync2(grid.frameCount, grid.players)
            dispatch(subscribersMap, data)
          }

          idle(roomId, grid, userMap, subscribersMap, tickCount + 1)

        case ChildDead(child, childRef) =>
          log.debug(s"roomActor 不再监管 gameRecorder:$child,$childRef")
//          ctx.unwatch(childRef)
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
