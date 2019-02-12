package org.seekloud.brickgame.paperClient

import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.seekloud.brickgame.util.Component

import scala.xml.Elem

class JoinPage(login: String => Unit) extends Component{

  def getNickname: Unit = {
    val nickname = dom.document.getElementById("textField").asInstanceOf[Input].value
    println(s"nickname: $nickname")
    login(nickname)
  }

  override def render: Elem = {
    <div>
      <body style="text-align:centre;">
        <input type="text" id="textField" placeholder="用户名" class="form-control"></input>
        <button id="logIn" class="btn btn-info" style="margin: 0rem 1rem 0rem 1rem;" onclick={() => getNickname }>进入游戏</button>
        <div>
          <canvas id="GameView" tabindex="1" style="position: relative;"></canvas>
        </div>
      </body>
    </div>
  }

}
