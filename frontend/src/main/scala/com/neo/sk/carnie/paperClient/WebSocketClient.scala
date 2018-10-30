package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.model.ReplayInfo
import com.neo.sk.carnie.paperClient.Protocol._
import org.seekloud.byteobject.ByteObject.bytesDecode
import org.seekloud.byteobject.decoder
import org.scalajs.dom
import org.scalajs.dom.WebSocket
import org.scalajs.dom.raw.{Blob, Event, FileReader, MessageEvent}
import org.seekloud.byteobject.MiddleBufferInJs

import scala.scalajs.js.typedarray.ArrayBuffer
import org.seekloud.byteobject.ByteObject._

/**
  * Created by dry on 2018/9/3.
  **/
class WebSocketClient (
                        connectOpenSuccess: (Event, String) => Unit,
                        connectError: Event => Unit,
                        messageHandler: GameMessage => Unit,
                        close:Event => Unit
                      ) {

  private var wsSetup = false
  private var gameStreamOpt: Option[WebSocket] = None

  def setUp(playId: String, name: String, order: String, replayInfo: Option[ReplayInfo]): Unit = {
    if (!wsSetup) {
      val url = order match {
        case "playGame" =>
          getWebSocketUri(playId, name)
        case "watchGame" =>
          println("set up watchGame webSocket!")
          getWebSocketUri4WatchGame(playId, name)
        case "watchRecord" =>
          val info = replayInfo.getOrElse(ReplayInfo("1000001", "1000001", "1000", "abcd1000"))
          getWebSocketUri4WatchRecord(info)
      }
      val gameStream = new WebSocket(url)
      gameStreamOpt = Some(gameStream)

      gameStream.onopen = { event0: Event =>
        wsSetup = true
        connectOpenSuccess(event0, order)
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
        gameStream.send(msg.fillMiddleBuffer(sendBuffer).result())

      case None => //
    }
  }

  def getWebSocketUri(idOfChatParticipant: String, nameOfChatParticipant: String): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}/carnie/join?id=$idOfChatParticipant&name=$nameOfChatParticipant"
  }

  def getWebSocketUri4WatchGame(idOfChatParticipant: String, nameOfChatParticipant: String): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}/carnie/watchGame?roomId=$idOfChatParticipant&playerId=$nameOfChatParticipant"
  }

  def getWebSocketUri4WatchRecord(replayInfo: ReplayInfo): String = {
    import replayInfo._
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}/carnie/joinWatchRecord?recordId=$recordId&playerId=$playerId&frame=$frame&accessCode=$accessCode"
  }

  def getWsState = wsSetup


}
