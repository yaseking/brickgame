package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.model.ReplayInfo
import com.neo.sk.carnie.ptcl.EsheepPtcl.PlayerMsg
import org.scalajs.dom
import io.circe.syntax._
import io.circe.generic.auto._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import com.neo.sk.carnie.paperClient.WebSocketProtocol._
/**
  * Created by haoshuhan on 2018/11/2.
  */

@JSExportTopLevel("paperClient.Main")
object Main extends js.JSApp {
  def main(): Unit = {
    val url = dom.window.location.href.split("carnie/")(1)
    val info = url.split("\\?")
    val playerMsgMap = info(1).split("&").map {
      a =>
        val b = a.split("=")
        (b(0), b(1))
    }.toMap
    val sendData = PlayerMsg(playerMsgMap).asJson.noSpaces
    println(s"sendData: $sendData")
    info(0) match {
      case "playGame" =>
        val playerId = if(playerMsgMap.contains("playerId")) playerMsgMap("playerId") else "unKnown"
        val playerName = if(playerMsgMap.contains("playerName")) playerMsgMap("playerName") else "unKnown"
        new NetGameHolder("playGame", PlayGamePara(playerId, playerName)).init()

      case "watchGame" =>
        val roomId = playerMsgMap.getOrElse("roomId", "1000")
        val playerId = playerMsgMap.getOrElse("playerId", "unknown")
        println(s"Frontend-roomId: $roomId, playerId:$playerId")
        new NetGameHolder("watchGame", WatchGamePara(roomId, playerId)).init()

      case "watchRecord" =>
        val recordId = playerMsgMap.getOrElse("recordId", "1000001")
        val playerId = playerMsgMap.getOrElse("playerId", "1000001")
        val frame = playerMsgMap.getOrElse("frame", "0")
        val accessCode = playerMsgMap.getOrElse("accessCode", "abc")
        new NetGameHolder4WatchRecord(WatchRecordPara(recordId, playerId, frame, accessCode)).init()

      case _ =>
        println("Unknown order!")
    }
  }
}
