package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.scene.{CreateRoomScene, _}
import com.neo.sk.carnie.utils.HttpUtil
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import com.neo.sk.carnie.Boot.executor
import com.neo.sk.carnie.paperClient.Protocol.{frameRate1, frameRate2}
import com.neo.sk.carnie.ptcl.RoomApiProtocol.{PwdReq, RoomListRsp, RoomListRsp4Client, SuccessRsp}
import com.neo.sk.carnie.utils.SecureUtil._
import javafx.scene.control.TextInputDialog
import javafx.scene.image.ImageView
import com.neo.sk.carnie.common.AppSetting

import scala.util.{Failure, Success}


class RoomListController(playerInfoInClient: PlayerInfoInClient, roomListScene: RoomListScene, context: Context, domain: String) extends HttpUtil {
  private val log = LoggerFactory.getLogger(this.getClass)
  //或许需要一个定时器,定时刷新请求
  updateRoomList()

  private def getRoomListInit() = {
    val url = s"http://$domain/carnie/getRoomList4Client"
    val appId = AppSetting.esheepGameId.toString
    val sn = appId + System.currentTimeMillis().toString
    val data = {}.asJson.noSpaces
    val gsKey = AppSetting.esheepGsKey
    val (timestamp, nonce, signature) = generateSignatureParameters(List(appId, sn, data), gsKey)
    val params = PostEnvelope(appId, sn, timestamp, nonce, data,signature).asJson.noSpaces
    postJsonRequestSend("post",url,List(),params,needLogRsp = false).map{
      case Right(value) =>
        decode[RoomListRsp4Client](value) match {
          case Right(r) =>
            if(r.errCode == 0){
              println(s"roomData: $r")
              Right(r)
            }else{
              log.debug(s"获取列表失败，errCode:${r.errCode},msg:${r.msg}")
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

  //fixme test
//  private def updateRoomList() = {
//    Boot.addToPlatform(
//      roomListScene.updateRoomList(List("1000-0-false","1001-1-true"))
//    )
//  }

  private def updateRoomList() = {
    getRoomListInit().onComplete{
      case Success(res) =>
        res match {
          case Right(roomListRsp) =>
            Boot.addToPlatform(
              roomListScene.updateRoomList(roomListRsp.data.roomList)
            )
          case Left(e) =>
            log.error(s"获取房间列表失败，error：${e}")
        }
      case Failure(e) =>
        log.error(s"failure:${e}")
    }
  }

  roomListScene.listener = new RoomListSceneListener {
    override def confirm(roomId: Int, mode: Int, hasPwd: Boolean): Unit = {
      if(roomId.toString != null) {
//        println(s"roomMsg: $roomId-$mode-$hasPwd")
        val img = 0 //头部图像
        val frameRate = if(mode==2) frameRate2 else frameRate1
        val pwd = if(hasPwd) inputPwd else None
//        println(s"pwd: $pwd")
        if(hasPwd){
          if(pwd.nonEmpty) {
            verifyPwd(roomId, pwd.get).map{
              case true =>
                Boot.addToPlatform(
                  playGame(mode, img, frameRate, roomId)
                )
              case false =>
              //              密码错误不做任何处理
            }
          }
        } else {
          Boot.addToPlatform(
            playGame(mode, img, frameRate, roomId)
          )
        }
      }
    }

    override def gotoCreateRoomScene(): Unit = {
      Boot.addToPlatform {
        val createRoomScene = new CreateRoomScene()
        new CreateRoomController(playerInfoInClient, createRoomScene, context, domain).showScene
      }
    }

    override def reFresh(): Unit = {
      Boot.addToPlatform(
        updateRoomList()
      )
    }
  }

  def verifyPwd(roomId:Int, pwd:String) = {
    val url = s"http://$domain/carnie/verifyPwd"
    val data = PwdReq(roomId,pwd).asJson.noSpaces
    val appId = AppSetting.esheepGameId.toString
    val sn = appId + System.currentTimeMillis().toString
    val gsKey = AppSetting.esheepGsKey
    val (timestamp, nonce, signature) = generateSignatureParameters(List(appId, sn, data), gsKey)
    val params = PostEnvelope(appId, sn, timestamp, nonce, data,signature).asJson.noSpaces
    postJsonRequestSend("post",url,List(),params,needLogRsp = false).map { //data
      case Right(value) =>
        decode[SuccessRsp](value) match {
          case Right(r) =>
            if(r.errCode==0){
              true
            } else {
              log.debug("some errors in verifyPwd1.")
              false
            }
          case Left(e) =>
            log.debug(s"some errors verifyPwd2: $e")
            false
        }
      case Left(e) =>
        log.debug(s"some errors verifyPwd3: $e")
        false
    }
  }

  def playGame(mode: Int,
               img:Int,
               frameRate:Int,
               roomId:Int) = {
    val playGameScreen = new GameScene(img, frameRate)
    val LayeredGameScreen = new LayeredGameScene(img, frameRate)
    context.switchScene(playGameScreen.getScene, fullScreen = true)
    new GameController(playerInfoInClient, context, playGameScreen,LayeredGameScreen, mode, frameRate).joinByRoomId(domain, roomId, img)
  }

  def inputPwd = {
    val dialog = new TextInputDialog()
    dialog.setTitle("房间密码")
    dialog.setHeaderText("")
    dialog.setGraphic(new ImageView())
    dialog.setContentText("请输入密码:")
    val rst = dialog.showAndWait()
    var pwd: Option[String] = None
    rst.ifPresent(a => pwd = Some(a))
    pwd
  }

  def showScene: Unit = {
    Boot.addToPlatform {
      context.switchScene(roomListScene.getScene, "RoomList", false)
    }
  }
}
