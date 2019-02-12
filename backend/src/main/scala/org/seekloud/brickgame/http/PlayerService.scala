package org.seekloud.brickgame.http

import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.util.{ByteString, Timeout}
import org.seekloud.brickgame.paperClient.Protocol
import akka.stream.scaladsl.Flow
import org.seekloud.brickgame.core.RoomManager
import org.seekloud.brickgame.paperClient.Protocol._
import org.slf4j.LoggerFactory
import org.seekloud.brickgame.Boot.roomManager
import org.seekloud.utils.{CirceSupport, SessionSupport}

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 4:13 PM
  */
trait PlayerService extends ServiceUtils with CirceSupport {

  implicit val system: ActorSystem

  implicit def executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  implicit val timeout: Timeout

  private[this] val log = LoggerFactory.getLogger("org.seekloud.hiStream.http.PlayerService")

  val playerIdGenerator = new AtomicInteger(100)

  val netSnakeRoute = {
    path("join") {
      parameter(
        'name.as[String]
      ) { name =>
        val id = playerIdGenerator.getAndIncrement()
        handleWebSocketMessages(webSocketChatFlow(id, name))
      }
    }
  }

  def webSocketChatFlow(id:Int, name: String): Flow[Message, Message, Any] = {
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
      .via(RoomManager.joinGame(roomManager, id, name))
      .map {
        case msg:Protocol.GameMessage =>
          val sendBuffer = new MiddleBufferInJvm(409600)
          BinaryMessage.Strict(ByteString(
            //encoded process
            msg.fillMiddleBuffer(sendBuffer).result()

          ))

        case x =>
          println("unknown")
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
