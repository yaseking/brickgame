package com.neo.sk.carnie.scene

import javafx.geometry.{Insets, Pos}
import javafx.scene.{Group, Scene}
import javafx.scene.control.{Button, Label, PasswordField, TextField}
import javafx.scene.layout.{GridPane, HBox, VBox}

abstract class BotSceneListener {
  def confirm(botId:String,botKey:String)
}

class BotScene {
  val width = 500
  val height = 500
  val group = new Group()
  private val scene = new Scene(group, width, height)

  val confirm = new Button("确定")
  confirm.setLayoutX(230)
  confirm.setLayoutY(230)

  val userName = new TextField()
  val passWorld = new PasswordField()

//  val vBox = new VBox(30)
//  val hBox1 = new HBox(20)
//  val hBox2 = new HBox(20)
//  hBox1.getChildren.addAll(new Label("botId:"),userName)
//  hBox2.getChildren.addAll(new Label("botKey:"),passWorld)
//  vBox.getChildren.addAll(hBox1,hBox2,confirm)
//  vBox.setAlignment(Pos.CENTER)
//  vBox.setPadding(new Insets(100,50,100,160))

  val grid = new GridPane()
  grid.setHgap(10)
  grid.setVgap(10)
  grid.setPadding(new Insets(140,50,100,120))
  grid.add(new Label("botId:"), 0 ,0)
  grid.add(userName, 1 ,0)
  grid.add(new Label("botKey:"), 0 ,1)
  grid.add(passWorld, 1 ,1)
//  grid.add(confirm,1,5)
//  grid.setAlignment(Pos.BOTTOM_CENTER)

  group.getChildren.add(grid)
  group.getChildren.add(confirm)

  def getScene = this.scene

  var listener:BotSceneListener = _

  confirm.setOnAction(_ => listener.confirm(userName.getText(),passWorld.getText()))

  def setListener(listener: BotSceneListener):Unit = {
    this.listener = listener
  }
}
