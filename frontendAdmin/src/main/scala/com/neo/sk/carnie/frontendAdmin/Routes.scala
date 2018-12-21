package com.neo.sk.carnie.frontendAdmin

/**
  * User: Jason
  * Date: 2018/12/17
  * Time: 17:27
  */
object Routes {
  val baseUrl = "/carnie/admin"

  object Admin {
    val login = baseUrl + "/login"
    val logout = baseUrl + "/logout"
    val getRoomList = baseUrl + "/getRoomList"
    val getRoomPlayerList = baseUrl + "/getRoomPlayerList"
    val getPlayerRecord = baseUrl + "/getPlayerRecord"
    val getPlayerRecordByTime = baseUrl + "/getPlayerRecordByTime"
  }
}
