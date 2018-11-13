package com.neo.sk.carnie.paperClient

/**
  * Created by haoshuhan on 2018/11/2.
  */
object WebSocketProtocol {
  sealed trait WebSocketPara

  case class PlayGamePara(playerId: String, playerName: String) extends WebSocketPara

  case class WatchGamePara(roomId: String, playerId: String, accessCode: String) extends WebSocketPara

  case class WatchRecordPara(recordId: String, playerId: String, frame: String, accessCode: String) extends WebSocketPara

}
