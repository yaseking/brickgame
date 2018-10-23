package com.neo.sk.carnie.page

import com.neo.sk.carnie.util.Component
import mhtml.Var
import mhtml._

import scala.xml.Elem

/**
  * Created by dry on 2018/10/11.
  */
object GamePage extends Component {

  private val cannvas = <canvas id ="GameView" tabindex="1"></canvas>

  private val startGameModal = Var(emptyHTML)

  def init(): Unit = {
    startGameModal :=
      <div style="text-align: center;">
        <form action="#" id="form" style="margin-top: 6em;">
          <h1 style="font-family: Verdana;font-size: 45px;">欢迎来到carnie</h1>
          <label for="name" style="font-size: 15px;">玩家名: </label><input style="height: 25px;width: 120px;" id="name" type="text" />
          <!--<input style="height: 25px;text-align: center;" id="join" type="button" class="btn btn-info" value="创建!"/>-->
          <button id="join" class="btn btn-info" style="height: 30%;">创建！</button>
        </form>
        <br></br>
      </div>
  }

  override def render: Elem = {
    init()
    <div>
      <div>{startGameModal}</div>
      {cannvas}
    </div>
  }

}
