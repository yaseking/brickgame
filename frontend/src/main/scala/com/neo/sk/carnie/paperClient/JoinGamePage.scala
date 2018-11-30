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
class JoinGamePage(order: String, webSocketPara: WebSocketPara) extends Component {
  sealed case class Model(id:Int,img:String,name:String)
  sealed case class Head(id:Int,img:String)

  var modelLists = List(Model(0,"/carnie/static/img/Genji.png","正常模式"),
    Model(1,"/carnie/static/img/Dva.png","反转模式"),Model(2,"/carnie/static/img/Tracer.png","2倍加速模式"))
  var modelSelectMap : Map[Int,Boolean] =Map()
  var modelSelected = Model(0,"/carnie/static/img/Dva.png","反转模式")
  //游戏选择框
  private val modelList: Var[List[Model]] = Var(modelLists)
  private val modelSelectFlag: Var[Map[Int,Boolean]] = Var(Map())

  var headLists = List(Head(0,"/carnie/static/img/luffy.png"), Head(1,"/carnie/static/img/fatTiger.png"),Head(2,"/carnie/static/img/Bob.png"),
    Head(3,"/carnie/static/img/yang.png"), Head(4,"/carnie/static/img/smile.png"),Head(5,"/carnie/static/img/pig.png"))
  var headSelectMap : Map[Int,Boolean] =Map()
  var headSelected = Head(0,"/carnie/static/img/luffy.png")
  //游戏选择框
  private val headList: Var[List[Head]] = Var(headLists)
  private val headSelectFlag: Var[Map[Int,Boolean]] = Var(Map())

  private val modelDiv = modelList.map{ games =>
    games.map( game =>
      <div style="text-align:center;width:27%;margin:20px;">
        <div style="overflow:hidden" id={game.id.toString}>
          <div class={selectClass(game.id)} onclick={()=>selectGame(game.id)} style="margin-top:20px;height:250px;width:250px">
            <img class="home-img" src={game.img}></img>
            <p style="text-align:center;margin-top:10px;font-size: 30px;color:white" > {game.name}</p>
          </div>
        </div>
      </div>
    )
  }
  private val headDiv = headList.map{ games =>
    games.map( game =>
      <div style="width:27%;margin:20px;">
        <div style="overflow:hidden" id={game.id.toString}>
          <div class={selectHeadClass(game.id)} onclick={()=>selectHead(game.id)} style="margin-top:20px;height:100px;width:100px;text-align: center">
            <img class="home-img" src={game.img}></img>
          </div>
        </div>
      </div>
    )
  }
  def init()={
    modelLists.foreach(game=>
      modelSelectMap += (game.id -> false)
    )
    headLists.foreach(game=>
      headSelectMap += (game.id -> false)
    )
  }
  def selectClass(id:Int) = modelSelectFlag.map {flag=>
    if (flag.contains(id)){
      flag(id) match {
        case true =>"game-selected"
        case _ =>"game-not-selected"
      }
    }
    else "game-not-selected"
  }
  def selectGame(id: Int) ={
    if (modelSelectMap.contains(modelSelected.id))
      modelSelectMap += (modelSelected.id -> false)
    if (modelSelectMap.contains(id))
      modelSelectMap += (id -> true)
    modelSelectFlag := modelSelectMap
    modelSelected = modelLists.find{_.id == id}.get
    println(modelSelected.name)
  }

  def selectHeadClass(id:Int) = headSelectFlag.map {flag=>
    if (flag.contains(id)){
      flag(id) match {
        case true =>"game-selected"
        case _ =>"game-not-selected"
      }
    }
    else "game-not-selected"
  }
  def selectHead(id: Int) ={
    if (headSelectMap.contains(headSelected.id))
      headSelectMap += (headSelected.id -> false)
    if (headSelectMap.contains(id))
      headSelectMap += (id -> true)
    headSelectFlag := headSelectMap
    headSelected = headLists.find{_.id == id}.get
    println(headSelected.id)
  }
  
  def gotoGame(modelId: Int, headId: Int):Unit ={
    if(modelId == -1 || headId == -1) JsFunc.alert("请选择模式和头像!")
    else Main.play(modelId,headId)
  }
  override def render: Elem = {
    {init()}
    <html>
      <body background="/carnie/static/img/background1.png" style=" background-repeat:no-repeat ;background-size:100% 100%;background-attachment: fixed;"
            text="#68228B" onload="setInterval('clock()',1000)">
        <div style="text-align: center;margin: 0 auto;">
          <div  id="form">
            <h1 style="font-family: Verdana;font-size: 30px;color:white" >欢迎来到carnie</h1>
          </div>
          <div style="overflow: hidden;" >
            <div style="display:flex;flex-wrap: wrap;margin-left:16%" >
                {modelDiv}
            </div>
          </div>
          
          <div style="overflow: hidden;" >
            <div style="margin-top: 20px;">
              <p style="text-align: center; margin-top: 10px;font-size: 30px;color:white" > 选择头像</p>
            </div>
            <div  style="text-align: center;display: flex; flex-wrap: wrap;margin-left:19%">
                {headDiv}
            </div>
          </div>
          <button type="button" class="btn btn-primary" onclick={() => gotoGame(modelSelected.id,headSelected.id)}>进入游戏</button>
          </div>
      </body>
    </html>
  }


}
