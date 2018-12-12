package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.scene._
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.paperClient.Protocol.{frameRate1, frameRate2}
import javafx.collections.FXCollections
import javafx.scene.control.ButtonBar.ButtonData
import javafx.scene.control._
import javafx.scene.layout.GridPane
//import javafx.stage.Window


class SelectController(playerInfoInClient: PlayerInfoInClient, selectScene: SelectScene, context: Context, domain: String) {

  selectScene.setListener(new SelectSceneListener{
    override def joinGame(mode: Int, img: Int): Unit = {
      Boot.addToPlatform {
        val frameRate = if(mode==2) frameRate2 else frameRate1
        val playGameScreen = new GameScene(img, frameRate)
        val LayeredGameScreen = new LayeredGameScene(img, frameRate)
        val x = false
        if(x) {
          context.switchScene(playGameScreen.getScene, fullScreen = true)
          new GameController(playerInfoInClient, context, playGameScreen,LayeredGameScreen, mode, frameRate).start(domain, mode, img)
        }
        else {
          context.switchScene(LayeredGameScreen.getScene,fullScreen = true)
          new GameController(playerInfoInClient, context, playGameScreen,LayeredGameScreen, mode, frameRate).start(domain, mode, img)
        }

      }
    }

    override def createRoom(mode: Int, img: Int, pwd: String): Unit = {
      Boot.addToPlatform {
        val frameRate = if(mode==2) frameRate2 else frameRate1
        println(s"pwd: $pwd")
//        val playGameScreen = new GameScene(img, frameRate)
//        val LayeredGameScreen = new LayeredGameScene(img, frameRate)
//        context.switchScene(playGameScreen.getScene, fullScreen = true)
//        new GameController(playerInfoInClient, context, playGameScreen, LayeredGameScreen, mode, frameRate).createRoom(domain, mode, img, pwd)
        initDialog
//        val window = new Window()
//        val window2 = new Dialog()
//        window2.show()
      }
    }

    override def gotoRoomList(): Unit = {
      Boot.addToPlatform {
        val roomListScene = new RoomListScene()
        new RoomListController(playerInfoInClient, roomListScene, context, domain).showScene
      }
    }
  })

  //todo 创建房间的弹窗demo
  def initDialog = {
    val dialog = new Dialog[(String,String,String)]()
    dialog.setTitle("test")
    val a = new ChoiceBox[String](FXCollections.observableArrayList("正常","反转","加速"))
    a.setValue("反转")
    val tF = new TextField()
    val loginButton = new ButtonType("确认", ButtonData.OK_DONE)
    val grid = new GridPane
    grid.add(a, 0 ,0)
    grid.add(tF, 0 ,1)
    dialog.getDialogPane.getButtonTypes.addAll(loginButton, ButtonType.CANCEL)
    dialog.getDialogPane.setContent(grid)
    dialog.setResultConverter(dialogButton =>
      if(dialogButton == loginButton)
        (a.getValue, "a", tF.getText)
      else
        null
    )
    val rst = dialog.showAndWait()
    rst.ifPresent(a =>
      println(s"${a._1}-${a._3}")
    )
  }

  def showScene: Unit = {
    Boot.addToPlatform {
      context.switchScene(selectScene.scene, "Select", false)
    }
  }
}
