package com.neo.sk.carnie.controller

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.common.{AppSetting, Context}
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.scene.{BotScene, BotSceneListener, LayeredGameScene, ModeScene}
import com.neo.sk.carnie.utils.Api4GameAgent.{botKey2Token, linkGameAgent}
import com.neo.sk.carnie.Boot.executor
import org.slf4j.LoggerFactory

class BotSceneController(modeScene: ModeScene,botScene: BotScene, context: Context) {
  private[this] val log = LoggerFactory.getLogger(this.getClass)

  botScene.setListener(new BotSceneListener {
    override def confirm(botId: String, botKey: String): Unit = {
      botKey2Token(botId, botKey).map {
        case Right(data) =>
          val gameId = AppSetting.esheepGameId
          linkGameAgent(gameId, "bot" + botId, data.token).map {
            //todo 连接webSocket
            case Right(rst) =>
              Boot.addToPlatform {
                val layeredGameScreen = new LayeredGameScene(0, 150)
                context.switchScene(layeredGameScreen.getScene, "layered", true)
                layeredGameScreen.drawGameWait(context.getStage.getWidth.toInt, context.getStage.getHeight.toInt)
                new BotController(PlayerInfoInClient(botId, botKey, rst.accessCode), context, layeredGameScreen)
              }
            case Left(e) =>
              log.error(s"bot link game agent error, $e")
          }

        case Left(e) =>
          log.error(s"botKey2Token error, $e")
      }
    }

    override def comeBack(): Unit = {
      Boot.addToPlatform(
        context.switchScene(modeScene.getScene, "模式选择", false)
      )
    }
  })

  def showScene() {
    Boot.addToPlatform {
      context.switchScene(botScene.getScene, "Bot模式", false)
    }
  }
}
