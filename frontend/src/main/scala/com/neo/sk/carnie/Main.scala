package com.neo.sk.carnie

import com.neo.sk.carnie.paperClient.WebSocketProtocol._
import com.neo.sk.carnie.paperClient.{JoinGamePage, NetGameHolder, CanvasPage,NetGameHolder4WatchRecord}
import com.neo.sk.carnie.ptcl.EsheepPtcl.PlayerMsg
import io.circe.generic.auto._
import io.circe.syntax._
import mhtml.{Cancelable, mount}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.xml.Elem
/**
  * Created by haoshuhan on 2018/11/2.
  */

@JSExportTopLevel("paperClient.Main")
object Main extends js.JSApp {
  var currentPage:Elem = <div></div>
  def main(): Unit = {
    selectPage()
  }

  def newGameHolder(playerMsgMap: Map[String, String], info: Array[String]): Unit = {
    info(0) match {
      case "watchGame" =>
        val roomId = playerMsgMap.getOrElse("roomId", "1000")
        val playerId = playerMsgMap.getOrElse("playerId", "unknown")
        val accessCode = playerMsgMap.getOrElse("accessCode", "test123")
        println(s"Frontend-roomId: $roomId, playerId:$playerId, accessCode: $accessCode")
        new NetGameHolder("watchGame", WatchGamePara(roomId, playerId, accessCode)).render

      case "watchRecord" =>
        val recordId = playerMsgMap.getOrElse("recordId", "1000001")
        val playerId = playerMsgMap.getOrElse("playerId", "1000001")
        val frame = playerMsgMap.getOrElse("frame", "0")
        val accessCode = playerMsgMap.getOrElse("accessCode", "abc")
        new NetGameHolder4WatchRecord(WatchRecordPara(recordId, playerId, frame, accessCode)).render

      case "playGame" =>
        println("playGame!")

      case _ =>
        println("Unknown order!")
    }
  }

  def selectPage(): Cancelable = {
    val url = dom.window.location.href.split("carnie/")(1)
    val info = url.split("\\?")
    val playerMsgMap = info(1).split("&").map {
      a =>
        val b = a.split("=")
        (b(0), b(1))
    }.toMap

    info(0) match {
      case "playGame" =>
        val playerId = if (playerMsgMap.contains("playerId")) playerMsgMap("playerId") else "unKnown"
        val playerName = if (playerMsgMap.contains("playerName")) playerMsgMap("playerName") else "unKnown"
        currentPage = new JoinGamePage("playGame", PlayGamePara(playerId, playerName)).render

      case _ =>
        currentPage = new CanvasPage().render
        newGameHolder(playerMsgMap, info)
    }
    show()
  }

  def refreshPage(newPage: Elem): Cancelable = {
    currentPage = newPage
    show()
  }

  def show(): Cancelable = {
    val page =
      <div>
        {currentPage}
      </div>
    mount(dom.document.body, page)
  }

//  def play(modelId:Int, headId:Int,playerId:String, playerName:String): Unit = {
//    currentPage = new CanvasPage().render
//    val page =
//      <div>
//        {currentPage}
//      </div>
//    mount(dom.document.body, page)
//    val url = dom.window.location.href.split("carnie/")(1)
//    val info = url.split("\\?")
//    val playerMsgMap = info(1).split("&").map {
//      a =>
//        val b = a.split("=")
//        (b(0), b(1))
//    }.toMap
//    val sendData = PlayerMsg(playerMsgMap).asJson.noSpaces
//    println(s"sendData: $sendData")
//    val playerId = if (playerMsgMap.contains("playerId")) playerMsgMap("playerId") else "unKnown"
//    val playerName = if (playerMsgMap.contains("playerName")) playerMsgMap("playerName") else "unKnown"
//    new NetGameHolder("playGame", PlayGamePara(playerId, playerName,modelId,headId)).init()
//    currentPage = new NetGameHolder("playGame", PlayGamePara("test", "test",modelId,headId)).render
//  }
}
