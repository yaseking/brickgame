package com.neo.sk.carnie.scene

import javafx.collections.{FXCollections, ObservableList, ObservableMap}
import javafx.geometry.Pos
import javafx.scene.control._
import javafx.scene.image.{Image, ImageView}
import javafx.scene.{Group, Scene}
import javafx.scene.layout.{BorderPane, HBox, Priority}

import scala.collection.mutable

abstract class RoomListSceneListener{
  def confirm(roomId: Int, mode: Int, hasPwd: Boolean)
}

class RoomListScene {
  val width = 500
  val height = 500

  private val group = new Group()
  private val scene = new Scene(group, width, height)
  private val borderPane = new BorderPane()

  val hBox = new HBox(20)

  val roomLockMap:mutable.HashMap[Int, (Int, Boolean)] = mutable.HashMap.empty[Int, (Int, Boolean)]//(roomId -> (mode, hasPwd))
  var roomList:List[String] = List.empty[String]
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

  def updateRoomList(roomList:List[String]):Unit = {
    println(s"updateRoomList: $roomList")
    this.roomList = roomList
    this.roomLockMap.clear()
    observableList.clear()
    roomList.sortBy(t => t).map { s =>
      val roomInfos = s.split("-")
      val roomId = roomInfos(0).toInt
      val modeName = if(roomInfos(1)=="0") "正常" else if(roomInfos(2)=="1") "反转" else "加速"
      val hasPwd = if(roomInfos(2)=="true") true else false
      roomLockMap += roomId -> (roomInfos(1).toInt, hasPwd)
      roomId.toString + "-" + modeName
    } foreach observableList.add
//    println(observableList)
  }

  listView.setCellFactory(_ => new ListCell[String](){//todo 找锁的图片和无锁的图片
    val img = new ImageView("img/Bob.png")
    val img1 = new ImageView("img/luffy.png")
    img.setFitWidth(15)
    img.setFitHeight(15)
    img1.setFitWidth(15)
    img1.setFitHeight(15)

    override def updateItem(item: String, empty: Boolean): Unit = {
      super.updateItem(item, empty)
      if(empty) {
        setText(null)
        setGraphic(null)
      } else {
//        println(s"item: $item")
        val roomId = item.split("-")(0).toInt
        if(roomLockMap.contains(roomId) && roomLockMap(roomId)._2){
          setGraphic(img)
        }
        else{
          setGraphic(img1)
        }
        setText(item)
      }
    }
  })

  def getScene = this.scene

  confirmBtn.setOnAction{_ =>
    val selectedInfo = listView.getSelectionModel.selectedItemProperty().get()
    val roomId = selectedInfo.split("-").head.toInt
    listener.confirm(roomId, roomLockMap(roomId)._1, roomLockMap(roomId)._2)
  }
//  confirmBtn.setOnAction(_ => println(listView.getSelectionModel.selectedItemProperty().get()))(Int, Int, Boolean)
}
