package com.neo.sk.carnie.common

import javafx.scene.Scene
import javafx.stage.Stage

/**
  * Created by dry on 2018/10/29.
  **/
class Context(stage: Stage) {

  def switchScene(scene: Scene, title:String = "carnie", fullScreen: Boolean) = {
    stage.setScene(scene)
    stage.sizeToScene()
    stage.setResizable(false)
    stage.setTitle(title)
    stage.setFullScreen(fullScreen)
    stage.show()
  }

}
