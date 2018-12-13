package com.neo.sk.carnie.scene

import javafx.scene.{Group, Scene}
import javafx.scene.control.Button

abstract class ModeSceneListener {
  def gotoNormalScene()
  def gotoBotScene()
}

class ModeScene {
  val width = 300
  val height = 300
  private val group = new Group()
  private val scene = new Scene(group, width, height)

  val normalBtn = new Button("普通模式")
  val botBtn = new Button("Bot模式")

  normalBtn.setLayoutX(80)
  normalBtn.setLayoutY(130)
  botBtn.setLayoutX(170)
  botBtn.setLayoutY(130)

  def getScene = this.scene

  var listener:ModeSceneListener = _

  group.getChildren.addAll(normalBtn,botBtn)

  normalBtn.setOnAction(_ => listener.gotoNormalScene())
  botBtn.setOnAction(_ => listener.gotoBotScene())

  def setListener(listener: ModeSceneListener):Unit = {
    this.listener = listener
  }
}
