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

    val forbidPlayer = baseUrl + "/forbidPlayer"
    val enablePlayer = baseUrl + "/enablePlayer"
    val showPlayerInfo = baseUrl + "/showPlayerInfo"
    val getActiveUserInfo = baseUrl + "/getActiveUserInfo"
    val getRoomPlayerList = baseUrl + "/getRoomPlayerList"
  }
}
