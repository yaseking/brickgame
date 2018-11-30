package com.neo.sk.carnie.scene

import javafx.scene.canvas.Canvas
import javafx.scene.{Group, Scene}
import javafx.scene.control.{Button, RadioButton}
import javafx.scene.image.{Image, ImageView}

object SelectScene {
  trait SelectSceneListener {
    def joinGame()
  }
}

class SelectScene {
  import SelectScene._

  val width = 800
  val height = 800
  val group = new Group
  val canvas = new Canvas(width, height)
  val canvasCtx = canvas.getGraphicsContext2D
  val modeImg1 = new Image("img/Genji.png")
  val mode1 = new RadioButton("test1")
  val button = new Button("加入游戏")
  var selectSceneListener: SelectSceneListener = _

  button.setLayoutX(330)
  button.setLayoutY(340)

  canvasCtx.drawImage(modeImg1, 15, 15, 100, 100)
  mode1.setLayoutX(63)
  mode1.setLayoutY(110)

  group.getChildren.add(canvas)
  group.getChildren.add(mode1)
  group.getChildren.add(button)
  val scene = new Scene(group)

  button.setOnAction(_ => selectSceneListener.joinGame())

  def setSelectSceneListener(listen: SelectSceneListener) = {
    selectSceneListener = listen
  }
}
