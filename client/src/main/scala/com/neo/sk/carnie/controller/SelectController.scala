package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.scene.{GameScene, SelectScene}
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient


class SelectController(selectScene: SelectScene, context: Context) {

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
