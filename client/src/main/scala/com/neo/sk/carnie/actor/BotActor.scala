package com.neo.sk.carnie.actor

import akka.actor.typed.{ActorRef, Behavior}
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
import com.neo.sk.carnie.paperClient.Protocol._
import org.seekloud.byteobject.ByteObject.{bytesDecode, _}
import org.seekloud.byteobject.MiddleBufferInJvm
import scala.concurrent.Future
import com.neo.sk.carnie.Boot.{executor, materializer, scheduler, system}
import com.neo.sk.carnie.common.Constant
import com.neo.sk.carnie.controller.BotController
import com.neo.sk.carnie.paperClient.Protocol
import org.seekloud.esheepapi.pb.actions.Move

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

  case class Action(move: Move) extends Command

  case class ReturnObservation(playerId: String) extends Command

  case class MsgToService(sendMsg: WsSendMsg) extends Command


  def create(botController: BotController): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers { implicit timer =>
        ctx.self ! Work
        waitingGaming(botController)
      }
    }
  }

  def waitingGaming(botController: BotController)(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
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
          waitingGame(botController)

        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behaviors.unhandled
      }
    }
  }

  def waitingGame(botController: BotController)(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case CreateRoom(playerId, apiToken) =>
          val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(getCreateRoomWebSocketUri(playerId, apiToken)))
          val source = getSource
          val sink = getSink(botController)
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

        case JoinRoom(roomId, playerId, apiToken) =>
          val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(getJoinRoomWebSocketUri(roomId, playerId, apiToken)))
          val source = getSource
          val sink = getSink(botController)
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

        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behaviors.unhandled
      }
    }
  }

  def gaming(actor: ActorRef[Protocol.WsSendMsg])(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Action(move) =>
          val actionNum = Constant.moveToKeyCode(move)
//          if(actionNum != -1)
//            actor ! Key
          Behaviors.same

        case ReturnObservation(playerId) =>
          Behaviors.same

        case LeaveRoom(playerId) =>
          Behaviors.same

        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behaviors.unhandled
      }
    }
  }

  private[this] def getSink(botController: BotController) =
    Sink.foreach[Message] {
      case TextMessage.Strict(msg) =>
        log.debug(s"msg from webSocket: $msg")

      case BinaryMessage.Strict(bMsg) =>
        //decode process.
        val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
        bytesDecode[GameMessage](buffer) match {
          case Right(v) => botController.gameMessageReceiver(v)
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
            case Right(v) => botController.gameMessageReceiver(v)
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

  def getJoinRoomWebSocketUri(roomId: String, playerId: String, accessCode: String): String = {
  val wsProtocol = "ws"
    val domain = "10.1.29.250:30368"
    //    val domain = "localhost:30368"
    s"$wsProtocol://$domain/carnie/joinGame4Client?id=$playerId&accessCode=$accessCode"
  }

  def getCreateRoomWebSocketUri(playerId: String, accessCode: String): String = {
    val wsProtocol = "ws"
    val domain = "10.1.29.250:30368"
    //    val domain = "localhost:30368"
    s"$wsProtocol://$domain/carnie/joinGame4Client?id=$playerId&accessCode=$accessCode"
  }

}