package com.neo.sk.carnie

import java.io.File

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer

import scala.language.postfixOps
import akka.dispatch.MessageDispatcher
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.controller._
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.scene.{GameScene, LoginScene, RoomListScene, SelectScene}
import com.typesafe.config.ConfigFactory
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
  implicit val scheduler = system.scheduler

  def addToPlatform(fun: => Unit) = {
    Platform.runLater(() => fun)
  }
}

class Boot extends javafx.application.Application {

  import Boot._

  override def start(mainStage: Stage): Unit = {
    val para = getParameters.getRaw
//    val para = getParameters.getRaw.get(0)

//    println("!!!!" + para)

    //是否需要图像渲染 tested
//    val fileUrl = getClass.getResource(s"/$para").toString.drop(5)
//    val file = new File(fileUrl)
//    if (file.isFile && file.exists) {
//      val botConfig = ConfigFactory.parseResources(para).withFallback(ConfigFactory.load())
//
//      val appConfig = botConfig.getConfig("app")
//      val render = appConfig.getBoolean("render")
//      if(render) {
//        val context = new Context(mainStage)
//
//        val loginScene = new LoginScene()
//        val loginController = new LoginController(loginScene, context)
//        loginController.showScene()
//        loginController.init()
//      } else {
//        val userConfig = appConfig.getConfig("user")
//        val email = userConfig.getString("email")
//        val psw = userConfig.getString("psw")
//        println(psw,email)
//        new BotController(PlayerInfoInClient("test", "test", "test"))
//      }
//    }

    val context = new Context(mainStage)

    val loginScene = new LoginScene()
    val loginController = new LoginController(loginScene, context)
    loginController.showScene()
//    loginController.init()



//    val playGameScreen = new GameScene()
//    context.switchScene(playGameScreen.getScene,fullScreen = true)
    import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
//    new GameController(PlayerInfoInClient("test", "test", "test"), context, playGameScreen, mode = 1).start("")

//    val selectScreen = new SelectScene()
//    new SelectController(PlayerInfoInClient("test", "test", "test"), selectScreen, context, "test").showScene

//    val roomListScene = new RoomListScene()
//    new RoomListController(PlayerInfoInClient("test", "test", "test"), roomListScene, context, "test").showScene
  }
}