package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.paperClient.Protocol.{frameRate1, frameRate2}
import com.neo.sk.carnie.scene._

class CreateRoomController(playerInfoInClient: PlayerInfoInClient, createRoomScene: CreateRoomScene, context: Context, domain: String) {

  createRoomScene.setListener(new CreateRoomSceneListener {
    override def createRoom(mode: Int, img: Int, pwd: String): Unit = {
      Boot.addToPlatform {
        val frameRate = if(mode==2) frameRate2 else frameRate1
        println(s"pwd: $pwd")
        val playGameScreen = new GameScene(img, frameRate)
//        val LayeredGameScreen = new LayeredGameScene(img, frameRate)
        context.switchScene(playGameScreen.getScene, fullScreen = true)
        new GameController(playerInfoInClient, context, playGameScreen, mode, frameRate).createRoom(domain, mode, img, pwd)
      }
    }
  })

  def showScene: Unit = {
    Boot.addToPlatform {
      context.switchScene(createRoomScene.getScene, "创建房间", false)
    }
  }
}
