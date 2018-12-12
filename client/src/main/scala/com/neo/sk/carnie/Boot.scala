package com.neo.sk.carnie

import java.io.File

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer

import scala.language.postfixOps
import akka.dispatch.MessageDispatcher
import com.neo.sk.carnie.common.{AppSetting, Context}
import com.neo.sk.carnie.controller.{BotController, GameController, LoginController, SelectController}
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.scene._
import com.typesafe.config.ConfigFactory
import javafx.application.Platform
import javafx.stage.Stage

import com.neo.sk.carnie.utils.Api4GameAgent._
import org.slf4j.LoggerFactory

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
  import com.neo.sk.carnie.common.AppSetting._
  private[this] val log = LoggerFactory.getLogger(this.getClass)

  override def start(mainStage: Stage): Unit = {
    val para = getParameters.getRaw

    println("!!!!" + para)

    //是否需要图像渲染
    val file = new File(para.get(0))
    if (file.isFile && file.exists) {
      val botConfig = ConfigFactory.parseResources(para(0)).withFallback(ConfigFactory.load())

      val appConfig = botConfig.getConfig("app")
      val render = appConfig.getBoolean("render")
      val context = new Context(mainStage)
      if(render) {
        val loginScene = new LoginScene()
        val loginController = new LoginController(loginScene, context)
        loginController.showScene()
        loginController.init()
      } else {
        val botInfo = appConfig.getConfig("botInfo")
        val botId = botInfo.getString("botId")
        val botKey = botInfo.getString("botKey")
        botKey2Token(botId, botKey).map {
          case Right(data) =>
            val gameId = AppSetting.esheepGameId
            linkGameAgent(gameId, botId, data.token).map {
              case Right(rst) =>
                val layeredGameScreen = new LayeredGameScene(0, 150)
                new BotController(PlayerInfoInClient(botId, botKey, rst.accessCode), context, layeredGameScreen)
              case Left(e) =>
                log.error(s"bot link game agent error, $e")
            }

          case Left(e) =>
            log.error(s"botKey2Token error, $e")
        }
      }
    }

    val context = new Context(mainStage)

    val loginScene = new LoginScene()
    val loginController = new LoginController(loginScene, context)
    loginController.showScene()
    loginController.init()



//    val playGameScreen = new GameScene()
//    context.switchScene(playGameScreen.getScene,fullScreen = true)
    import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
//    new GameController(PlayerInfoInClient("test", "test", "test"), context, playGameScreen, mode = 1).start("")

//    val selectScreen = new SelectScene()
//    new SelectController(PlayerInfoInClient("test", "test", "test"), selectScreen, context, "test").showScene

//    val roomListScene = new RoomListScene
//    context.switchScene(roomListScene.getScene,fullScreen = false)
  }
}