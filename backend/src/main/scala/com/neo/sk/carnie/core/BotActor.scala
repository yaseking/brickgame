package com.neo.sk.carnie.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
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

  case class ConnectGame(botId: String, botName: String, roomId: Int) extends Command

  case object MakeAction extends Command

  case object KillBot extends Command

  private final case object MakeActionKey


  def create(id: String, name: String, grid: GridOnServer, roomActor: ActorRef[RoomActor.Command], mode: Int): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        val frameRate = mode match {
          case 2 => Protocol.frameRate2
          case _ => Protocol.frameRate1
        }
        roomActor ! RoomActor.JoinRoom4Bot(id, name, ctx.self, new Random().nextInt(6))
        timer.startPeriodicTimer(MakeActionKey, MakeAction, frameRate.millis)
        gaming(grid, roomActor)
      }
    }
  }

  def gaming(grid: GridOnServer, roomActor: ActorRef[RoomActor.Command])(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case MakeAction =>
          Behaviors.same

        case KillBot =>
          Behaviors.same

        case unknownMsg@_ =>
          log.warn(s"${ctx.self.path} unknown msg: $unknownMsg")
          stashBuffer.stash(unknownMsg)
          Behaviors.unhandled
      }
    }
  }
}
