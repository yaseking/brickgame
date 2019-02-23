package brickgame.frontendAdmin

/**
  * User: Jason
  * Date: 2018/12/17
  * Time: 17:27
  */
object Routes {
  val baseUrl = "/brickgame/admin"

  object Admin {
    val login = baseUrl + "/login"
    val logout = baseUrl + "/logout"
    val getRoomList = baseUrl + "/getRoomList"
    val getRoomPlayerList = baseUrl + "/getRoomPlayerList"
    val getPlayerRecord = baseUrl + "/getPlayerRecord"
    val getPlayerRecordByTime = baseUrl + "/getPlayerRecordByTime"
    val getPlayerRecordAmount = baseUrl + "/getPlayerRecordAmount"
    val getPlayerByTimeAmount = baseUrl + "/getPlayerByTimeAmount"
    val forbidPlayer = baseUrl + "forbidPlayer"
  }
}
