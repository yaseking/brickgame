package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.paperClient.Protocol.{frameRate1, frameRate2}
import com.neo.sk.carnie.paperClient.WebSocketProtocol.PlayGamePara
import com.neo.sk.carnie.util.Component
import com.neo.sk.carnie.{Main, Routes}
import com.neo.sk.carnie.ptcl.RoomApiProtocol.RoomListRsp4Client
import com.neo.sk.carnie.util.{Http, JsFunc}
import mhtml.{Rx, Var}
import io.circe.generic.auto._
import io.circe.syntax._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.mutable
import scala.util.Random
import scala.xml.Elem

class RoomListPage(webSocketPara: PlayGamePara) extends Component {

  case class Room(id:Int,model:String,isLocked:Boolean)
//  var modelLists = List(Room(0,"/carnie/static/img/coffee2.png","正常模式"),
//    Room(1,"/carnie/static/img/game.png","反转模式"),Room(2,"/carnie/static/img/rocket1.png","加速模式"))
  var roomSelectMap : Map[Int,Boolean] =Map()
  var roomSelected = Room(-1,"",false)
  //模式选择框
//  private val modelList: Var[List[Room]] = Var(modelLists)
  private val roomSelectFlag: Var[Map[Int, Boolean]] = Var(Map())
  val roomList: Var[List[Room]] = Var(List.empty[Room])
  var roomLists: List[Room] = List.empty[Room]
//  val roomLockMap:mutable.HashMap[Int, (Int, Boolean)] = mutable.HashMap.empty[Int, (Int, Boolean)]//(roomId -> (mode, hasPwd))

  def getRoomList() :Unit = {
    val url = Routes.Carnie.getRoomList4Front
    Http.getAndParse[RoomListRsp4Client](url).map {
      case Right(rsp) =>
        try {
          if (rsp.errCode == 0) {
            roomLists = rsp.data.roomList.sortBy(t => t).map { s =>
              val roomInfos = s.split("-")
              val roomId = roomInfos(0).toInt
              val modeName = if(roomInfos(1)=="0") "正常" else if(roomInfos(2)=="1") "反转" else "加速"
              val hasPwd = if(roomInfos(2)=="true") true else false
//              roomLockMap += roomId -> (roomInfos(1).toInt, hasPwd)
              Room(roomId , modeName ,hasPwd)
            }
            roomList := roomLists
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
    }
  }

  def init() :Unit={
    roomLists.foreach(game=>
        if(game.id == 0) roomSelectMap += (game.id -> true)
        else roomSelectMap += (game.id -> false)
      )
    roomSelectFlag := roomSelectMap
  }

  def selectClass(id: Int): Rx[String] = roomSelectFlag.map { flag =>
    if (flag.contains(id)) {
      flag(id) match {
        case true =>"game-selected"
        case _ =>"game-not-selected"
      }
    }
    else "game-not-selected"
  }

  def selectGame(id: Int): Unit = {
    if (roomSelectMap.contains(roomSelected.id))
      roomSelectMap += (roomSelected.id -> false)
    if (roomSelectMap.contains(id))
      roomSelectMap += (id -> true)
    roomSelectFlag := roomSelectMap
    roomSelected = roomLists.find {
      _.id == id
    }.get
    println(roomSelected.id)
  }

  def gotoGame(modelId: Int, headId: Int, playerId: String, playerName: String): Unit = {
    if (modelId == -1 || headId == -1) JsFunc.alert("请选择模式和头像!")
    else {
      Main.refreshPage(new CanvasPage().render)
      val frameRate = if(modelId==2) frameRate2 else frameRate1
      new NetGameHolder("playGame", PlayGamePara(playerId, playerName, modelId, headId), modelId, headId, frameRate).init()
    }
  }

  private val roomDiv = roomList.map{
    case Nil =>
      <div style="text-align: center;">
      <h style="font-size: 35px;color:white;text-align: center;">当前没有正在游戏的房间 </h>
      </div>
//      <table class="table">
//        <thead class="thead-light">
//          <tr>
//            <th scope="col">Locked Or Not</th>
//            <th scope="col">Room ID</th>
//            <th scope="col">Model</th>
//          </tr>
//        </thead>
//        <tbody>
//          <tr>
//            <td></td>
//            <td></td>
//            <td></td>
//          </tr>
//        </tbody>
//      </table>
    case list =>
      <table class="table">
        <thead class="thead-light">
          <tr>
            <th scope="col">Locked Or Not</th>
            <th scope="col">Room ID</th>
            <th scope="col">Model</th>
          </tr>
        </thead>
        <tbody>
          {list.map{l =>
          <tr class={selectClass(l.id)}>
            <td>{if(l.isLocked) <image id="luffyImg" src="/carnie/static/img/luffy.png" style="width: 10px;height: 10px;"></image>
                else <image id="luffyImg" src="/carnie/static/img/fatTiger.png" style="width: 10px;height: 10px;"></image>}</td>
            <td>{l.id}</td>
            <td>{l.model}</td>
          </tr>
            }
          }
        </tbody>
        </table>
  }

  override def render: Elem = {
    getRoomList()
    init()
    <div style="background-color: #333333;height:100%" id="body" >
      {roomDiv}
      <div style="text-align: center;">
        <button type="button"   style="font-size: 30px ;" class="btn-primary" onclick=
        {() => gotoGame(1,1,webSocketPara.playerId,webSocketPara.playerName)}>进入游戏</button>
      </div>
    </div>
  }
}
