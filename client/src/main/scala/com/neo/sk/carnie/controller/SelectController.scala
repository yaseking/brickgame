package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.scene.{GameScene, SelectScene, SelectSceneListener}
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient


class SelectController(selectScene: SelectScene, context: Context) {

  selectScene.setListener(new SelectSceneListener{
    override def joinGame(mode: Int, img: Int): Unit = {
      println(s"mode-$mode, img-$img")
    }
  })

  def showScene: Unit = {
    Boot.addToPlatform {
      context.switchScene(selectScene.scene, "Select", false)
    }
  }

  def switchToGaming(playerInfoInClient: PlayerInfoInClient, domain: String):Unit = {//fixme
    Boot.addToPlatform {
      val playGameScreen = new GameScene()
      context.switchScene(playGameScreen.getScene, fullScreen = true)
      new GameController(playerInfoInClient, context, playGameScreen).start(domain)
    }
  }
}
