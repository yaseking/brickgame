package com.neo.sk.carnie.actor

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.scaladsl.{Keep, Sink}
import com.neo.sk.carnie.bot.BotServer
import org.slf4j.LoggerFactory
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
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
import com.neo.sk.carnie.Boot.{executor, materializer, scheduler, system, timeout}
import com.neo.sk.carnie.common.Constant
import com.neo.sk.carnie.controller.BotController
import com.neo.sk.carnie.paperClient.{Protocol, Score}
import com.neo.sk.carnie.paperClient.WebSocketProtocol.PlayGamePara
import org.seekloud.esheepapi.pb.actions.Move
import org.seekloud.esheepapi.pb.api.{SimpleRsp, State}
import org.seekloud.esheepapi.pb.observations.{ImgData, LayeredObservation}

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import concurrent.duration._

/**
  * Created by dry on 2018/12/3.
  **/

object BotActor {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  val idGenerator = new AtomicInteger(1)

  val delay = 2

  private final case object BehaviorChangeKey

  case object Work extends Command

  case class CreateRoom(playerId: String, apiToken: String, password: String, replyTo: ActorRef[String]) extends Command

  case class RoomId(roomId: String) extends Command

  case class JoinRoom(roomId: String, playerId: String, apiToken: String, replyTo: ActorRef[SimpleRsp]) extends Command

  case class LeaveRoom(playerId: String) extends Command

  case class Action(move: Move, replyTo: ActorRef[Int]) extends Command

  case class ReturnObservation(playerId: String, replyTo: ActorRef[(Option[ImgData], LayeredObservation, Int)]) extends Command

  case class ReturnInform(replyTo: ActorRef[(Score, Int)]) extends Command

  case class MsgToService(sendMsg: WsSendMsg) extends Command

  case class TimeOut(msg: String) extends Command

  final case class SwitchBehavior(
                                   name: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error")
                                 ) extends Command


  def create(botController: BotController): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers { implicit timer =>
        ctx.self ! Work
        waitingForWork(botController)
      }
    }
  }

  def waitingForWork(botController: BotController)(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Work =>
          val executor = concurrent.ExecutionContext.Implicits.global
          val port = 5321//todo config

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
        case CreateRoom(playerId, apiToken, pwd, replyTo) =>
          val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(getCreateRoomWebSocketUri(playerId, apiToken, pwd)))
          val source = getSource
          val sink = getSink(botController)
          val ((stream, response), closed) =
            source
              .viaMat(webSocketFlow)(Keep.both) // keep the materialized Future[WebSocketUpgradeResponse]
              .toMat(sink)(Keep.both) // also keep the Future[Done]
              .run()

          val connected = response.flatMap { upgrade =>
            if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
              ctx.self ! SwitchBehavior("waitingForRoomId", waitingForRoomId(stream, botController, playerId, replyTo))
              Future.successful("connect success")
            } else {
              replyTo ! "error"
              ctx.self ! SwitchBehavior("waitingGame", waitingGame(botController))
              throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
            }
          } //ws建立

          closed.onComplete { _ =>
            log.info("connect to service closed!")
          } //ws断开
          connected.onComplete(i => log.info(i.toString))
//          gaming(stream, botController, playerId)
          switchBehavior(ctx, "busy", busy())

        case JoinRoom(roomId, playerId, apiToken, replyTo) =>
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
              replyTo ! SimpleRsp(state = State.unknown, msg = "ok")
              Future.successful("connect success")
            } else {
              replyTo ! SimpleRsp(errCode = 10006, state = State.unknown, msg = "join room error")
              throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
            }
          } //ws建立

          closed.onComplete { _ =>
            log.info("connect to service closed!")
          } //ws断开
          connected.onComplete(i => log.info(i.toString))
          gaming(stream, botController, playerId)

        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behaviors.unhandled
      }
    }
  }

  def gaming(actor: ActorRef[Protocol.WsSendMsg],
             botController: BotController,
             playerId: String)(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Action(move, replyTo) =>
          val actionNum = Constant.moveToKeyCode(move)
          if(actionNum != -1) {
            val actionId = idGenerator.getAndIncrement()
            val frame = botController.grid.frameCount
            actor ! Key(playerId, actionNum, frame, actionId)
            botController.grid.addActionWithFrame(playerId, actionNum, frame)
            replyTo ! frame.toInt
          } else replyTo ! -1
          Behaviors.same

        case ReturnObservation(playerId, replyTo) =>
          replyTo ! botController.getAllImage
          Behaviors.same

        case ReturnInform(replyTo) =>
          replyTo ! (botController.myCurrentRank, botController.grid.frameCount.toInt)
          Behaviors.same

        case LeaveRoom(playerId) =>
          log.info(s"player:$playerId leave room, botActor stop.")
          Behaviors.stopped

        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behaviors.unhandled
      }
    }
  }

  def waitingForRoomId(actor: ActorRef[Protocol.WsSendMsg],
           botController: BotController,
           playerId: String,
           replyTo: ActorRef[String])(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case RoomId(roomId) =>
          replyTo ! roomId
          stashBuffer.unstashAll(ctx, gaming(actor, botController, playerId))

        case unknown@_ =>
          stashBuffer.stash(unknown)
          Behaviors.same
      }
    }
  }

  private def busy()(
    implicit stashBuffer:StashBuffer[Command],
    timer:TimerScheduler[Command]
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, behavior,durationOpt,timeOut) =>
          log.debug(s"switchBehavior")
          switchBehavior(ctx,name,behavior,durationOpt,timeOut)

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path} is time out when busy,msg=${m}")
          Behaviors.stopped

        case unknowMsg =>
          stashBuffer.stash(unknowMsg)
          Behavior.same
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

  private[this] def switchBehavior(ctx: ActorContext[Command],
                                   behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
                                  (implicit stashBuffer: StashBuffer[Command],
                                   timer: TimerScheduler[Command]) = {
    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }

  def getJoinRoomWebSocketUri(roomId: String, playerId: String, accessCode: String): String = {
  val wsProtocol = "ws"
    val domain = "10.1.29.250:30368"
    //    val domain = "localhost:30368"
    s"$wsProtocol://$domain/carnie/joinGame4Client?id=$playerId&accessCode=$accessCode"
  }

  def getCreateRoomWebSocketUri(playerId: String, accessCode: String, pwd: String): String = {
    val wsProtocol = "ws"
    val domain = "10.1.29.250:30368"
    //    val domain = "localhost:30368"
    s"$wsProtocol://$domain/carnie/joinGame4ClientCreateRoom?id=$playerId&accessCode=$accessCode&mode=1&img=1&pwd=$pwd"
  }

}
