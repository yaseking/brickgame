package org.seekloud.brickgame.paperClient

import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.scalajs.dom.raw.KeyboardEvent
import org.seekloud.brickgame.Main
import org.seekloud.brickgame.util.{Component, Page}

import scala.xml.{Elem, Node}

object JoinPage extends Page{
  override val locationHashString: String = "#/JoinPage"

  def getNickname: Unit = {
    val nickname = dom.document.getElementById("username").asInstanceOf[Input].value
    println(s"nickname: $nickname")
    dom.document.getElementById("joinPage").setAttribute("display","none")
    dom.document.getElementById("joinPage").setAttribute("hidden","hidden")
    Main.refreshPage(CanvasPage.render)
    new NetGameHolder(nickname).init()
  }

  def switch: Unit = {
    dom.document.getElementById("joinPage").setAttribute("display","none")
    dom.document.getElementById("joinPage").setAttribute("hidden","hidden")
    Main.refreshPage(LoginPage.render)
  }


  val Email:Var[Node] =Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem;">
      <label class="col-md-3" style="text-align:right">昵称</label>
      <div class="col-md-6">
        <input type="text" id="username" placeholder="昵称" class="form-control" autofocus="true"></input>
      </div>
    </div>
  )

  val Title:Var[Node]=Var(
    <div class="row" style="margin-top: 15rem;margin-bottom: 4rem;">
      <div style="text-align: center;font-size: 5rem;">
        Brickgame
      </div>
    </div>
  )

  val Btn:Var[Node]=Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem;text-align:center;">
      <button id="logIn" class="btn btn-info" style="margin: 0rem 1rem 0rem 1rem;" onclick={()=>getNickname} >
        游客登录
      </button>
    </div>
  )

  val Btn2:Var[Node]=Var(
    <div class="row" style="padding: 1rem 1rem 1rem 1rem;text-align:center;">
      <button id="logIn" class="btn btn-success" style="margin: 0rem 1rem 0rem 1rem;" onclick={()=>switch}>
        账号登录
      </button>
    </div>
  )

  val Form:Var[Node]=Var(
    <form class="col-md-8 col-md-offset-2" style="border: 1px solid #dfdbdb;border-radius: 6px;padding:2rem 1rem 2rem 1rem;">
      {Email}
    </form>
  )

  override def render: Elem = {
    <div id="joinPage">
      <div class="container">
        {Title}
        {Form}
      </div>
      <div class="container">
        {Btn}
        {Btn2}
      </div>
    </div>
  }

}
