package com.neo.sk.carnie.actor

import akka.actor.typed._
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.stream.OverflowStrategy
import akka.stream.typed.scaladsl.ActorSource
import akka.util.ByteString
import com.neo.sk.carnie.paperClient.Protocol._
import org.seekloud.byteobject.ByteObject.{bytesDecode, _}
import org.seekloud.byteobject.MiddleBufferInJvm
import org.slf4j.LoggerFactory
import akka.stream.scaladsl.{Keep, Sink}
import scala.concurrent.Future
import com.neo.sk.carnie.controller.GameController
import com.neo.sk.carnie.Boot.{executor, materializer, scheduler, system}
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.paperClient.Protocol

/**
  * Created by dry on 2018/10/23.
  **/
object PlayGameWebSocket {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  sealed trait WsCommand

  case class ConnectGame(playerInfo: PlayerInfoInClient, domain: String) extends WsCommand

  case class MsgToService(sendMsg: WsSendMsg) extends WsCommand

  def create(gameController: GameController): Behavior[WsCommand] = {
    Behaviors.setup[WsCommand] { ctx =>
      Behaviors.withTimers { implicit timer =>
        idle(gameController)
      }
    }
  }

  def idle(gameController: GameController)(implicit timer: TimerScheduler[WsCommand]): Behavior[WsCommand] = {
    Behaviors.receive[WsCommand] { (ctx, msg) =>
      msg match {
        case ConnectGame(playerInfo, domain) =>
          val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(getWebSocketUri(playerInfo.id, playerInfo.name, playerInfo.accessCode, domain)))
          val source = getSource
          val sink = getSink(gameController)
          val ((stream, response), closed) =
            source
              .viaMat(webSocketFlow)(Keep.both) // keep the materialized Future[WebSocketUpgradeResponse]
              .toMat(sink)(Keep.both) // also keep the Future[Done]
              .run()

          val connected = response.flatMap { upgrade =>
            if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
              Future.successful("connect success")
            } else {
              throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
            }
          } //ws建立

          closed.onComplete { _ =>
            log.info("connect to service closed!")
            gameController.loseConnect()
          } //ws断开
          connected.onComplete(i => log.info(i.toString))
          connecting(stream)

        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behaviors.unhandled
      }
    }
  }

  def connecting(actor: ActorRef[Protocol.WsSendMsg]): Behavior[WsCommand] = {
    Behaviors.receive[WsCommand] { (ctx, msg) =>
      msg match {
        case m@MsgToService(sendMsg) =>
          actor ! sendMsg
          Behaviors.same

        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behaviors.unhandled
      }
    }
  }

  private[this] def getSink(gameController: GameController) =
    Sink.foreach[Message] {
      case TextMessage.Strict(msg) =>
        log.debug(s"msg from webSocket: $msg")

      case BinaryMessage.Strict(bMsg) =>
        //decode process.
        val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
        bytesDecode[GameMessage](buffer) match {
          case Right(v) => gameController.gameMessageReceiver(v)
          case Left(e) =>
            println(s"decode error: ${e.message}")
        }

      case unknown@_ =>
        log.debug(s"i receiver an unknown message:$unknown")
    }

  private[this] def getSource = ActorSource.actorRef[WsSendMsg](
    completionMatcher = {
      case WsSendComplete =>
    }, failureMatcher = {
      case WsSendFailed(ex) ⇒ ex
    },
    bufferSize = 8,
    overflowStrategy = OverflowStrategy.fail
  ).collect {
    case message: UserAction =>
      val sendBuffer = new MiddleBufferInJvm(409600)
      BinaryMessage.Strict(ByteString(
        message.fillMiddleBuffer(sendBuffer).result()
      ))

  }

  def getWebSocketUri(playerId: String, playerName: String, accessCode: String, domain: String): String = {
    val wsProtocol = "ws"
    val domain = "10.1.29.250:30368"
    s"$wsProtocol://$domain/carnie/joinGame4Client?id=$playerId&name=$playerName"
  }

}
