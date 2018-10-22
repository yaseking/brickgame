package com.neo.sk.carnie.core

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import com.neo.sk.carnie.utils.EsheepClient
import com.neo.sk.carnie.common.AppSettings

/**
  * Lty 18/10/17
  */
object TokenActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class GetToken(gameId: Long, gsKey: String) extends Command

  final case object GetTokenKey extends Command

  private[this] def interval = {//测试，每5s请求一次
    10 * 1000
  }

  private[this] def firstTime = {
    1 * 1000
  }

  private[this] val gameId: Long = AppSettings.esheepGameId
  private[this] val gsKey: String = AppSettings.esheepGsKey

  val behavior = init()

  def init(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      Behaviors.withTimers[Command] { implicit timer =>
        timer.startSingleTimer(GetTokenKey, GetToken(gameId, gsKey), firstTime.millis)
        timer.startPeriodicTimer(GetTokenKey, GetToken(gameId, gsKey), interval.millis)
        idle()
      }
    }
  }

  def idle()(implicit timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case GetToken(gameId, gsKey) =>
          EsheepClient.getTokenRequest(gameId, gsKey)
          Behaviors.same

        case x =>
          log.warn(s"${ctx.self.path} unknown msg: $x")
          Behaviors.unhandled
      }

    }
  }

}
