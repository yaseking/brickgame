package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.scene.{BotScene, BotSceneListener}

class BotSceneController(botScene: BotScene, context: Context) {

  botScene.setListener((botId, botKey) => Unit)

  def showScene() {
    Boot.addToPlatform {
      context.switchScene(botScene.getScene, "Bot模式", false)
    }
  }
}
