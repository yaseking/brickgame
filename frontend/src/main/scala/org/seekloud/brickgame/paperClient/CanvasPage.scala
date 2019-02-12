package org.seekloud.brickgame.paperClient

import org.seekloud.brickgame.util.Component

import scala.xml.Elem

class CanvasPage extends Component{

  override def render: Elem = {
    <div>
      <canvas id="GameView" tabindex="1"></canvas>
    </div>
  }//borderView style="position: relative;"

}
