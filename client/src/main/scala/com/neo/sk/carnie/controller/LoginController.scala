package com.neo.sk.carnie.controller

import java.io.ByteArrayInputStream
import akka.actor.typed.ActorRef
import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.actor.LoginSocketClient
import com.neo.sk.carnie.actor.LoginSocketClient.EstablishConnection2Es
import com.neo.sk.carnie.scene.LoginScene
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.controller.Api4GameAgent._
import com.neo.sk.carnie.Boot.executor

/**
  * Created by dry on 2018/10/26.
  **/

class LoginController(wsClient:  ActorRef[LoginSocketClient.WsCommand], loginScene: LoginScene, context: Context) {

  loginScene.setLoginSceneListener(new LoginScene.LoginSceneListener {
    override def onButtonConnect(): Unit = {
//      val id = System.currentTimeMillis().toString
//      val name = "name" + System.currentTimeMillis().toString
//      val accessCode = "jgfkldpwer"
      getLoginRspFromEs().map {
        case Right(r) =>
          val wsUrl = r.wsUrl
          val scanUrl = r.scanUrl
          loginScene.drawScanUrl(imageFromBase64(scanUrl))
          wsClient ! EstablishConnection2Es(wsUrl)
        case Left(_) =>
          //不做处理
      }
//      wsClient ! ConnectGame(id, name, accessCode)
    }
  })

  def imageFromBase64(base64Str:String)  = {
    if(base64Str == null) null

    import sun.misc.BASE64Decoder
    val decoder = new BASE64Decoder
    val bytes:Array[Byte]= decoder.decodeBuffer(base64Str)
    for(i <- 0 until bytes.length){
      if(bytes(i) < 0) bytes(i)=(bytes(i).+(256)).toByte
    }
    val  b = new ByteArrayInputStream(bytes)
    b
  }

  def showScene() {
    Boot.addToPlatform {
      context.switchScene(loginScene.scene, "Login")
    }
  }

}
