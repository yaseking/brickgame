package com.neo.sk.carnie.controller

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.ActorRef
import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.Boot.gameMessageReceiver
import com.neo.sk.carnie.actor.GameMessageReceiver.GridInitial
import com.neo.sk.carnie.common.{Constant, Context}
import com.neo.sk.carnie.paperClient.Protocol.{NeedToSync, UserAction}
import com.neo.sk.carnie.paperClient.{Boundary, Point, Protocol}
import com.neo.sk.carnie.scene.GameScene
import javafx.animation.{Animation, AnimationTimer, KeyFrame, Timeline}
import javafx.scene.input.KeyCode
import javafx.util.Duration

/**
  * Created by dry on 2018/10/29.
  **/
class GameController(id: String,
                     name: String,
                     accessCode: String,
                     stageCtx: Context,
                     gameScene: GameScene,
                     serverActor: ActorRef[Protocol.WsSendMsg]) {

  val bounds = Point(Boundary.w, Boundary.h)
  val grid = new GridOnClient(bounds)
  var basicTime = 0l
  var firstCome = false
  val idGenerator = new AtomicInteger(1)


  def connectToGameServer: Unit = {
    Boot.addToPlatform {
      stageCtx.switchScene(gameScene.scene, "Gaming")
      gameMessageReceiver ! GridInitial(grid)
      startGameLoop()
    }
  }

  def startGameLoop(): Unit = { //渲染帧
    val animationTimer = new AnimationTimer() {
      override def handle(now: Long): Unit = {
        gameScene.draw(grid.myId, grid.getGridData, grid.currentRank)
      }
    }
    val timeline = new Timeline()
    timeline.setCycleCount(Animation.INDEFINITE)
    val keyFrame = new KeyFrame(Duration.millis( Protocol.frameRate), { _ =>
      logicLoop()
    })

    timeline.getKeyFrames.add(keyFrame)
    animationTimer.start()
    timeline.play()
  }

  private def logicLoop(): Unit = { //逻辑帧
    basicTime = System.currentTimeMillis()
    if (!grid.justSynced) {
      grid.update("f")
      if (grid.newFieldInfo.nonEmpty && grid.newFieldInfo.get.frameCount <= grid.frameCount) { //圈地信息
        if (grid.newFieldInfo.get.frameCount == grid.frameCount) {
          grid.addNewFieldInfo(grid.newFieldInfo.get)
        } else { //主动要求同步数据
          serverActor ! NeedToSync(grid.myId).asInstanceOf[UserAction]
        }
        grid.newFieldInfo = None
      }
    } else if(grid.syncGridData.nonEmpty) {
      grid.initSyncGridData(grid.syncGridData.get)
      grid.syncGridData = None
      grid.justSynced = false
    }
  }

  gameScene.setGameSceneListener(new GameScene.GameSceneListener {
    override def onKeyPressed(key: KeyCode): Unit = {
      if (Constant.watchKeys.contains(key)) {
        val frame = grid.frameCount + 2
        val actionId = idGenerator.getAndIncrement()
        val keyCode = Constant.keyCode2Int(key)
        grid.addActionWithFrame(grid.myId, keyCode, frame)

        if (key != KeyCode.SPACE) {
          grid.myActionHistory += actionId -> (keyCode, frame)
        } else {
          //数据重置
        }
        serverActor ! Protocol.Key(grid.myId, Constant.keyCode2Int(key), frame, actionId)
      }
    }
  })


}
