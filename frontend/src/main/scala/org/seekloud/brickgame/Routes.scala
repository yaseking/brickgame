package org.seekloud.brickgame

object Routes {
  val baseUrl = "/brickgame"

  object Esheep {
    val playGame = baseUrl + "/esheep/playGame"
  }

  object Carnie {
    val getRoomList = baseUrl + "/getRoomList"
    val updateRoomList = baseUrl + "/updateRoomList"
    val getRoomList4Front = baseUrl + "/getRoomList4Front"
    val addSession = baseUrl + "/addSession"
  }

  object Player {
    val login = baseUrl + "/login"
    val register = baseUrl + "/register"
  }
}
