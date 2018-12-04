package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.scene.{GameScene, SelectScene, SelectSceneListener}
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.paperClient.Protocol.{frameRate1, frameRate2}


class SelectController(playerInfoInClient: PlayerInfoInClient, selectScene: SelectScene, context: Context, domain: String) {

  selectScene.setListener(new SelectSceneListener{
    override def joinGame(mode: Int, img: Int): Unit = {
      println(s"mode-$mode, img-$img")
      Boot.addToPlatform {
        val frameRate = if(mode==2) frameRate2 else frameRate1
        val playGameScreen = new GameScene(img, frameRate)
        context.switchScene(playGameScreen.getScene, fullScreen = true)
        new GameController(playerInfoInClient, context, playGameScreen, mode, frameRate).start(domain, mode, img)
      }
    }
  })

  def showScene: Unit = {
    Boot.addToPlatform {
      context.switchScene(selectScene.scene, "Select", false)
    }
  }
}
