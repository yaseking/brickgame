package com.neo.sk.carnie.actor

import akka.Done
import akka.actor.ActorSystem
import akka.actor.typed._
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import akka.util.ByteString
import com.neo.sk.carnie.paperClient.Protocol._
import com.neo.sk.carnie.paperClient.WsSourceProtocol.{CompleteMsgServer, FailMsgServer, WsMsgSource}
import org.seekloud.byteobject.ByteObject.{bytesDecode, _}
import org.seekloud.byteobject.MiddleBufferInJvm
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContextExecutor, Future}
import com.neo.sk.carnie.common.{AppSetting, Context}
import com.neo.sk.carnie.controller.GameController
import com.neo.sk.carnie.paperClient.WsSourceProtocol
import com.neo.sk.carnie.protocol.Protocol4Agent.WsRsp
import com.neo.sk.carnie.scene.GameScene

/**
  * Created by dry on 2018/10/23.
  **/
object LoginSocketClient {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  sealed trait WsCommand

  case class ConnectGame(id: String, name: String, accessCode: String) extends WsCommand

  case class EstablishConnection2Es(wsUrl: String) extends WsCommand

  def create(context: Context,
             _system: ActorSystem,
             _materializer: Materializer,
             _executor: ExecutionContextExecutor
            ): Behavior[WsCommand] = {
    Behaviors.setup[WsCommand] { ctx =>
      Behaviors.withTimers { timer =>
        idle(context)(timer, _system, _materializer, _executor)
      }
    }
  }

  def idle(context: Context)(implicit timer: TimerScheduler[WsCommand],
           system: ActorSystem,
           materializer: Materializer,
           executor: ExecutionContextExecutor): Behavior[WsCommand] = {
    Behaviors.receive[WsCommand] { (ctx, msg) =>
      msg match {
        case EstablishConnection2Es(wsUrl: String) =>
          val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(wsUrl))

          val source = getSource
          val sink = getSink4EstablishConnection(ctx.self)
          val response =
            source
              .viaMat(webSocketFlow)(Keep.right)
              .toMat(sink)(Keep.left)
              .run()
          val connected = response.flatMap { upgrade =>
            if (upgrade.response.status == StatusCodes.SwitchingProtocols) {

              Future.successful("WsClient connect success. EstablishConnectionEs!")
            } else {
              throw new RuntimeException(s"WSClient connection failed: ${upgrade.response.status}")
            }
          } //链接建立时
          connected.onComplete(i => log.info(i.toString))
          Behavior.same
      }
    }
  }

  def getSink4EstablishConnection(self: ActorRef[WsCommand]):Sink[Message,Future[Done]] = {
    Sink.foreach {
      case TextMessage.Strict(msg) =>
        import io.circe.generic.auto._
        import io.circe.parser.decode
        import com.neo.sk.carnie.protocol.Protocol4Agent._
        import com.neo.sk.carnie.utils.Api4GameAgent.linkGameAgent
        import com.neo.sk.carnie.Boot.executor

        log.debug(s"msg from webSocket: $msg")
        val gameId = AppSetting.esheepGameId
        decode[WsRsp](msg) match {
          case Right(res) =>
            println("res:   "+res)
            val playerId = "user" + res.Ws4AgentRsp.data.userId.toString
            val playerName = res.Ws4AgentRsp.data.nickname
            linkGameAgent(gameId,playerId,res.Ws4AgentRsp.data.token).map{
              case Right(r) =>
                log.info("accessCode: "+r.accessCode)
                log.info("prepare to join carnie!")
//                self ! ConnectGame(playerId, playerName, r.accessCode)
              case Left(_) =>
                log.debug("link error!")
            }
          case Left(le) =>
            log.debug(s"decode esheep webmsg error! Error information:${le}")
        }

      case BinaryMessage.Strict(bMsg) =>
        //decode process.
        val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
        bytesDecode[WsRsp](buffer) match {
          case Right(_) =>
          case Left(e) =>
            println(s"decode error: ${e.message}")
            TextMsg("decode error")
        }

      case unknown@_ =>
        log.debug(s"i receive an unknown msg:$unknown")
    }
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

}
