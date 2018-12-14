package com.neo.sk.carnie

import java.io.File

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer

import scala.language.postfixOps
import akka.dispatch.MessageDispatcher
import akka.util.Timeout
import com.neo.sk.carnie.common.{AppSetting, Context}
import com.neo.sk.carnie.controller._
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.scene._
import com.typesafe.config.ConfigFactory
import javafx.application.Platform
import javafx.stage.Stage
import com.neo.sk.carnie.utils.Api4GameAgent._
import org.slf4j.LoggerFactory

import concurrent.duration._
import scala.language.postfixOps

/**
  * Created by dry on 2018/10/23.
  **/
object Boot {
  import com.neo.sk.carnie.common.AppSetting._

  implicit val system: ActorSystem = ActorSystem("carnie", config)
  implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val scheduler = system.scheduler
  implicit val timeout: Timeout = Timeout(20 seconds)

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

//    是否需要图像渲染
//    if(!para.isEmpty){
//      val file = new File(para.get(0))
//      if (file.isFile && file.exists) {
//        val botConfig = ConfigFactory.parseResources(para(0)).withFallback(ConfigFactory.load())
//
//        val appConfig = botConfig.getConfig("app")
//        val render = appConfig.getBoolean("render")
//        val context = new Context(mainStage)
//        if(render) {
//          val modeScene = new ModeScene()
//          val modeController = new ModeController(modeScene,context)
//          modeController.showScene()
//        } else {
//          val botInfo = appConfig.getConfig("botInfo")
//          val botId = botInfo.getString("botId")
//          val botKey = botInfo.getString("botKey")
//          botKey2Token(botId, botKey).map {
//            case Right(data) =>
//              val gameId = AppSetting.esheepGameId
//              linkGameAgent(gameId, botId, data.token).map {
//                case Right(rst) =>
//                  val layeredGameScreen = new LayeredGameScene(0, 150)
//                  new BotController(PlayerInfoInClient(botId, data.botName, botKey, rst.accessCode), context, layeredGameScreen)
//                case Left(e) =>
//                  log.error(s"bot link game agent error, $e")
//              }
//
//            case Left(e) =>
//              log.error(s"botKey2Token error, $e")
//          }
//        }
//      }
//    } else {
//      log.debug("未输入参数.")
//    }
    //test
    val context = new Context(mainStage)
//    val layeredGameScreen = new LayeredGameScene(0, 150)
//    new BotController(PlayerInfoInClient("123", "abc", "test"), context, layeredGameScreen)

//    val context = new Context(mainStage)

    val modeScene = new ModeScene()
    new ModeController(modeScene,context).showScene()

//    val loginScene = new LoginScene()
//    new LoginController(loginScene,context).showScene()

//    val botScene = new BotScene()
//    new BotSceneController(botScene,context).showScene()


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