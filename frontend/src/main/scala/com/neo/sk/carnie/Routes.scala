package com.neo.sk.carnie

object Routes {
  val baseUrl = "/carnie"

  object Esheep {
    val playGame = baseUrl + "/esheep/playGame"
  }

  object Carnie {
    val getRoomList = baseUrl + "/getRoomList"
    val updateRoomList = baseUrl + "/updateRoomList"
    val getRoomList4Front = baseUrl + "/getRoomList4Front"
    val addSession = baseUrl + "/addSession"
  }
}
