package com.neo.sk.carnie.controller

import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.scene.{RoomListScene, RoomListSceneListener}
import com.neo.sk.carnie.utils.HttpUtil
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import com.neo.sk.carnie.Boot.executor
import com.neo.sk.carnie.ptcl.RoomApiProtocol.RoomListRsp

import scala.util.{Failure, Success}


class RoomListController(playerInfoInClient: PlayerInfoInClient, roomListScene: RoomListScene, context: Context, domain: String) extends HttpUtil {
  private val log = LoggerFactory.getLogger(this.getClass)
  //或许需要一个定时器
  updateRoomList()

  private def getRoomListInit() = {
    //需要起一个定时器，定时刷新请求
    val url = s"http://$domain/carnie/getRoomList"
    postJsonRequestSend("post",url,List(),"",needLogRsp = false).map{
      case Right(value) =>
        decode[RoomListRsp](value) match {
          case Right(data) =>
            if(data.errCode == 0){
              Right(data)
            }else{
              log.debug(s"获取列表失败，errCode:${data.errCode},msg:${data.msg}")
              Left("Error")
            }
          case Left(error) =>
            log.debug(s"获取房间列表失败，${error}")
            Left("Error")

        }
      case Left(error) =>
        log.debug(s"获取房间列表失败，${error}")
        Left("Error")
    }
  }

  private def updateRoomList() = {
    getRoomListInit().onComplete{
      case Success(res) =>
        res match {
          case Right(roomListRsp) =>
            roomListScene.updateRoomList(roomListRsp.data.roomList)
          case Left(e) =>
            log.error(s"获取房间列表失败，error：${e}")
        }
      case Failure(e) =>
        log.error(s"failure:${e}")
    }
  }

  roomListScene.listener = new RoomListSceneListener {
    override def confirm(roomId: String): Unit = {
      println(s"roomId: $roomId")
    }
  }
}
