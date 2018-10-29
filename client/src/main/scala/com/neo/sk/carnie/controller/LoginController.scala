package com.neo.sk.carnie.controller

import akka.actor.typed.ActorRef
import com.neo.sk.carnie.actor.WebSocketClient
import com.neo.sk.carnie.scene.LoginScene
import com.neo.sk.carnie.common.Context

/**
  * Created by dry on 2018/10/26.
  **/
class LoginController(wsClient:  ActorRef[WebSocketClient.WsCommand], loginScene: LoginScene, context: Context) {

}
