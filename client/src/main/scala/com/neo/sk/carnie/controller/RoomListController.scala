package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.scene.{GameScene, LayeredGameScene, RoomListScene, RoomListSceneListener}
import com.neo.sk.carnie.utils.HttpUtil
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import com.neo.sk.carnie.Boot.executor
import com.neo.sk.carnie.ptcl.RoomApiProtocol.RoomListRsp
import com.neo.sk.carnie.utils.SecureUtil._
import scala.util.{Failure, Success}


class RoomListController(playerInfoInClient: PlayerInfoInClient, roomListScene: RoomListScene, context: Context, domain: String) extends HttpUtil {
  private val log = LoggerFactory.getLogger(this.getClass)
  //或许需要一个定时器,定时刷新请求
  updateRoomList()

  private def getRoomListInit() = {
    //fixme appId和gsKey放在application中，不要暴露
    val url = s"http://$domain/carnie/getRoomList"
//    val url = s"http://10.1.29.250:30368/carnie/getRoomList"
    val appId = 1000000003.toString
    val sn = appId + System.currentTimeMillis().toString
    val data = {}.asJson.noSpaces
    val (timestamp, nonce, signature) = generateSignatureParameters(List(appId, sn, data), "rt7gJt5hkrdYk31W2lF4I0TlAgaBQpsb")
    val params = PostEnvelope(appId, sn, timestamp, nonce, data,signature).asJson.noSpaces
//    val jsonData = genPostEnvelope("esheep",System.nanoTime().toString,{}.asJson.noSpaces,"").asJson.noSpaces
    postJsonRequestSend("post",url,List(),params,needLogRsp = false).map{
      case Right(value) =>
        decode[RoomListRsp](value) match {
          case Right(data) =>
            if(data.errCode == 0){
              println(s"roomData: $data")
              Right(data)
            }else{
              log.debug(s"获取列表失败，errCode:${data.errCode},msg:${data.msg}")
              Left("Error")
            }
          case Left(error) =>
            log.debug(s"获取房间列表失败1，${error}")
            Left("Error")

        }
      case Left(error) =>
        log.debug(s"获取房间列表失败2，${error}")
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
      val mode = 0
      val img = 0
      val frameRate = 150
      val playGameScreen = new GameScene(img, frameRate)
      val LayeredGameScreen = new LayeredGameScene(img, frameRate)
      context.switchScene(playGameScreen.getScene, fullScreen = true)
      new GameController(playerInfoInClient, context, playGameScreen,LayeredGameScreen, mode, frameRate).joinByRoomId(domain, roomId.toInt, img)
    }
  }

  def showScene: Unit = {
    Boot.addToPlatform {
      context.switchScene(roomListScene.getScene, "RoomList", false)
    }
  }
}
