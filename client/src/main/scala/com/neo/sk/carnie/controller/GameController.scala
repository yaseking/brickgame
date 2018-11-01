package com.neo.sk.carnie.controller

import java.util.concurrent.atomic.AtomicInteger

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.common.{Constant, Context}
import com.neo.sk.carnie.paperClient.Protocol.{NeedToSync, UserAction}
import com.neo.sk.carnie.paperClient.{Boundary, Point, Protocol, WsSourceProtocol}
import com.neo.sk.carnie.scene.GameScene
import javafx.animation.{Animation, AnimationTimer, KeyFrame, Timeline}
import javafx.scene.input.KeyCode
import javafx.util.Duration
import akka.actor.typed.scaladsl.adapter._
import org.slf4j.LoggerFactory
import com.neo.sk.carnie.actor.PlayGameWebSocket
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient

/**
  * Created by dry on 2018/10/29.
  **/

class GameController(player: PlayerInfoInClient,
                     stageCtx: Context,
                     gameScene: GameScene) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  private val playActor = Boot.system.spawn(PlayGameWebSocket.create(this), "playActor")

  val bounds = Point(Boundary.w, Boundary.h)
  var grid = new GridOnClient(bounds)
  var firstCome = true
  val idGenerator = new AtomicInteger(1)

  var justSynced = false
  var newFieldInfo: scala.Option[Protocol.NewFieldInfo] = None
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None

  private val timeline = new Timeline()
  private var logicFrameTime = System.currentTimeMillis()
  private val animationTimer = new AnimationTimer() {
    override def handle(now: Long): Unit = {
      draw(System.currentTimeMillis() - logicFrameTime)
    }
  }

  def loseConnect(): Unit = {
    gameScene.drawGameOff(firstCome)
    animationTimer.stop()
  }

  def start(): Unit = {
    playActor ! PlayGameWebSocket.ConnectGame(player, "")
    addUserActionListen()
    startGameLoop()
  }

  def startGameLoop(): Unit = { //渲染帧
    logicFrameTime = System.currentTimeMillis()
    timeline.setCycleCount(Animation.INDEFINITE)
    val keyFrame = new KeyFrame(Duration.millis(Protocol.frameRate), { _ =>
      logicLoop()
    })
    timeline.getKeyFrames.add(keyFrame)
    animationTimer.start()
    timeline.play()
  }

  private def logicLoop(): Unit = { //逻辑帧
    logicFrameTime = System.currentTimeMillis()
    if (!justSynced) {
      grid.update("f")
      if (newFieldInfo.nonEmpty && newFieldInfo.get.frameCount <= grid.frameCount) { //圈地信息
        if (newFieldInfo.get.frameCount == grid.frameCount) {
          grid.addNewFieldInfo(newFieldInfo.get)
        } else { //主动要求同步数据
          playActor ! PlayGameWebSocket.MsgToService(NeedToSync(player.id).asInstanceOf[UserAction])
        }
        newFieldInfo = None
      }
    } else if(syncGridData.nonEmpty) {
      grid.initSyncGridData(syncGridData.get)
      syncGridData = None
      justSynced = false
    }
  }

  def draw(offsetTime: Long): Unit = {
    val data = grid.getGridData
    data.snakes.find(_.id == player.id) match {
      case Some(_) =>
        firstCome = false
        gameScene.draw(player.id, data, offsetTime, grid, grid.currentRank.headOption.map(_.id).getOrElse(player.id))

      case None =>
        if (firstCome) gameScene.drawGameWait()
        else gameScene.drawGameDie(grid.getKiller(player.id).map(_._2))
    }
  }

  def gameMessageReceiver(msg: WsSourceProtocol.WsMsgSource): Unit = {
    msg match {
      case Protocol.Id(id) =>
        log.debug(s"i receive my id:$id")

      case Protocol.SnakeAction(id, keyCode, frame, actionId) =>
        Boot.addToPlatform {
          if (grid.snakes.exists(_._1 == id)) {
            if (id == player.id) { //收到自己的进行校验是否与预判一致，若不一致则回溯
              if (grid.myActionHistory.get(actionId).isEmpty) { //前端没有该项，则加入
                grid.addActionWithFrame(id, keyCode, frame)
                if (frame < grid.frameCount && grid.frameCount - frame <= (grid.maxDelayed - 1)) { //回溯
                  val oldGrid = grid
                  oldGrid.recallGrid(frame, grid.frameCount)
                  grid = oldGrid
                }
              } else {
                if (grid.myActionHistory(actionId)._1 != keyCode || grid.myActionHistory(actionId)._2 != frame) { //若keyCode或则frame不一致则进行回溯
                  grid.deleteActionWithFrame(id, grid.myActionHistory(actionId)._2)
                  grid.addActionWithFrame(id, keyCode, frame)
                  val miniFrame = Math.min(frame, grid.myActionHistory(actionId)._2)
                  if (miniFrame < grid.frameCount && grid.frameCount - miniFrame <= (grid.maxDelayed - 1)) { //回溯
                    val oldGrid = grid
                    oldGrid.recallGrid(miniFrame, grid.frameCount)
                    grid = oldGrid
                  }
                }
                grid.myActionHistory -= actionId
              }
            } else { //收到别人的动作则加入action，若帧号滞后则进行回溯
              grid.addActionWithFrame(id, keyCode, frame)
              if (frame < grid.frameCount && grid.frameCount - frame <= (grid.maxDelayed - 1)) { //回溯
                val oldGrid = grid
                oldGrid.recallGrid(frame, grid.frameCount)
                grid = oldGrid
              }
            }
          }
        }

      case Protocol.ReStartGame =>
        Boot.addToPlatform {
          firstCome = true
        }

      case Protocol.SomeOneWin(winner, finalData) =>
        Boot.addToPlatform {
          gameScene.drawGameWin(player.id, winner, finalData)
          grid.cleanData()
          animationTimer.stop()
        }

      case Protocol.Ranks(current) =>
        Boot.addToPlatform {
          grid.currentRank = current
          if (grid.getGridData.snakes.exists(_.id == player.id))
            gameScene.drawRank(player.id, grid.getGridData.snakes, current)
        }

      case data: Protocol.Data4TotalSync =>
        Boot.addToPlatform{
          syncGridData = Some(data)
          justSynced = true
        }

      case Protocol.SomeOneKilled(killedId, killedName, killerName) =>
        Boot.addToPlatform {
          grid.killInfo = (killedId, killedName, killerName)
          grid.lastTime = 100
        }

      case data: Protocol.NewFieldInfo =>
        Boot.addToPlatform{
          newFieldInfo = Some(data)
        }

      case x@Protocol.ReceivePingPacket(_) =>
        //          PerformanceTool.receivePingPackage(x)

      case unknown@_ =>
        log.debug(s"i receive an unknown msg:$unknown")
    }
  }

  def addUserActionListen() = {
    gameScene.viewCanvas.requestFocus()

    gameScene.viewCanvas.setOnKeyPressed{ event =>
      val key = event.getCode
      if (Constant.watchKeys.contains(key)) {
        val frame = grid.frameCount + 2
        val actionId = idGenerator.getAndIncrement()
        val keyCode = Constant.keyCode2Int(key)
        grid.addActionWithFrame(player.id, keyCode, frame)

        if (key != KeyCode.SPACE) {
          grid.myActionHistory += actionId -> (keyCode, frame)
        } else {
          //数据重置
        }
        playActor ! PlayGameWebSocket.MsgToService(Protocol.Key(player.id, Constant.keyCode2Int(key), frame, actionId))
      }
    }

  }

}
