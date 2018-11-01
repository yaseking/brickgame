package com.neo.sk.carnie.actor

import akka.Done
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
import org.seekloud.byteobject.ByteObject.{bytesDecode, _}
import org.seekloud.byteobject.MiddleBufferInJvm
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContextExecutor, Future}
import com.neo.sk.carnie.common.{AppSetting, Context}
import com.neo.sk.carnie.controller.{GameController, LoginController}
import com.neo.sk.carnie.Boot.{executor, materializer, scheduler, system}

/**
  * Created by dry on 2018/10/23.
  **/
object LoginSocketClient {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  sealed trait WsCommand

  case class EstablishConnection2Es(wsUrl: String) extends WsCommand

  def create(context: Context, loginController: LoginController): Behavior[WsCommand] = {
    Behaviors.setup[WsCommand] { ctx =>
      Behaviors.withTimers { implicit timer =>
        idle(context, loginController)(timer)
      }
    }
  }

  def idle(context: Context, loginController: LoginController)(implicit timer: TimerScheduler[WsCommand]): Behavior[WsCommand] = {
    Behaviors.receive[WsCommand] { (ctx, msg) =>
      msg match {
        case EstablishConnection2Es(wsUrl: String) =>
          val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(wsUrl))

          val source = getSource
          val sink = getSink4EstablishConnection(ctx.self, context, loginController)
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
//          connected.onComplete(i => log.info(i.toString))
          Behavior.same
      }
    }
  }

  def getSink4EstablishConnection(self: ActorRef[WsCommand], context: Context, loginController: LoginController):Sink[Message,Future[Done]] = {
    Sink.foreach {
      case TextMessage.Strict(msg) =>
        import io.circe.generic.auto._
        import io.circe.parser.decode
        import com.neo.sk.carnie.protocol.Protocol4Agent._
        import com.neo.sk.carnie.utils.Api4GameAgent.linkGameAgent
        import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
        import com.neo.sk.carnie.Boot.executor

        val gameId = AppSetting.esheepGameId
        decode[MsgFromLogin](msg) match {
          case Right(res) =>
            res match {
              case WsRsp(ws4AgentRsp) =>
                if (ws4AgentRsp.errCode != 0) {
                  log.debug(s"receive responseRsp error....${ws4AgentRsp.msg}")
                } else {
                  val data = ws4AgentRsp.data
                  val playerId = "user" + data.userId.toString
                  val playerName = data.nickname
                  linkGameAgent(gameId, playerId, data.token).map {
                    case Right(r) =>
                      loginController.switchToGaming()

                    case Left(e) =>
                      log.debug(s"linkGameAgent..$e")
                  }
                }

              case HeartBeat =>
                println(s"HeartBeat: $res")
                //每5s收到一次心跳消息，不做处理
//                log.info("i receive a HeartBeat msg.")
            }

          case Left(e) =>
            println(s"msg: $msg")
            log.debug(s"decode esheep webmsg error! Error information:$e")
        }

//        decode[WsRsp](msg) match {
//          case Right(res) =>
//            println(s"msg: $msg")
//            println("res:   "+res)
//            val playerId = "user" + res.Ws4AgentRsp.data.userId.toString
//            val playerName = res.Ws4AgentRsp.data.nickname
//            linkGameAgent(gameId,playerId,res.Ws4AgentRsp.data.token).map{
//              case Right(r) =>
//                log.info("accessCode: "+r.accessCode)
//                log.info("prepare to join carnie!")
//                loginController.switchToGaming()
//
//              case Left(_) =>
//                log.debug("link error!")
//            }
//          case Left(le) =>
//            log.debug(s"decode esheep webmsg error! Error information:${le}")
//        }

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
