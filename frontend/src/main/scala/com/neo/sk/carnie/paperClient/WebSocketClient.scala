package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.paperClient.Protocol.{GameMessage, ReceivePingPacket, RequireSync, UserAction}
import com.neo.sk.carnie.util.byteObject.ByteObject.bytesDecode
import com.neo.sk.carnie.util.byteObject.decoder
import org.scalajs.dom
import org.scalajs.dom.WebSocket
import org.scalajs.dom.raw.{Blob, Event, FileReader, MessageEvent}
import com.neo.sk.carnie.util.MiddleBufferInJs

import scala.scalajs.js.typedarray.ArrayBuffer
import com.neo.sk.carnie.util.byteObject.ByteObject._

/**
  * Created by dry on 2018/9/3.
  **/
class WebSocketClient (
                        connectOpenSuccess: Event => Unit,
                        connectError: Event => Unit,
                        messageHandler: GameMessage => Unit,
                        close:Event => Unit
                      ) {

  private var wsSetup = false
  private var gameStreamOpt: Option[WebSocket] = None

  def joinGame(name: String): Unit = {
    if (!wsSetup) {
      val gameStream = new WebSocket(getWebSocketUri(name))
      gameStreamOpt = Some(gameStream)

      gameStream.onopen = { event0: Event =>
        wsSetup = true
        connectOpenSuccess(event0)
      }

      gameStream.onerror = { event: Event =>
        wsSetup = false
        gameStreamOpt = None
        connectError(event)
      }

      gameStream.onclose = { event: Event =>
        wsSetup = false
        gameStreamOpt = None
        close(event)
      }

      gameStream.onmessage = { event: MessageEvent =>
        event.data match {
          case blobMsg: Blob =>
            val fr = new FileReader()
            fr.readAsArrayBuffer(blobMsg)
            fr.onloadend = { _: Event =>
              val middleDataInJs = new MiddleBufferInJs(fr.result.asInstanceOf[ArrayBuffer]) //put data into MiddleBuffer
            val encodedData: Either[decoder.DecoderFailure, Protocol.GameMessage] = bytesDecode[Protocol.GameMessage](middleDataInJs) // get encoded data.
              encodedData match {
                case Right(data) =>
//                  data match {
//                    case ReceivePingPacket(_) =>
//
//                    case _ => println(blobMsg.size + "!!!" + data)
//                  }
                  messageHandler(data)

                case Left(e) =>
                  println(s"got error: ${e.message}")
              }
            }
        }
      }
    }
  }

  val sendBuffer = new MiddleBufferInJs(409600) //sender buffer

  def sendMessage(msg: UserAction): Unit = {
    gameStreamOpt match {
      case Some(gameStream) =>
        msg match {case RequireSync(_) => println("!!!!!!!!!!!!!???????")}
        gameStream.send(msg.fillMiddleBuffer(sendBuffer).result())

      case None => //
    }
  }

  def getWebSocketUri(nameOfChatParticipant: String): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}/carnie/netSnake/join?name=$nameOfChatParticipant"
  }

  def getWsState = wsSetup


}
