package com.neo.sk.carnie.scene

import javafx.collections.{FXCollections, ObservableList}
import javafx.geometry.Pos
import javafx.scene.control.{Button, ListView}
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

  var roomList:List[Int] = List.empty[Int]
  private val observableList:ObservableList[String] = FXCollections.observableArrayList()
  private val listView = new ListView[String](observableList)
  private val confirmBtn = new Button("进入房间")

  var listener: RoomListSceneListener = _

//  confirmBtn.setPrefSize(100,20)
  hBox.getChildren.addAll(listView, confirmBtn)
  hBox.setAlignment(Pos.CENTER)
//  HBox.setHgrow(listView,Priority.ALWAYS)
  //  borderPane.prefHeightProperty().bind(scene.heightProperty())
  borderPane.prefWidthProperty().bind(scene.widthProperty())
  borderPane.setCenter(hBox)
  group.getChildren.add(borderPane)

  def updateRoomList(roomList:List[Int]):Unit = {
    this.roomList = roomList
    observableList.clear()
    roomList.sortBy(t => t).map(_.toString) foreach observableList.add
  }

  def getScene = this.scene

  confirmBtn.setOnAction(_ => listener.confirm(listView.getSelectionModel.selectedItemProperty().get()))
}
