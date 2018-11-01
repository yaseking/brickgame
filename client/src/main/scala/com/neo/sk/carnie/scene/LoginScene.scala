package com.neo.sk.carnie.scene

import com.neo.sk.carnie.Boot

import java.io.ByteArrayInputStream

import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.image.Image
import javafx.scene.paint.Color

/**
  * Created by dry on 2018/10/29.
  **/
object LoginScene {
  trait LoginSceneListener {
    def onButtonConnect()
  }
}

class LoginScene {

  import LoginScene._

  val width = 500
  val height = 500
  val group = new Group
  val button = new Button("连接")
  val canvas = new Canvas(width, height)
  val canvasCtx = canvas.getGraphicsContext2D
  var loginSceneListener: LoginSceneListener = _

  button.setLayoutX(230)
  button.setLayoutY(240)

  canvasCtx.setFill(Color.rgb(153, 255, 153))
  canvasCtx.fillRect(0, 0, width, height)
  group.getChildren.add(canvas)
  group.getChildren.add(button)
  val scene = new Scene(group)

  button.setOnAction(_ => loginSceneListener.onButtonConnect())

  def drawScanUrl(imageStream: ByteArrayInputStream) = {
    Boot.addToPlatform {
      group.getChildren.remove(button)
      val img = new Image(imageStream)
      canvasCtx.drawImage(img, 0, 0)
    }
  }

  def setLoginSceneListener(listener: LoginSceneListener) {
    loginSceneListener = listener
  }

}
