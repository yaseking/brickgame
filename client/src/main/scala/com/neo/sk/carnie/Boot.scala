package com.neo.sk.carnie

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import akka.actor.typed.scaladsl.adapter._

import scala.language.postfixOps
import akka.dispatch.MessageDispatcher
import com.neo.sk.carnie.actor.{GameMessageReceiver, WebSocketClient}
import com.neo.sk.carnie.common.Context
import javafx.application.Platform
import javafx.stage.Stage

/**
  * Created by dry on 2018/10/23.
  **/
object Boot {

  import com.neo.sk.carnie.common.AppSetting._

  implicit val system: ActorSystem = ActorSystem("carnie", config)
  implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val scheduler: Scheduler = system.scheduler
  val gameMessageReceiver = system.spawn(GameMessageReceiver.create(), "gameMessageReceiver")

  def addToPlatform(fun: => Unit) = {
    Platform.runLater(() => fun)
  }
}

class Boot extends javafx.application.Application {

  import Boot._

  override def start(mainStage: Stage): Unit = {
    val context = new Context(mainStage)
    val wsClient = system.spawn(WebSocketClient.create(gameMessageReceiver, context, system, materializer, executor), "wsClient")
    //    val loginScene = new LoginScene()
    //    val loginController = new LoginController(wsClient, loginScene, context)
    //    loginController.showScene()


    //		val gameViewScene = new GameScene()
    //		mainStage.setMaximized(true)
    //		context.switchScene(gameViewScene.GameViewScene,"Medusa")


  }
}