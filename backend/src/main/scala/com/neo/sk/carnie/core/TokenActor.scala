package com.neo.sk.carnie.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import com.neo.sk.carnie.utils.EsheepClient
import com.neo.sk.carnie.common.AppSettings
import com.neo.sk.carnie.Boot.executor
import com.neo.sk.carnie.protocol.EsheepProtocol.TokenData
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._


/**
  * Lty 18/10/17
  */
object TokenActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case object GetToken extends Command

  final case object GetTokenKey extends Command

  final case class AskForToken(reply: ActorRef[Option[String]]) extends Command

  private var token: String = ""
  private var expiredTime: Long = 1000 * 60 * 10

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
//        timer.startSingleTimer(GetTokenKey, GetToken(gameId, gsKey), firstTime.millis)
//        timer.startPeriodicTimer(GetTokenKey, GetToken(gameId, gsKey), interval.millis)
//        EsheepClient.getTokenRequest(gameId, gsKey).map {
//          case Right(rsp) =>
//            println(s"getToken first time: $rsp")
//            token = rsp.token
//        }
        ctx.self ! GetToken
        idle()
      }
    }
  }

  def idle()(implicit timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case GetToken =>
          EsheepClient.getTokenRequest(gameId, gsKey).map{
            case Right(rsp) =>
              println(s"getToken first time: $rsp")
              expiredTime = rsp.expireTime
              token = rsp.token
            case Left(e) =>
              log.info(s"Some errors happened in getToken: $e")
          }
          timer.startSingleTimer(GetTokenKey, GetToken, expiredTime.millis)
          Behaviors.same

        case AskForToken(reply) =>
          reply ! Option(token)
          Behaviors.same

        case x =>
          log.warn(s"${ctx.self.path} unknown msg: $x")
          Behaviors.unhandled
      }

    }
  }

}
