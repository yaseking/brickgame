package org.seekloud.brickgame.paperClient

import org.seekloud.brickgame.util.{Component, Http, JsFunc, Page}

import scala.xml.Elem

object CanvasPage extends Page{
  override val locationHashString: String = "#/CanvasPage"

  def sendExpression(num:Int) = {
    NetGameHolder.sendExpression(num)
  }

  override def render: Elem = {
    <div  class="row">
      <div id="div1" class="col-md-2">
        <img src="/brickgame/static/img/666.png" style="margin-left:5%;width:40%;display:inline;" onclick={()=>sendExpression(0)}></img>
        <img src="/brickgame/static/img/cute.png" style="margin-left:5%;width:40%;display:inline;" onclick={()=>sendExpression(1)}></img>
        <br></br>
        <img src="/brickgame/static/img/happy.png" style="margin-left:5%;width:40%;display:inline;" onclick={()=>sendExpression(2)}></img>
        <img src="/brickgame/static/img/hello.png" style="margin-left:5%;width:40%;display:inline;" onclick={()=>sendExpression(3)}></img>
        <br></br>
        <img src="/brickgame/static/img/poor.png" style="margin-left:5%;width:40%;display:inline;" onclick={()=>sendExpression(4)}></img>
        <img src="/brickgame/static/img/sick.png" style="margin-left:5%;width:40%;display:inline;" onclick={()=>sendExpression(5)}></img>
      </div>

      <div class="col-md-10">
        <canvas id="GameView" tabindex="1"></canvas>
      </div>

    </div>
  }
  //borderView style="position: relative;"
//  <img id="fatTigerImg" src="/brickgame/static/img/fatTiger.png" style="width: 30px;height: 30px;display: inline-block;"></img>
//    <img id="BobImg" src="/brickgame/static/img/Bob.png" style="width: 30px;height: 30px;display: inline-block;"></img>
//    <img id="yangImg" src="/brickgame/static/img/yang.png" style="width: 30px;height: 30px;display: inline-block;"></img>
//    <img id="smileImg" src="/brickgame/static/img/smile.png" style="width: 30px;height: 30px;display: inline-block;"></img>
//    <img id="pigImg" src="/brickgame/static/img/pig.png" style="margin-left:15%;width:40%;display:block;"></img>
}
