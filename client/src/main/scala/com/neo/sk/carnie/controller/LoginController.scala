package com.neo.sk.carnie.controller

import java.io.ByteArrayInputStream

import akka.actor.typed.ActorRef
import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.actor.LoginSocketClient
import com.neo.sk.carnie.actor.LoginSocketClient.EstablishConnection2Es
import com.neo.sk.carnie.scene.{GameScene, LoginScene}
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.utils.Api4GameAgent._
import com.neo.sk.carnie.Boot.{executor, materializer, system}
import akka.actor.typed.scaladsl.adapter._
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import org.slf4j.LoggerFactory

/**
  * Created by dry on 2018/10/26.
  **/

class LoginController(loginScene: LoginScene, context: Context) {

  val loginSocketClient = system.spawn(LoginSocketClient.create(context, this), "loginSocketClient")

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  loginScene.setLoginSceneListener(new LoginScene.LoginSceneListener {
    override def onButtonConnect(): Unit = {
      getLoginRspFromEs().map {
        case Right(r) =>
          val wsUrl = r.wsUrl
          val scanUrl = r.scanUrl
          loginScene.drawScanUrl(imageFromBase64(scanUrl))
          loginSocketClient ! EstablishConnection2Es(wsUrl)

        case Left(_) =>
          log.debug("failed to getLoginRspFromEs.")
      }
    }
  })

  def imageFromBase64(base64Str:String): ByteArrayInputStream  = {
    if(base64Str == null) null

    import sun.misc.BASE64Decoder
    val decoder = new BASE64Decoder
    val bytes:Array[Byte]= decoder.decodeBuffer(base64Str)
    bytes.indices.foreach{ i =>
      if(bytes(i) < 0) bytes(i)=(bytes(i)+256).toByte
    }
    val  b = new ByteArrayInputStream(bytes)
    b
  }

  def showScene() {
    Boot.addToPlatform {
      context.switchScene(loginScene.scene, "Login")
    }
  }

  def switchToGaming(playerInfoInClient: PlayerInfoInClient, domain: String):Unit = {
    Boot.addToPlatform {
      val playGameScreen = new GameScene()
      context.switchScene(playGameScreen.getScene)
      new GameController(playerInfoInClient, context, playGameScreen).start(domain)
    }
  }

}
