package brickgame.frontendAdmin.pages

import brickgame.frontendAdmin.Routes
import brickgame.frontendAdmin.util.{Http, JsFunc, Page}
import org.seekloud.brickgame.ptcl.AdminPtcl._
import brickgame.frontendAdmin.util.TimeTool
import mhtml.Var
import org.scalajs.dom
import org.seekloud.brickgame.ptcl.RoomApiProtocol.RoomMapRsp

import scala.xml.Elem
import scala.concurrent.ExecutionContext.Implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
/**
  * User: Jason
  * Date: 2018/12/18
  * Time: 13:04
  */
object ViewPage extends Page{
  override val locationHashString: String = "#/View"

  val playerRecordsVar = Var(List.empty[PlayerRecord])

  var playerAmount = 0

  var playerAmountToday= 0

  val isGetAmount = Var(false)

  var roomNum = 0

  var playerNum = 0

  var isGetRoomAmount = Var(false)

  def getPlayerRecordAmount() : Unit = {
    val url = Routes.Admin.getActiveUserInfo
    Http.getAndParse[PlayerAmountRsp](url).map{
      case Right(rsp) =>
        try {
          if (rsp.errCode == 0) {
            println(s"total :${rsp.playerAmount}, today: ${rsp.playerAmountToday}")
            playerAmount = rsp.playerAmount
            playerAmountToday = rsp.playerAmountToday
            isGetAmount := true
          }
          else {
            println("error======" + rsp.msg)
            JsFunc.alert(rsp.msg)
          }
        }
        catch {
          case e: Exception =>
            println(e)
        }

      case Left(e) =>
        println("error======" + e)
        JsFunc.alert("对不起，您尚未登陆!")
        dom.window.location.href=""
    }
  }

  def getRoomPlayerList(): Unit = {
    val url = Routes.Admin.getRoomPlayerList
    Http.getAndParse[RoomMapRsp](url).map{
      case Right(rsp) =>
        try {
          if (rsp.errCode == 0) {
            roomNum = rsp.roomNum
            playerNum = rsp.playerNum
            isGetRoomAmount := true
          }
          else {
            println("error======" + rsp.msg)
            JsFunc.alert(rsp.msg)
          }
        }
        catch {
          case e: Exception =>
            println(e)
        }

      case Left(e) =>
        println("error======" + e)
        JsFunc.alert("对不起，您尚未登陆!")
        dom.window.location.href=""
    }
  }

  override def render: Elem = {
    getPlayerRecordAmount()
    getRoomPlayerList()
    <div>
      <div class="container">
        <h4 style="padding-left: 100px;"><b>用户活跃统计：</b></h4>
        <div>
          {
          isGetAmount.map{
            case true => <div style="font-family:楷体;font-size:20px;">历史活跃总数:{playerAmount},今日活跃次数:{playerAmountToday}。</div>
            case _ => <div></div>
          }
          }
        </div>
        <br></br>
        <h4 style="padding-left: 100px;"><b>实时房间玩家数：</b></h4>
        <div>
          {
          isGetRoomAmount.map{
            case true => <div style="font-family:楷体;font-size:20px;">当前房间数:{roomNum},当前玩家数:{playerNum}。</div>
            case _ => <div></div>
          }
          }
        </div>
      </div>
    </div>
  }
}
