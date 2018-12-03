package com.neo.sk.carnie.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.carnie.bot.BotServer
import org.slf4j.LoggerFactory

/**
  * Created by dry on 2018/12/3.
  **/
object BotActor {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case object Work extends Command

  case class CreateRoom(playerId: String, apiToken: String) extends Command

  case class JoinRoom() extends Command

  case class LeaveRoom() extends Command

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers { implicit timer =>
        waiting()
      }
    }
  }

  def waiting()(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Work =>
          val executor = concurrent.ExecutionContext.Implicits.global
          val port = 5321

          val server = BotServer.build(port, executor, ctx.self)
          server.start()
          log.debug(s"Server started at $port")

          sys.addShutdownHook {
            log.debug("JVM SHUT DOWN.")
            server.shutdown()
            log.debug("SHUT DOWN.")
          }

          server.awaitTermination()
          log.debug("DONE.")
          gaming()

        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behaviors.unhandled
      }
    }
  }

  def gaming()(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {


        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behaviors.unhandled
      }
    }
  }

}
