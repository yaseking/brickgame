package brickgame.frontendAdmin.pages

import brickgame.frontendAdmin.Routes
import brickgame.frontendAdmin.util.{Http, JsFunc, Page}
import org.seekloud.brickgame.ptcl.AdminPtcl._
import mhtml.Var
import io.circe.generic.auto._
import io.circe.syntax._

import scala.xml.{Elem, Node}
import scala.concurrent.ExecutionContext.Implicits.global

object CurrentDataPage extends Page{
  override val locationHashString: String = "#/CurrentDataPage"

  private val userList = Var(List.empty[UserInfo])

  private val userDiv = userList.map{i=>buildUserInfo(i)}

  def buildUserInfo(list: List[UserInfo]): Elem = {
    if (list != Nil){
      <div>
        <div class="row" style="margin-top:10px">
          <div class="col-xs-2" style="text-align:center;font-weight:bold"></div>
          <div class="col-xs-1" style="text-align:center;font-weight:bold">用户名：</div>
          <div class="col-xs-1" style="text-align:center;font-weight:bold"></div>
          <div class="col-xs-2" style="text-align:center;font-weight:bold">状态：</div>
        </div>
        {
        list.map{user=>getUserItem(user)}
        }
      </div>
    }else{
      <div >
        <div class="row" style="margin-top:10px">
          <div class="col-xs-2" style="text-align:center;font-weight:bold"></div>
          <div class="col-xs-2" style="text-align:center;font-weight:bold">
            <p>没有用户</p>
          </div>
        </div>
      </div>
    }
  }

  def getUserItem(user: UserInfo):Elem = {
    <div class="row" style="margin-top:10px">
      <div class="col-xs-2" style="text-align:center;font-weight:bold"></div>
      <div class="col-xs-1" style="text-align:center;font-weight:bold">
        {user.name}
      </div>
      <div class="col-xs-1" style="text-align:center;font-weight:bold"></div>
      <div class="col-xs-2" style="text-align:center;font-weight:bold">{
        if(user.state)
          <button style="height:auto;weight:auto;" class="btn btn-danger" onclick={()=>forbidUser(user.name)}>禁止</button>
        else
          <button style="height:auto;weight:auto;" class="btn btn-info" onclick={()=>enableUser(user.name)}>解禁</button>
        }</div>
    </div>
  }

  def forbidUser(name:String): Unit = {
    val url = Routes.Admin.forbidPlayer
    Http.postJsonAndParse[SuccessRsp](url, PlayerReq(name).asJson.noSpaces).map {
      case Right(rsp) =>
        if(rsp.errCode==0) {
          showUserInfo
        } else {
          println(s"Failed to forbidUser, as ${rsp.msg}")
        }

      case Left(e) =>
        println(s"errors in forbidUser: $e")
    }
  }

  def enableUser(name:String): Unit = {
    val url = Routes.Admin.enablePlayer
    Http.postJsonAndParse[SuccessRsp](url, PlayerReq(name).asJson.noSpaces).map {
      case Right(rsp) =>
        if(rsp.errCode==0) {
          showUserInfo
        } else {
          println(s"Failed to forbidUser, as ${rsp.msg}")
        }

      case Left(e) =>
        println(s"errors in forbidUser: $e")
    }
  }

  def showUserInfo: Unit = {
    val url = Routes.Admin.showPlayerInfo
    Http.getAndParse[UserInfoRsp](url).map {
      case Right(rsp) =>
        if(rsp.errCode==0) {
          userList := rsp.data
        }else{
          println(s"fail to showUserInfo, ${rsp.msg}")
        }

      case Left(e) =>
        println(s"errors in showUserInfo: $e")
    }
  }

  override def render: Elem = {
    showUserInfo
//    getRoomList()
    <div class="container">
      {userDiv}
    </div>
  }
}
