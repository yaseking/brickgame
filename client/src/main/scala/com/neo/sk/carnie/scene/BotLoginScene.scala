//package com.neo.sk.carnie.scene
//
///**
//  * User: Jason
//  * Date: 2018/12/13
//  * Time: 16:53
//  */
//import com.neo.sk.carnie.ptcl.EsheepPtcl.BotInfo
//import javafx.collections.{FXCollections, ObservableList}
//import javafx.geometry.{Insets, Pos}
//import javafx.scene.{Group, Scene}
//import javafx.scene.control._
//import javafx.scene.image.{Image, ImageView}
//import javafx.scene.layout.{BorderPane, GridPane, HBox}
//
//abstract class BotLoginSceneListener {
//  def login(botId: String, botKey: String)
//}
//class BotLoginScene {
//  val width = 500
//  val height = 500
//
//  private val group = new Group()
//  private val scene = new Scene(group, width, height)
//  private val loginBtn = new Button("登陆")
//
//  def getScene:Scene = this.scene
//  val grid = new GridPane
//  val nameField = new TextField()
//  val pwdField = new PasswordField()
//  var listener: BotLoginSceneListener = _
//
//  grid.setHgap(10)
//  grid.setVgap(10)
//  grid.setPadding(new Insets(10,10,15,10))
//  grid.add(new Label("botId:"), 0 ,0)
//  grid.add(nameField, 1 ,0)
//  grid.add(new Label("botKey:"), 0 ,1)
//  grid.add(pwdField, 1 ,1)
//
//  loginBtn.setOnAction(_ => listener.login(nameField.getText(), pwdField.getText()))
//
//  def setListener(listen: BotLoginSceneListener): Unit ={
//    listener = listen
//  }
//}
