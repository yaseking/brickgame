package com.neo.sk.carnie.controller

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.common.{Constant, Context}
import com.neo.sk.carnie.paperClient.Protocol.{NeedToSync, UserAction}
import com.neo.sk.carnie.paperClient._
import com.neo.sk.carnie.scene.GameScene
import javafx.animation.{Animation, AnimationTimer, KeyFrame, Timeline}
import javafx.scene.input.KeyCode
import javafx.util.Duration
import akka.actor.typed.scaladsl.adapter._
import org.slf4j.LoggerFactory
import com.neo.sk.carnie.actor.PlayGameWebSocket
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import javafx.scene.media.{AudioClip, Media, MediaPlayer}

/**
  * Created by dry on 2018/10/29.
  **/

class GameController(player: PlayerInfoInClient,
                     stageCtx: Context,
                     gameScene: GameScene) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  private val playActor = Boot.system.spawn(PlayGameWebSocket.create(this), "playActor")

  var currentRank = List.empty[Score]
  val bounds = Point(Boundary.w, Boundary.h)
  var grid = new GridOnClient(bounds)
  var firstCome = true
  val idGenerator = new AtomicInteger(1)
  var scoreFlag = true
  var timeFlag = true
  var isWin = false
  var exitFullScreen = false
  var winnerName = "unknown"
  var isContinues = true
  var justSynced = false
  var winnerData : Option[Protocol.SomeOneWin] = None
  private var fieldNum = 0
  val audioFinish = new AudioClip(getClass.getResource("/mp3/finish.mp3").toString)
  val audioKill = new AudioClip(getClass.getResource("/mp3/kill.mp3").toString)
  val audioWin = new AudioClip(getClass.getResource("/mp3/win.mp3").toString)
  val audioDie = new AudioClip(getClass.getResource("/mp3/killed.mp3").toString)
  var newFieldInfo: scala.Option[Protocol.NewFieldInfo] = None
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None
  private var stageWidth = stageCtx.getStage.getWidth.toInt
  private var stageHeight = stageCtx.getStage.getHeight.toInt
  private var isContinue = true
  private var myScore = BaseScore(0, 0, 0l, 0l)
  private val timeline = new Timeline()
  private var logicFrameTime = System.currentTimeMillis()
  private val animationTimer = new AnimationTimer() {
    override def handle(now: Long): Unit = {
      draw(System.currentTimeMillis() - logicFrameTime)
    }
  }

  def loseConnect(): Unit = {
    gameScene.drawGameOff(firstCome)
//    animationTimer.stop()
  }

  def start(domain: String): Unit = {
    playActor ! PlayGameWebSocket.ConnectGame(player, domain)
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
    if(!stageCtx.getStage.isFullScreen && !exitFullScreen) {
      gameScene.resetScreen(1200,750,1200,250)
      stageCtx.getStage.setWidth(1200)
      stageCtx.getStage.setHeight(750)
      exitFullScreen = true
    }
    if(stageWidth != stageCtx.getStage.getWidth.toInt || stageHeight != stageCtx.getStage.getHeight.toInt){
      stageWidth = stageCtx.getStage.getWidth.toInt
      stageHeight = stageCtx.getStage.getHeight.toInt
      gameScene.resetScreen(stageWidth,stageHeight,stageWidth,stageHeight)
      stageCtx.getStage.setWidth(stageWidth)
      stageCtx.getStage.setHeight(stageHeight)
    }
    logicFrameTime = System.currentTimeMillis()
    playActor ! PlayGameWebSocket.MsgToService(Protocol.SendPingPacket(player.id, System.currentTimeMillis()))
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
      case Some(snake) =>
        firstCome = false
        if (scoreFlag) {
          myScore = BaseScore(0, 0, System.currentTimeMillis(), 0l)
          scoreFlag = false
        }
        data.killHistory.foreach {
          i => if (i.frameCount + 1 == data.frameCount && i.killerId == player.id) audioKill.play()
        }
        val myFieldCount = grid.getMyFieldCount(player.id, bounds, Point(0, 0))
        if(myFieldCount>fieldNum){
          audioFinish.play()
          fieldNum = myFieldCount
        }

        gameScene.draw(player.id, data, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(player.id))
        if (grid.killInfo._2 != "" && grid.killInfo._3 != "" && snake.id != grid.killInfo._1) {
          gameScene.drawUserDieInfo(grid.killInfo._2, grid.killInfo._3)
          grid.lastTime -= 1
          if (grid.lastTime == 0) {
            grid.killInfo = ("", "", "")
          }
        }
      case None =>
        if(!isWin){
          if (firstCome) gameScene.drawGameWait()
          else {
            if(timeFlag){
              currentRank.filter(_.id == player.id).foreach { score =>
                myScore = myScore.copy(kill = score.k, area = score.area, endTime = System.currentTimeMillis())
              }
              timeFlag = false
              log.debug("my score has been set")
            }
            gameScene.drawGameDie(grid.getKiller(player.id).map(_._2),myScore)
            if(isContinue) {
              audioDie.play()
              log.info("play the dieSound.")
            }
            isContinue = false
          }
        }
        else{
//          animationTimer.start()
          gameScene.drawGameWin(player.id, winnerData.get.winnerName, winnerData.get.data)
        }
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

      case Protocol.SomeOneWin(winner, finalData) =>
        isWin = true
        winnerName = winner
        winnerData = Some(Protocol.SomeOneWin(winner, finalData))
//        gameScene.drawGameWin(player.id, winner, finalData)
        Boot.addToPlatform {
          audioWin.play()
          grid.cleanData()
//          animationTimer.stop()
        }

      case Protocol.Ranks(current) =>
        Boot.addToPlatform {
          currentRank = current
          if (grid.getGridData.snakes.exists(_.id == player.id) && !isWin)
            gameScene.drawRank(player.id, grid.getGridData.snakes, current)
        }

      case data: Protocol.Data4TotalSync =>
        log.debug(s"i receive Data4TotalSync!!${System.currentTimeMillis()}")
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
        Boot.addToPlatform{
          PerformanceTool.receivePingPackage(x)
        }


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
          audioWin.stop()
          audioDie.stop()
          firstCome = true
          scoreFlag = true
          timeFlag = true
          log.debug("timeFlag has been reset")
          if(isWin){
            isWin = false
            winnerName = "unknown"
          }
          fieldNum = 0
          animationTimer.start()
          isContinue = true
        }
        playActor ! PlayGameWebSocket.MsgToService(Protocol.Key(player.id, Constant.keyCode2Int(key), frame, actionId))
      }
    }

  }

}
