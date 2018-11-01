package com.neo.sk.carnie

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer

import scala.language.postfixOps
import akka.dispatch.MessageDispatcher
import com.neo.sk.carnie.actor.LoginSocketClient
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.controller.LoginController
import com.neo.sk.carnie.scene.{GameScene, LoginScene}
import javafx.application.Platform
import javafx.stage.Stage
import akka.actor.typed.scaladsl.adapter._

/**
  * Created by dry on 2018/10/23.
  **/
object Boot {

  import com.neo.sk.carnie.common.AppSetting._

  implicit val system: ActorSystem = ActorSystem("carnie", config)
  implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val scheduler = system.scheduler

  def addToPlatform(fun: => Unit) = {
    Platform.runLater(() => fun)
  }
}

class Boot extends javafx.application.Application {

  override def start(mainStage: Stage): Unit = {
    val context = new Context(mainStage)

    val loginSocketClient = system.spawn(LoginSocketClient.create(context, system, materializer, executor), "loginSocketClient")

    val loginScene = new LoginScene()
    val loginController = new LoginController(loginSocketClient, loginScene, context)
    loginController.showScene()


    val playGameScreen = new GameScene()
    context.switchScene(playGameScreen.getScene)
    import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
    new GameController(PlayerInfoInClient("test", "test", "test"), context, playGameScreen).start()

  }
}