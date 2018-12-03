package com.neo.sk.carnie.actor

import akka.actor.typed.Behavior
import akka.stream.scaladsl.{Keep, Sink}
import com.neo.sk.carnie.bot.BotServer
import org.slf4j.LoggerFactory
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.stream.OverflowStrategy
import akka.stream.typed.scaladsl.ActorSource
import akka.util.{ByteString, ByteStringBuilder}
import com.neo.sk.carnie.actor.PlayGameWebSocket.log
import com.neo.sk.carnie.controller.GameController
import com.neo.sk.carnie.paperClient.Protocol._
import org.seekloud.byteobject.ByteObject.bytesDecode
import org.seekloud.byteobject.MiddleBufferInJvm

import scala.concurrent.Future

/**
  * Created by dry on 2018/12/3.
  **/
object BotActor {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case object Work extends Command

  case class CreateRoom(playerId: String, apiToken: String) extends Command

  case class JoinRoom(roomId: String, playerId: String, apiToken: String) extends Command

  case class LeaveRoom(playerId: String) extends Command

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers { implicit timer =>
        waitingWork()
      }
    }
  }

  def waitingWork()(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
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
          waitingGame()

        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behaviors.unhandled
      }
    }
  }

  def waitingGame()(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case CreateRoom(playerId, apiToken) =>
          val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(getWebSocketUri(playerId, apiToken)))
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
          } //ws断开
          connected.onComplete(i => log.info(i.toString))
          gaming(stream)
          Behaviors.same

        case JoinRoom(roomId, playerId, apiToken) =>
          Behaviors.same

        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behaviors.unhandled
      }
    }
  }

  def gaming()(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case LeaveRoom(playerId) =>
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

      case msg:BinaryMessage.Streamed =>
        val f = msg.dataStream.runFold(new ByteStringBuilder().result()){
          case (s, str) => s.++(str)
        }

        f.map { bMsg =>
          val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
          bytesDecode[GameMessage](buffer) match {
            case Right(v) => gameController.gameMessageReceiver(v)
            case Left(e) =>
              println(s"decode error: ${e.message}")
          }
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
    bufferSize = 64,
    overflowStrategy = OverflowStrategy.fail
  ).collect {
    case message: UserAction =>
      val sendBuffer = new MiddleBufferInJvm(409600)
      BinaryMessage.Strict(ByteString(
        message.fillMiddleBuffer(sendBuffer).result()
      ))

  }

  def getWebSocketUri(playerId: String, apiToken: String): String = {//mode: Int, img: Int
  val wsProtocol = "ws"
    val domain = "10.1.29.250:30368"
    //    val domain = "localhost:30368"
    s"$wsProtocol://$domain/carnie/joinGame4Client?id=$playerId&apiToken=$apiToken"
    //    s"$wsProtocol://$domain/carnie/join?id=$playerId&name=$playerName"
  }

}
