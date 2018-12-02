package com.neo.sk.carnie.scene

import javafx.scene.canvas.Canvas
import javafx.scene.{Group, Scene}
import javafx.scene.control.{Button, RadioButton, ToggleGroup}
import javafx.scene.image.{Image, ImageView}

abstract class SelectSceneListener {
    def joinGame(mode: Int, img: Int)
}

class SelectScene {

  var selectedMode: Int = 0
  var selectedImg: Int = 0

  val width = 500
  val height = 500
  val group = new Group
  var listener: SelectSceneListener = _

  val canvas = new Canvas(width, height)
  val canvasCtx = canvas.getGraphicsContext2D
  val modeImg1 = new Image("img/Genji.png")

  val toggleGroup = new ToggleGroup()
  val mode0 = new RadioButton("test1")
  mode0.setSelected(true)
  mode0.setToggleGroup(toggleGroup)
  mode0.setUserData(0)
  val mode1 = new RadioButton()
  mode1.setToggleGroup(toggleGroup)
  mode1.setUserData(1)
  val mode2 = new RadioButton()
  mode2.setToggleGroup(toggleGroup)
  mode2.setUserData(2)

  mode0.setLayoutX(100)
  mode0.setLayoutY(170)
  mode1.setLayoutX(250)
  mode1.setLayoutY(170)
  mode2.setLayoutX(400)
  mode2.setLayoutY(170)

  val toggleGroup2 = new ToggleGroup()
  val img0 = new RadioButton("img0")
  img0.setSelected(true)
  img0.setToggleGroup(toggleGroup2)
  img0.setUserData(0)
  val img1 = new RadioButton("img1")
  img1.setToggleGroup(toggleGroup2)
  img1.setUserData(1)
  val img2 = new RadioButton("img2")
  img2.setToggleGroup(toggleGroup2)
  img2.setUserData(2)
  val img3 = new RadioButton("img3")
  img3.setToggleGroup(toggleGroup2)
  img3.setUserData(3)
  val img4 = new RadioButton("img")
  img4.setToggleGroup(toggleGroup2)
  img4.setUserData(4)
  val img5 = new RadioButton("img")
  img5.setToggleGroup(toggleGroup2)
  img5.setUserData(5)
  val img6 = new RadioButton("img")
  img6.setToggleGroup(toggleGroup2)
  img6.setUserData(6)

  img0.setLayoutX(100)
  img0.setLayoutY(350)
  img1.setLayoutX(150)
  img1.setLayoutY(350)
  img2.setLayoutX(200)
  img2.setLayoutY(350)
  img3.setLayoutX(250)
  img3.setLayoutY(350)
  img4.setLayoutX(300)
  img4.setLayoutY(350)
  img5.setLayoutX(350)
  img5.setLayoutY(350)
  img6.setLayoutX(400)
  img6.setLayoutY(350)

  val button = new Button("加入游戏")

  button.setLayoutX(250)
  button.setLayoutY(400)

  canvasCtx.drawImage(modeImg1, 40, 50, 120, 120)
  canvasCtx.drawImage(modeImg1, 190, 50, 120, 120)
  canvasCtx.drawImage(modeImg1, 340, 50, 120, 120)


  group.getChildren.add(canvas)
  group.getChildren.add(mode0)
  group.getChildren.add(mode1)
  group.getChildren.add(mode2)
  group.getChildren.add(img0)
  group.getChildren.add(img1)
  group.getChildren.add(img2)
  group.getChildren.add(img3)
  group.getChildren.add(img4)
  group.getChildren.add(img5)
  group.getChildren.add(img6)
  group.getChildren.add(button)
  val scene = new Scene(group)

  button.setOnAction(_ => listener.joinGame(selectedMode, selectedImg))

  toggleGroup.selectedToggleProperty().addListener(_ => selectMode())
  toggleGroup2.selectedToggleProperty().addListener(_ => selectImg())

  def selectMode(): Unit = {
//    val a = toggleGroup.getSelectedToggle
//    println(s"a $a")
    val rst = toggleGroup.getSelectedToggle.getUserData.toString.toInt
    println(s"rst: $rst")
    selectedMode = rst

  }

  def selectImg(): Unit ={
    val rst = toggleGroup2.getSelectedToggle.getUserData.toString.toInt
    println(s"rst2 $rst")
    selectedImg = rst
  }

  def setListener(listen: SelectSceneListener): Unit ={
    listener = listen
  }
//  def joinGame() ={
//    println(s"mode-$selectedMode")
//    println(s"img-$selectedImg")
//    (selectedMode, selectedImg)
//  }
}
