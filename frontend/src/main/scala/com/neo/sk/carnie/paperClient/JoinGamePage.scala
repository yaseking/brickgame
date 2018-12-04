package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.common.Constant
import com.neo.sk.carnie.util.JsFunc
import com.neo.sk.carnie.paperClient.Protocol._
import com.neo.sk.carnie.paperClient.WebSocketProtocol.{PlayGamePara, WebSocketPara}
import com.neo.sk.carnie.util.Component
import org.scalajs.dom

import scala.util.Random
//import org.scalajs.dom.ext.KeyCode
//import org.scalajs.dom.html.{Document => _, _}
import org.scalajs.dom.raw.{Event, HTMLAudioElement, VisibilityState}
import com.neo.sk.carnie.Main
import mhtml.{Rx, Var}
import scala.scalajs.js.Date
import io.circe.generic.auto._
import io.circe.syntax._


import scala.xml.Elem

/**
  * Created by dry on 2018/11/28.
  **/
class JoinGamePage(order: String, webSocketPara: PlayGamePara) extends Component {

  sealed case class Model(id: Int, img: String, name: String)

  sealed case class Head(id: Int, img: String)

  var modelLists = List(Model(0,"/carnie/static/img/coffee2.png","正常模式"),
    Model(1,"/carnie/static/img/game.png","反转模式"),Model(2,"/carnie/static/img/rocket1.png","加速模式"))
  var modelSelectMap : Map[Int,Boolean] =Map()
  var modelSelected = Model(0,"/carnie/static/img/coffee2.png","正常模式")
  //游戏选择框
  private val modelList: Var[List[Model]] = Var(modelLists)
  private val modelSelectFlag: Var[Map[Int, Boolean]] = Var(Map())

  var headLists = List(Head(0, "/carnie/static/img/luffy.png"), Head(1, "/carnie/static/img/fatTiger.png"), Head(2, "/carnie/static/img/Bob.png"),
    Head(3, "/carnie/static/img/yang.png"), Head(4, "/carnie/static/img/smile.png"), Head(5, "/carnie/static/img/pig.png"))
  var headSelectMap: Map[Int, Boolean] = Map()
  var headSelected = Head(0, "/carnie/static/img/luffy.png")
  //游戏选择框
  private val headList: Var[List[Head]] = Var(headLists)
  private val headSelectFlag: Var[Map[Int, Boolean]] = Var(Map())

  private val modelDiv = modelList.map { games =>
    games.map(game =>
      <div style="width:27%;margin:15px;">
        <div style="overflow:hidden" id={game.id.toString}>
          <div class={selectClass(game.id)} onclick={()=>selectGame(game.id)} style="margin-top:10px;height:150px;width:150px;">
            <img class="home-img" src={game.img}></img>
          </div>
          <p style="font-size: 15px;color:white;margin-left:12%;margin-right:12%" > {game.name}</p>
        </div>
      </div>
    )
  }

  private val headDiv = headList.map { games =>
    games.map(game =>
      <div style="width:27%;margin:15px;">
        <div style="overflow:hidden" id={game.id.toString}>
          <div class={selectHeadClass(game.id)} onclick={() => selectHead(game.id)} style="margin-top:10px;height:100px;width:100px;text-align: center">
            <img class="home-img" src={game.img}></img>
          </div>
        </div>
      </div>
    )
  }
  def init()={
    modelLists.foreach(game=>
      if(game.id == 0) modelSelectMap += (game.id -> true)
      else modelSelectMap += (game.id -> false)
    )
    modelSelectFlag := modelSelectMap
    val rnd = Random.nextInt(6)
    headLists.foreach(game =>
      if(game.id == rnd) headSelectMap += (game.id -> true)
      else headSelectMap += (game.id -> false)
    )
    headSelectFlag := headSelectMap
    headSelected = headLists(rnd)
  }

  def selectClass(id: Int): Rx[String] = modelSelectFlag.map { flag =>
    if (flag.contains(id)) {
      flag(id) match {
        case true =>"game-selected"
        case _ =>"game-not-selected"
      }
    }
    else "game-not-selected"
  }

  def selectGame(id: Int): Unit = {
    if (modelSelectMap.contains(modelSelected.id))
      modelSelectMap += (modelSelected.id -> false)
    if (modelSelectMap.contains(id))
      modelSelectMap += (id -> true)
    modelSelectFlag := modelSelectMap
    modelSelected = modelLists.find {
      _.id == id
    }.get
    println(modelSelected.name)
  }

  def selectHeadClass(id: Int): Rx[String] = headSelectFlag.map { flag =>
    if (flag.contains(id)) {
      flag(id) match {
        case true => "game-selected"
        case _ => "game-not-selected"
      }
    }
    else "game-not-selected"
  }

  def selectHead(id: Int): Unit = {
    if (headSelectMap.contains(headSelected.id))
      headSelectMap += (headSelected.id -> false)
    if (headSelectMap.contains(id))
      headSelectMap += (id -> true)
    headSelectFlag := headSelectMap
    headSelected = headLists.find {
      _.id == id
    }.get
    println(headSelected.id)
  }

  def gotoGame(modelId: Int, headId: Int, playerId: String, playerName: String): Unit = {
    if (modelId == -1 || headId == -1) JsFunc.alert("请选择模式和头像!")
    else {
      Main.refreshPage(new CanvasPage().render)
      val frameRate = if(modelId==2) frameRate2 else frameRate1
      new NetGameHolder("playGame", PlayGamePara(playerId, playerName, modelId, headId), headId, frameRate).init()
    }
  }
  override def render: Elem = {
    {init()}
    <div id="resizeDiv">
      <div  style="background-color: #333333;height:750px" id="body" >
        <div  id="selectPage">
          <div  id="form">
            <h1 style="font-family: Verdana;font-size:30px;color:white;text-align: center;" >欢迎来到carnie</h1>
          </div>
          <div style="overflow: hidden;" >
            <div style="display:flex;flex-wrap: nowrap;margin-left:18%;margin-right:18%" >
                {modelDiv}
            </div>
          </div>

          <div style="text-align: center;">
            <button type="button"   style="font-size: 30px ;" class="btn btn-primary" onclick=
            {() => gotoGame(modelSelected.id,headSelected.id,webSocketPara.playerId,webSocketPara.playerName)}>进入游戏</button>
          </div>


          <div style="overflow: hidden;" >
            <div style="margin-top: 10px;">
              <p style="text-align: center; margin-top: 20px;font-size: 20px;color:white" >选择头像</p>
            </div>
            <div  style="text-align: center;display: flex; flex-wrap: nowrap;margin-left:12%;margin-right:12%">
                {headDiv}
            </div>
          </div>
          </div>
      </div>
    </div>
  }


}
