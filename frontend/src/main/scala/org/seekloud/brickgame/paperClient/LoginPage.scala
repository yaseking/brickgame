package org.seekloud.brickgame.paperClient

import io.circe.generic.auto._
import io.circe.syntax._
import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.html.{Button, Input}
import org.scalajs.dom.raw.KeyboardEvent
import org.seekloud.brickgame.{Main, Routes}
import org.seekloud.brickgame.ptcl.AdminPtcl._
import org.seekloud.brickgame.util.{Component, Http, JsFunc, Page}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.{Elem, Node}

/**
  * User: Jason
  * Date: 2018/12/18
  * Time: 9:54
  */
object LoginPage extends Component{
//  override val locationHashString: String = "#/LoginPage"

  def login():Unit = {
    val name = dom.window.document.getElementById("username").asInstanceOf[Input].value
    val password = dom.window.document.getElementById("password").asInstanceOf[Input].value
    val url = Routes.Player.login
    val data = LoginReq(name, password).asJson.noSpaces
    Http.postJsonAndParse[SuccessRsp](url, data).map {
      case Right(rsp) =>
        try {
          if (rsp.errCode == 0) {
            //refreshPage
//            dom.window.location.href="#/CurrentDataPage"

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
        JsFunc.alert("Login error!")
    }
  }

  def switch: Unit = {
    dom.document.getElementById("loginPage").setAttribute("display","none")
    dom.document.getElementById("loginPage").setAttribute("hidden","hidden")
    Main.refreshPage(RegisterPage.render)
  }

  val Email:Var[Node] =Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem;">
      <label class="col-md-3" style="text-align:right">用户名</label>
      <div class="col-md-6">
        <input type="text" id="username4login" placeholder="用户名" class="form-control" autofocus="true"></input>
      </div>
    </div>
  )

  val PassWord:Var[Node] =Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem">
      <label class="col-md-3" style="text-align:right;">密码</label>
      <div class="col-md-6">
        <input type="password" id="password4login" placeholder="密码" class="form-control"></input>
      </div>
    </div>
  )

  val Nickname:Var[Node] =Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem">
      <label class="col-md-3" style="text-align:right;">昵称</label>
      <div class="col-md-6">
        <input type="text" id="nickname4login" placeholder="昵称" class="form-control" onkeydown={e:KeyboardEvent => loginByEnter(e)}></input>
      </div>
    </div>
  )

  def loginByEnter(event: KeyboardEvent):Unit = {
    if(event.keyCode == 13)
      dom.document.getElementById("logIn").asInstanceOf[Button].click()
  }

  val Title:Var[Node]=Var(
    <div class="row" style="margin-top: 15rem;margin-bottom: 4rem;">
      <div style="text-align: center;font-size: 4rem;">
        Brickgame登录页
      </div>
    </div>

  )

  val Btn:Var[Node]=Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem;text-align:center;">
      <button id="logIn" class="btn btn-info" style="margin: 0rem 1rem 0rem 1rem;" onclick={()=>login() } >
        登录
      </button>
      <button id="logIn" class="btn btn-successful" style="margin: 0rem 1rem 0rem 1rem;" onclick={()=>switch } >
        注册
      </button>
    </div>
  )

  val Btn2:Var[Node]=Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem;text-align:center;">
      <button id="logIn" class="btn btn-successful" style="margin: 0rem 1rem 0rem 1rem;" onclick={()=>login() } >
        注册
      </button>
    </div>
  )

  val Form:Var[Node]=Var(
    <form class="col-md-8 col-md-offset-2" style="border: 1px solid #dfdbdb;border-radius: 6px;padding:2rem 1rem 2rem 1rem;">
      {Email}
      {PassWord}
      {Nickname}
    </form>
  )

  override def render: Elem =
      <div id="loginPage">
        <div class="container">
          {Title}
          {Form}
        </div>
        <div class="container">
          {Btn}
        </div>
      </div>

}
