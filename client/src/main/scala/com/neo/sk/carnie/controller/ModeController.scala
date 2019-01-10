package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.scene.{BotScene, LoginScene, ModeScene, ModeSceneListener}

class ModeController(modeScene: ModeScene, context: Context) {

  modeScene.setListener(new ModeSceneListener {
    override def gotoNormalScene(): Unit = {
      Boot.addToPlatform{
        val loginScene = new LoginScene
        val loginController = new LoginController(modeScene, loginScene, context)
        loginController.init()
        loginController.showScene()
      }
    }

    override def gotoBotScene(): Unit = {
      Boot.addToPlatform{
        val botScene = new BotScene
        val botController = new BotSceneController(modeScene,botScene,context)
        botController.showScene()
      }
    }
  })

  def showScene() {
    Boot.addToPlatform {
      context.switchScene(modeScene.getScene, "模式选择", false)
    }
  }
}
