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


object RegisterPage extends Component{
//  override val locationHashString: String = "#/LoginPage"

  def register():Unit = {
    val name = dom.window.document.getElementById("username4R").asInstanceOf[Input].value
    val password = dom.window.document.getElementById("password4R").asInstanceOf[Input].value
    val password2 = dom.window.document.getElementById("password4R2").asInstanceOf[Input].value
    if (password != password2) {
      JsFunc.alert("两次密码不一致。")
      dom.window.document.getElementById("password4R").asInstanceOf[Input].value = ""
      dom.window.document.getElementById("password4R2").asInstanceOf[Input].value = ""

    } else if(name.length>14 || password.length>14) {
      JsFunc.alert("用户名或密码超过14位")
    } else {
      val url = Routes.Player.register
      val data = LoginReq(name, password).asJson.noSpaces
      Http.postJsonAndParse[SuccessRsp](url, data).map {
        case Right(rsp) =>
          try {
            if (rsp.errCode == 0) {
              Main.refreshPage(CanvasPage.render)
              NetGameHolder.init(name)
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

  }

  val Email:Var[Node] =Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem;">
      <label class="col-md-3" style="text-align:right">用户名</label>
      <div class="col-md-6">
        <input type="text" id="username4R" placeholder="用户名" class="form-control" autofocus="true"></input>
      </div>
    </div>
  )

  val PassWord:Var[Node] =Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem">
      <label class="col-md-3" style="text-align:right;">密码</label>
      <div class="col-md-6">
        <input type="password" id="password4R" placeholder="密码" class="form-control"></input>
      </div>
    </div>
  )

  val PassWord2:Var[Node] =Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem">
      <label class="col-md-3" style="text-align:right;">确认密码</label>
      <div class="col-md-6">
        <input type="password" id="password4R2" placeholder="确认密码" class="form-control" onkeydown={e:KeyboardEvent => loginByEnter(e)}></input>
      </div>
    </div>
  )

  def loginByEnter(event: KeyboardEvent):Unit = {
    if(event.keyCode == 13)
      dom.document.getElementById("login4R").asInstanceOf[Button].click()
  }

  val Title:Var[Node]=Var(
    <div class="row" style="margin-top: 15rem;margin-bottom: 4rem;">
      <div style="text-align: center;font-size: 4rem;">
        Brickgame注册页
      </div>
    </div>
  )

  val Btn:Var[Node]=Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem;text-align:center;">
      <button id="login4R" class="btn btn-info" style="margin: 0rem 1rem 0rem 1rem;" onclick={()=>register() } >
        登陆
      </button>
    </div>
  )

  val Form:Var[Node]=Var(
    <form class="col-md-8 col-md-offset-2" style="border: 1px solid #dfdbdb;border-radius: 6px;padding:2rem 1rem 2rem 1rem;">
      {Email}
      {PassWord}
      {PassWord2}
    </form>
  )

  override def render: Elem =
    <div>
      <div class="container">
        {Title}
        {Form}
      </div>
      <div class="container">
        {Btn}
      </div>
    </div>

}
