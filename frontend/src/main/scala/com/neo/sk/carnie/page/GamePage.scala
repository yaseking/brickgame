package com.neo.sk.carnie.page

import com.neo.sk.carnie.util.Component

import scala.xml.Elem

/**
  * Created by dry on 2018/10/11.
  */
object GamePage extends Component {

  private val cannvas = <canvas id ="GameView" tabindex="1"></canvas>


  def init(): Unit = {

  }

  override def render: Elem = {
    init()
    <div>
      {cannvas}
    </div>
  }

}
