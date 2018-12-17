package com.neo.sk.carnie.core

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import org.slf4j.LoggerFactory

/**
  * Created by dry on 2018/12/17.
  **/
object BotManager {
  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

//  case class InitBot(roomId) extends Command

  val behaviors: Behavior[Command] = init()

  def init(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        idle()
      }
    }
  }

  def idle()(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {

        case unknownMsg@_ =>
          log.warn(s"${ctx.self.path} unknown msg: $unknownMsg")
          stashBuffer.stash(unknownMsg)
          Behaviors.unhandled
      }
    }
  }

}
