package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.scene.{GameScene, SelectScene, SelectSceneListener}
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.paperClient.Protocol.{frameRate1, frameRate2}
import javafx.collections.FXCollections
import javafx.scene.control.ButtonBar.ButtonData
import javafx.scene.control._
import javafx.scene.layout.GridPane
import javafx.stage.Window


class SelectController(playerInfoInClient: PlayerInfoInClient, selectScene: SelectScene, context: Context, domain: String) {

  selectScene.setListener(new SelectSceneListener{
    override def joinGame(mode: Int, img: Int): Unit = {
      Boot.addToPlatform {
        val frameRate = if(mode==2) frameRate2 else frameRate1
        val playGameScreen = new GameScene(img, frameRate)
        context.switchScene(playGameScreen.getScene, fullScreen = true)
        new GameController(playerInfoInClient, context, playGameScreen, mode, frameRate).start(domain, mode, img)
      }
    }

    override def createRoom(mode: Int, img: Int, pwd: String): Unit = {
      Boot.addToPlatform {
        val frameRate = if(mode==2) frameRate2 else frameRate1
        println(s"pwd: $pwd")
//        val playGameScreen = new GameScene(img, frameRate)
//        context.switchScene(playGameScreen.getScene, fullScreen = true)
//        new GameController(playerInfoInClient, context, playGameScreen, mode, frameRate).createRoom(domain, mode, img, pwd)
//        val window = new Window()
        val window2 = new Dialog()
        window2.show()
      }
    }
  })


  //todo 创建房间的弹窗demo
  def initDialog = {
    val dialog = new Dialog[(String,String,String)]()
    dialog.setTitle("test")
    val a = new ChoiceBox[String](FXCollections.observableArrayList("正常","反转","加速"))
    val b = a.getValue
    val tF = new TextField()
    val grid = new GridPane
    grid.add(tF, 0 ,0)
    dialog.getDialogPane.setContent(grid)
    val loginButton = new ButtonType("Login", ButtonData.OK_DONE)
    dialog.setResultConverter(dialogButton =>
      ("a", "b", "c")
    )
    val rst = dialog.showAndWait()
  }

  def showScene: Unit = {
    Boot.addToPlatform {
      context.switchScene(selectScene.scene, "Select", false)
    }
  }
}
