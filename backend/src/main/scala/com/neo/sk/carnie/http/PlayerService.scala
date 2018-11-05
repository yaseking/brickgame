package com.neo.sk.carnie.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.util.{ByteString, Timeout}
import com.neo.sk.carnie.paperClient.Protocol
import akka.stream.scaladsl.Flow
import com.neo.sk.carnie.core.{GameReplay, RoomManager, TokenActor}
import com.neo.sk.carnie.paperClient.Protocol._
import org.slf4j.LoggerFactory
import com.neo.sk.carnie.Boot.roomManager
import com.neo.sk.carnie.common.AppSettings
import com.neo.sk.carnie.core.TokenActor.AskForToken
import com.neo.sk.carnie.ptcl.ErrorRsp
import com.neo.sk.utils.{CirceSupport, EsheepClient}
import io.circe.generic.auto._

import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.carnie.Boot.scheduler

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 4:13 PM
  */
trait PlayerService extends ServiceUtils with CirceSupport {

  implicit val system: ActorSystem

  implicit def executor: ExecutionContextExecutor

  val tokenActor: akka.actor.typed.ActorRef[TokenActor.Command]

  implicit val materializer: Materializer

  implicit val timeout: Timeout

//  val tokenActor: akka.actor.typed.ActorRef[TokenActor.Command]

  private[this] val log = LoggerFactory.getLogger("com.neo.sk.hiStream.http.SnakeService")


  val netSnakeRoute = {
    path("join") {
      parameter(
        'id.as[String],
        'name.as[String]
      ) { (id, name) =>
        handleWebSocketMessages(webSocketChatFlow(id, sender = name))
      }
    } ~
      path("watchGame") {
        parameter(
          'roomId.as[Int],
          'playerId.as[String]
        ) { (roomId, playerId) =>
          handleWebSocketMessages(webSocketChatFlow4WatchGame(roomId, playerId))
        }
      } ~ path("joinWatchRecord") {
      parameter(
        'recordId.as[Long],
        'playerId.as[String],
        'frame.as[Int],
        'accessCode.as[String]
      ) { (recordId, playerId, frame, accessCode) =>
        val gameId = AppSettings.esheepGameId
        dealFutureResult {
          val msg: Future[String] = tokenActor ? AskForToken
          msg.map { token =>
            dealFutureResult{
              EsheepClient.verifyAccessCode(gameId, accessCode, token).map {
                case Right(rsp) =>
                  handleWebSocketMessages(webSocketChatFlow4WatchRecord(playerId, recordId, frame, rsp.playerId))
                case Left(e) =>
                  complete(ErrorRsp(120002, "Some errors happened in parse verifyAccessCode."))
              }
            }
          }
        }


      }
    } ~
      path("joinGame4Client") {
        parameter(
          'id.as[String],
          'name.as[String],
          'accessCode.as[String]
        ) { (id, name, accessCode) =>
          val gameId = AppSettings.esheepGameId
          dealFutureResult{
            val msg: Future[String] = tokenActor ? AskForToken
            msg.map {token =>
              dealFutureResult{
                log.info("start to verifyAccessCode4Client.")
                EsheepClient.verifyAccessCode(gameId, accessCode, token).map {
                  case Right(_) =>
                    handleWebSocketMessages(webSocketChatFlow(id, sender = name))
                  case Left(e) =>
                    log.error(s"playGame error. fail to verifyAccessCode4Client: $e")
                    //fixme 测试阶段验证失败也建立连接，记得修改。
//                    complete(ErrorRsp(120010, "Some errors happened in parse verifyAccessCode."))
                    handleWebSocketMessages(webSocketChatFlow(id, sender = name))
                }
              }
            }
          }
        }
      }
  }

  def webSocketChatFlow4WatchGame(roomId: Int, playerId: String): Flow[Message, Message, Any] = {
    import scala.language.implicitConversions
    import org.seekloud.byteobject.ByteObject._
    import org.seekloud.byteobject.MiddleBufferInJvm
    import io.circe.generic.auto._
    import io.circe.parser._
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) =>
          log.debug(s"msg from webSocket: $msg")
          TextInfo(msg)

        case BinaryMessage.Strict(bMsg) =>
          //decode process.
          val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
          val msg =
            bytesDecode[UserAction](buffer) match {
              case Right(v) => v
              case Left(e) =>
                println(s"decode error: ${e.message}")
                TextInfo("decode error")
            }
          msg
        // unpack incoming WS text messages...
        // This will lose (ignore) messages not received in one chunk (which is
        // unlikely because chat messages are small) but absolutely possible
        // FIXME: We need to handle TextMessage.Streamed as well.
      }
      .via(RoomManager.watchGame(roomManager, roomId, playerId))
      .map {
        case msg:Protocol.GameMessage =>
          val sendBuffer = new MiddleBufferInJvm(409600)
          BinaryMessage.Strict(ByteString(
            //encoded process
            msg.fillMiddleBuffer(sendBuffer).result()

          ))

        case x =>
          TextMessage.apply("")

      }.withAttributes(ActorAttributes.supervisionStrategy(decider)) // ... then log any processing errors on stdin
  }


  def webSocketChatFlow(playedId: String, sender: String): Flow[Message, Message, Any] = {
    import scala.language.implicitConversions
    import org.seekloud.byteobject.ByteObject._
    import org.seekloud.byteobject.MiddleBufferInJvm
    import io.circe.generic.auto._
    import io.circe.parser._
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) =>
          log.debug(s"msg from webSocket: $msg")
          TextInfo(msg)

        case BinaryMessage.Strict(bMsg) =>
          //decode process.
          val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
          val msg =
            bytesDecode[UserAction](buffer) match {
              case Right(v) => v
              case Left(e) =>
                println(s"decode error: ${e.message}")
                TextInfo("decode error")
            }
          msg
        // unpack incoming WS text messages...
        // This will lose (ignore) messages not received in one chunk (which is
        // unlikely because chat messages are small) but absolutely possible
        // FIXME: We need to handle TextMessage.Streamed as well.
      }
      .via(RoomManager.joinGame(roomManager, playedId, sender))
      .map {
        case msg:Protocol.GameMessage =>
          val sendBuffer = new MiddleBufferInJvm(409600)
          BinaryMessage.Strict(ByteString(
            //encoded process
            msg.fillMiddleBuffer(sendBuffer).result()

          ))

        case x =>
          TextMessage.apply("")

      }.withAttributes(ActorAttributes.supervisionStrategy(decider)) // ... then log any processing errors on stdin
  }

  def webSocketChatFlow4WatchRecord(playedId: String, recordId: Long, frame: Int, playerId: String): Flow[Message, Message, Any] = {
    import scala.language.implicitConversions
    import org.seekloud.byteobject.ByteObject._
    import org.seekloud.byteobject.MiddleBufferInJvm
    import io.circe.generic.auto._
    import io.circe.parser._
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) =>
          log.debug(s"msg from webSocket: $msg")
          TextInfo(msg)

        case BinaryMessage.Strict(bMsg) =>
          //decode process.
          val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
          val msg =
            bytesDecode[UserAction](buffer) match {
              case Right(v) => v
              case Left(e) =>
                println(s"decode error: ${e.message}")
                TextInfo("decode error")
            }
          msg
        // unpack incoming WS text messages...
        // This will lose (ignore) messages not received in one chunk (which is
        // unlikely because chat messages are small) but absolutely possible
        // FIXME: We need to handle TextMessage.Streamed as well.
      }
      .via(RoomManager.replayGame(roomManager, recordId, playedId, frame, playerId))
      .map {
        case msg:Protocol.GameMessage =>
          val sendBuffer = new MiddleBufferInJvm(409600)
          BinaryMessage.Strict(ByteString(
            //encoded process
            msg.fillMiddleBuffer(sendBuffer).result()

          ))

        case x =>
          TextMessage.apply("")

      }.withAttributes(ActorAttributes.supervisionStrategy(decider)) // ... then log any processing errors on stdin
  }


  val decider: Supervision.Decider = {
    e: Throwable =>
      e.printStackTrace()
      println(s"WS stream failed with $e")
      Supervision.Resume
  }


}
