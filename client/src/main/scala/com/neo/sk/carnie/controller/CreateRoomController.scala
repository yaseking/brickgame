package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.Boot.executor
import com.neo.sk.carnie.common.{AppSetting, Context}
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.paperClient.Protocol.{frameRate1, frameRate2}
import com.neo.sk.carnie.scene._
import com.neo.sk.carnie.utils.Api4GameAgent.linkGameAgent
import org.slf4j.LoggerFactory

class CreateRoomController(playerInfoInClient: PlayerInfoInClient, createRoomScene: CreateRoomScene, context: Context) {
  private val log = LoggerFactory.getLogger(this.getClass)
//  private val domain = AppSetting.esheepDomain

  createRoomScene.setListener(new CreateRoomSceneListener {
    override def createRoom(mode: Int, img: Int, pwd: String): Unit = {
      Boot.addToPlatform {
        val frameRate = if(mode==2) frameRate2 else frameRate1
//        println(s"pwd: $pwd")
        val gameId = AppSetting.esheepGameId
        linkGameAgent(gameId, playerInfoInClient.id, playerInfoInClient.msg).map {
          case Right(r) =>
            val playGameScreen = new GameScene(img, frameRate)
            Boot.addToPlatform{
              context.switchScene(playGameScreen.getScene, fullScreen = true)
              new GameController(playerInfoInClient.copy(msg=r.accessCode), context, playGameScreen, mode, frameRate).start(r.gsPrimaryInfo.domain, mode, img)
            }

          case Left(e) =>
            log.debug(s"linkGameAgent..$e")
        }
      }
    }
  })

  def showScene: Unit = {
    Boot.addToPlatform {
      context.switchScene(createRoomScene.getScene, "创建房间", false)
    }
  }
}
