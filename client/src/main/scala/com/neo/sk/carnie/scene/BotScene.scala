package com.neo.sk.carnie.scene

import javafx.geometry.{Insets, Pos}
import javafx.scene.{Group, Scene}
import javafx.scene.control.{Button, TextField}
import javafx.scene.layout.{HBox, VBox}

abstract class BotSceneListener {
  def confirm(botId:String,botKey:String)
}

class BotScene {
  val width = 500
  val height = 500
  val group = new Group()
  private val scene = new Scene(group, width, height)

  val confirm = new Button("确定")


  val userName = new TextField()
  val passWorld = new TextField()

  val vBox = new VBox(30)
  vBox.getChildren.addAll(userName,passWorld,confirm)
  vBox.setAlignment(Pos.CENTER)
  vBox.setPadding(new Insets(100,50,100,160))

  group.getChildren.add(vBox)

  def getScene = this.scene

  var listener:BotSceneListener = _

  confirm.setOnAction(_ => listener.confirm(userName.getText(),passWorld.getText()))

  def setListener(listener: BotSceneListener):Unit = {
    this.listener = listener
  }
}
