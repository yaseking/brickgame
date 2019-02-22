package org.seekloud.brickgame.paperClient

import org.seekloud.brickgame.util.{Component, Page}

import scala.xml.Elem

object CanvasPage extends Page{
  override val locationHashString: String = "#/CanvasPage"

  override def render: Elem = {
    <div>
      <canvas id="GameView" tabindex="1"></canvas>
    </div>
  }//borderView style="position: relative;"

}
