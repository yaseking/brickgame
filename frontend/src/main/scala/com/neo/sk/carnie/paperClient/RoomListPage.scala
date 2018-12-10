package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.paperClient.WebSocketProtocol.PlayGamePara
import com.neo.sk.carnie.util.Component
import mhtml.Var

import scala.xml.Elem

class RoomListPage(webSocketPara: PlayGamePara) extends Component {

  val roomList: Var[List[String]] = Var(List.empty[String])

  def getRoomList(): Unit = {

  }

  override def render: Elem = {
    <div></div>
  }
}
