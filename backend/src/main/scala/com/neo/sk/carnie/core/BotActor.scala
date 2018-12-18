package com.neo.sk.carnie.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.carnie.common.AppSettings
import com.neo.sk.carnie.core.RoomActor.{UserActionOnServer, dispatch}
import com.neo.sk.carnie.paperClient.Protocol.Key
import com.neo.sk.carnie.paperClient.{GridOnServer, Protocol}
import org.slf4j.LoggerFactory

import concurrent.duration._
import scala.util.Random

/**
  * Created by dry on 2018/12/17.
  **/
object BotActor {
  //简单的bot：在某个房间生成后生成陪玩bot
  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class InitInfo(botName: String, mode: Int, grid: GridOnServer, roomActor: ActorRef[RoomActor.Command]) extends Command
  case object MakeAction extends Command

  case object KillBot extends Command

  case object BotDead extends Command

  case object Space extends Command

  case object BackToGame extends Command

  private final case object MakeActionKey

  private final case object SpaceKey

  private var action = 0


  def create(botId: String): Behavior[Command] = {
    implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
    Behaviors.withTimers[Command] { implicit timer =>

      Behaviors.receive[Command] { (ctx, msg) =>
        msg match {
          case InitInfo(botName, mode, grid, roomActor) =>
            val frameRate = mode match {
              case 2 => Protocol.frameRate2
              case _ => Protocol.frameRate1
            }
            roomActor ! RoomActor.JoinRoom4Bot(botId, botName, ctx.self, new Random().nextInt(6))
            timer.startPeriodicTimer(MakeActionKey, MakeAction, (1 + scala.util.Random.nextInt(20)) * frameRate.millis)
            gaming(botId, grid, roomActor, frameRate)

          case unknownMsg@_ =>
            log.warn(s"${ctx.self.path} unknown msg: $unknownMsg")
            stashBuffer.stash(unknownMsg)
            Behaviors.unhandled
        }
      }
    }
  }

  def gaming(botId: String, grid: GridOnServer, roomActor: ActorRef[RoomActor.Command], frameRate: Int)
            (implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case MakeAction =>
          val actionCode = action % 4 + 37
          roomActor ! UserActionOnServer(botId, Key(botId, actionCode, grid.frameCount, -1))
          action += 1
          Behaviors.same

        case BotDead =>
          log.info(s"bot dead:$botId")
          timer.startSingleTimer(SpaceKey, Space, (2 + scala.util.Random.nextInt(8)) * frameRate.millis)
          timer.cancel(MakeActionKey)
          dead(botId, grid, roomActor, frameRate)

        case KillBot =>
          log.debug(s"botActor:$botId go to die...")
          Behaviors.stopped

        case unknownMsg@_ =>
          log.warn(s"${ctx.self.path} unknown msg: $unknownMsg")
          stashBuffer.stash(unknownMsg)
          Behaviors.unhandled
      }
    }
  }

  def dead(botId: String, grid: GridOnServer, roomActor: ActorRef[RoomActor.Command], frameRate: Int)
          (implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Space =>
          log.info(s"recv Space: botId:$botId")
          val actionCode = 32
          roomActor ! UserActionOnServer(botId, Key(botId, actionCode, grid.frameCount, -1))
          Behaviors.same

        case BackToGame =>
          log.info(s"back to game")
          timer.startPeriodicTimer(MakeActionKey, MakeAction, (1 + scala.util.Random.nextInt(20)) * frameRate.millis)
          gaming(botId, grid, roomActor, frameRate)

        case KillBot =>
          log.debug(s"botActor:$botId go to die...")
          Behaviors.stopped

        case unknownMsg@_ =>
          log.warn(s"${ctx.self.path} unknown msg: $unknownMsg")
          stashBuffer.stash(unknownMsg)
          Behaviors.unhandled
      }
    }
  }
}
