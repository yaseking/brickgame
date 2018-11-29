package com.neo.sk.carnie.scene

import javafx.scene.{Group, Scene}
import javafx.scene.control.Button

object SelectScene {
  trait SelectSceneListener {
    def joinGame()
  }
}

class SelectScene {
  import SelectScene._

  val width = 500
  val height = 500
  val group = new Group
  val button = new Button("加入游戏")
  var selectSceneListener: SelectSceneListener = _

  button.setLayoutX(230)
  button.setLayoutY(240)

  group.getChildren.add(button)
  val scene = new Scene(group)

  button.setOnAction(_ => selectSceneListener.joinGame())

  def setSelectSceneListener(listen: SelectSceneListener) = {
    selectSceneListener = listen
  }
}
