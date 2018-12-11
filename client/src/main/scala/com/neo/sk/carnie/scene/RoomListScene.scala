package com.neo.sk.carnie.scene

import javafx.collections.{FXCollections, ObservableList, ObservableMap}
import javafx.geometry.Pos
import javafx.scene.control.{Button, Label, ListView}
import javafx.scene.image.{Image, ImageView}
import javafx.scene.{Group, Scene}
import javafx.scene.layout.{BorderPane, HBox, Priority}

abstract class RoomListSceneListener{
  def confirm(roomId:String)
}

class RoomListScene {
  val width = 500
  val height = 500

  private val group = new Group()
  private val scene = new Scene(group, width, height)
  private val borderPane = new BorderPane()

  val hBox = new HBox(20)

  var roomList:List[String] = List.empty[String]
  private val observableList:ObservableList[ImageView] = FXCollections.observableArrayList()
//  private val observableList:ObservableMap[String,Option[Image]] = FXCollections.observableHashMap()//observableArrayList()
//  private val listView = new ListView[(String,Option[Image])](observableList)
  private val listView = new ListView[ImageView](observableList)
  private val confirmBtn = new Button("进入房间")

  var listener: RoomListSceneListener = _

  val test = new Label("")
  test.textProperty()

//  confirmBtn.setPrefSize(100,20)
  hBox.getChildren.addAll(listView, confirmBtn)
  hBox.setAlignment(Pos.CENTER)
//  HBox.setHgrow(listView,Priority.ALWAYS)
  //  borderPane.prefHeightProperty().bind(scene.heightProperty())
  borderPane.prefWidthProperty().bind(scene.widthProperty())
  borderPane.setCenter(hBox)
  group.getChildren.add(borderPane)

  //fixme 图片大小调整
  def updateRoomList(roomList:List[String]):Unit = {
    this.roomList = roomList
    observableList.clear()
//    val img = new ImageView("img/Bob.png")
    val img = new ImageView("")
    img.setX(1)
    img.setY(1)
    img.setFitWidth(10)
    img.setFitHeight(10)
    roomList.sortBy(t => t).map { s =>
      //      (s.replace("0","正常").replace("1","反转").replace("2","加速")
      //        ,img)
      println(s"x-${img.getX},y-${img.getY}")
      null
    } foreach observableList.add
  }

  def getScene = this.scene

  confirmBtn.setOnAction(_ => listener.confirm(listView.getSelectionModel.selectedItemProperty().get().toString))
}
