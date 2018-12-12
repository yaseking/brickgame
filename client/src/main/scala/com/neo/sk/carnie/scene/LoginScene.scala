package com.neo.sk.carnie.scene

import com.neo.sk.carnie.Boot
import java.io.ByteArrayInputStream

import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.image.Image
import javafx.scene.paint.Color
import javafx.scene.text.Font

/**
  * Created by dry on 2018/10/29.
  **/
object LoginScene {
  trait LoginSceneListener {
    def loginByMail()
  }
}

class LoginScene {
  import LoginScene._

  val width = 500
  val height = 500
  val group = new Group
  val button = new Button("邮箱登录")
  val canvas = new Canvas(width, height)
  val canvasCtx = canvas.getGraphicsContext2D
  var loginSceneListener: LoginSceneListener = _

  button.setLayoutX(380)
  button.setLayoutY(400)

  canvasCtx.setFill(Color.rgb(255, 255, 255))
  canvasCtx.fillRect(0, 0, width, height)
  canvasCtx.setFont(Font.font(18))
  canvasCtx.setFill(Color.BLACK)
  canvasCtx.fillText("扫码登录", 220, 380)
  group.getChildren.add(canvas)
  group.getChildren.add(button)
  val scene = new Scene(group)

  button.setOnAction(_ => loginSceneListener.loginByMail())

  def drawScanUrl(imageStream: ByteArrayInputStream) = {
    Boot.addToPlatform {
//      group.getChildren.remove(button)
      val img = new Image(imageStream)
      canvasCtx.drawImage(img,100,40)
    }
  }

  def setLoginSceneListener(listener: LoginSceneListener) {
    loginSceneListener = listener
  }

}
