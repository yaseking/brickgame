package com.neo.sk.carnie.frontendAdmin.pages

import com.neo.sk.carnie.frontendAdmin.Routes
import com.neo.sk.carnie.frontendAdmin.util.{Http, JsFunc, Page}
import com.neo.sk.carnie.ptcl.RoomApiProtocol._
import mhtml.Var
import org.scalajs.dom
import io.circe.generic.auto._
import io.circe.syntax._

import scala.util.{Failure, Success}
import scala.xml.{Elem, Node}
import scala.concurrent.ExecutionContext.Implicits.global


/**
  * User: Jason
  * Date: 2018/12/19
  * Time: 9:57
  */
object CurrentDataPage extends Page{
  override val locationHashString: String = "#/CurrentDataPage"

  case class Room(id: Int, model: String)

  val roomListVar: Var[List[Room]] = Var(List.empty)

  var roomList: List[Room] = List.empty

  val roomPlayerList: Var[List[PlayerIdName]] = Var(List.empty)

//  var roomPlayerList: List[PlayerIdName] = List.empty

  var roomPlayerMap: Map[Room,List[PlayerIdName]] = Map()

  var isGetPlayer = false

  def getRoomList() : Unit = {
    val url = Routes.Admin.getRoomList
    Http.getAndParse[RoomListRsp4Client](url).map{
      case Right(rsp) =>
        try {
          if (rsp.errCode == 0) {
            roomList = rsp.data.roomList.map{ s =>
              val roomInfo = s.split("-")
              val roomId = roomInfo(0).toInt
              val modeName = if(roomInfo(1)=="0") "正常" else if(roomInfo(2)=="1") "反转" else "加速"
              //              val hasPwd = if(roomInfo(2)=="true") true else false
              Room(roomId.toInt,modeName)
            }
            roomListVar := roomList
//            if(roomList.nonEmpty){
//              roomList.foreach{ r =>
//                getRoomPlayerList(r.id)
//                if(isGetPlayer){
//                  roomPlayerMap += (r -> roomPlayerList)
//                  isGetPlayer = false
//                }
//              }
//            }
//            println(roomPlayerMap)
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
        JsFunc.alert("Login error!")
    }
  }

  def getRoomPlayerList(roomId: Int) ={
    val url = Routes.Admin.getRoomPlayerList
    val data = RoomIdReq(roomId).asJson.noSpaces
    Http.postJsonAndParse[PlayerListRsp](url,data).map{
      case Right(rsp) =>
        if (rsp.errCode == 0) {
//          roomPlayerList :=
          rsp.data.playerList
//          isGetPlayer = true
        }
        else {
          println("error======" + rsp.msg)
//          JsFunc.alert(rsp.msg)
          List()
        }

      case Left(e) =>
        println("error======" + e)
//        JsFunc.alert("Login error!")
        List()
    }
  }

  private val roomDiv = roomListVar.map{
    case Nil =>
      <div style="text-align: center;"><h3>暂无数据</h3></div>

    case r => showRoomL(r)

  }

  private def showRoomL(roomL: List[Room]):Elem = {
    <div style="margin-top: 30px;">
      <div class="row">
        <div class="col-xs-2">
        </div>
        <div class="col-xs-1" style="text-align:center;font-weight:bold">roomId</div>
        <div class="col-xs-1" style="text-align:center;font-weight:bold">model</div>
        <div class="col-xs-2" style="text-align:center;font-weight:bold">playerId</div>
        <div class="col-xs-2" style="text-align:center;font-weight:bold">playerName</div>
        <br></br>
      </div>
      <div>
        {roomL.map { i => showRoom(i) }}
      </div>
    </div>
  }

  private def showRoom(room: Room):Elem = {
    getRoomPlayerList(room.id).onComplete{
      case Success(r) => roomPlayerList := r
      case Failure(e) => roomPlayerList := List()
    }
    <div class="row">
      <div class="col-xs-2">
      </div>
      <div class="col-xs-1" style="text-align:center;">
        {room.id}
      </div>
      <div class="col-xs-1" style="text-align:center;">
        {room.model}
      </div>
      <div class="col-xs-2" style="text-align:center;">
        {
         roomPlayerList.map{
           r => r.map{ i =>
             <div>{s"${i.playerId}"}</div>
           }
         }
        }
      </div>
      <div class="col-xs-2" style="text-align:center;">
        {
        roomPlayerList.map{
          r => r.map{ i =>
            <div>{s"${i.nickname}"}</div>
          }
        }
        }
      </div>
      <br></br>
    </div>
  }

  override def render: Elem = {
    getRoomList()
    <div>
      {roomDiv}
    </div>
  }
}
