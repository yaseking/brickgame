package com.neo.sk.carnie.controller

import java.util.concurrent.atomic.AtomicInteger

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.common.{Constant, Context}
import com.neo.sk.carnie.paperClient.Protocol.{NeedToSync, NewFieldInfo, UserAction, UserLeft}
import com.neo.sk.carnie.paperClient._
import com.neo.sk.carnie.scene.{GameScene, PerformanceTool}
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
//  var scoreFlag = true
//  var timeFlag = true
  var isWin = false
  var exitFullScreen = false
  var winnerName = "unknown"
  var isContinues = true
  var winnerData : Option[Protocol.Data4TotalSync] = None
//  private var fieldNum = 0
  val audioFinish = new AudioClip(getClass.getResource("/mp3/finish.mp3").toString)
  val audioKill = new AudioClip(getClass.getResource("/mp3/kill.mp3").toString)
  val audioWin = new AudioClip(getClass.getResource("/mp3/win.mp3").toString)
  val audioDie = new AudioClip(getClass.getResource("/mp3/killed.mp3").toString)
  var newFieldInfo: scala.Option[Protocol.NewFieldInfo] = None
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None
  var newSnakeInfo: scala.Option[Protocol.NewSnakeInfo] = None
//  var totalData: scala.Option[Protocol.Data4TotalSync] = None
  var drawFunction: FrontProtocol.DrawFunction = FrontProtocol.DrawGameWait
  private var stageWidth = stageCtx.getStage.getWidth.toInt
  private var stageHeight = stageCtx.getStage.getHeight.toInt
  private var isContinue = true
  private var myScore = BaseScore(0, 0, 0l, 0l)
  private var maxArea: Int = 0
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

    if (newSnakeInfo.nonEmpty) {
      grid.snakes ++= newSnakeInfo.get.snake.map(s => s.id -> s).toMap
      grid.addNewFieldInfo(NewFieldInfo(newSnakeInfo.get.frameCount, newSnakeInfo.get.filedDetails))
      newSnakeInfo = None
    }

    if(syncGridData.nonEmpty) {
      grid.initSyncGridData(syncGridData.get)
      syncGridData = None
    } else {
      grid.update("f")
      if (newFieldInfo.nonEmpty && newFieldInfo.get.frameCount <= grid.frameCount) { //圈地信息
        if (newFieldInfo.get.frameCount == grid.frameCount) {
          grid.addNewFieldInfo(newFieldInfo.get)
        } else { //主动要求同步数据
          playActor ! PlayGameWebSocket.MsgToService(NeedToSync(player.id).asInstanceOf[UserAction])
        }
        newFieldInfo = None
      }
    }

    val gridData = grid.getGridData
    gridData.snakes.find(_.id == player.id) match {
      case Some(_) =>
        firstCome = false
        drawFunction = FrontProtocol.DrawBaseGame(gridData)

      case None if isWin =>

      case None if !firstCome =>
//        println(s"killer: ${grid.getKiller(player.id).map(_._2)}")
        drawFunction = FrontProtocol.DrawGameDie(grid.getKiller(player.id).map(_._2))

      case _ =>
        drawFunction = FrontProtocol.DrawGameWait
    }
  }

  def draw(offsetTime: Long): Unit = {
    drawFunction match {
      case FrontProtocol.DrawGameWait => gameScene.drawGameWait()

      case FrontProtocol.DrawGameOff => gameScene.drawGameOff(firstCome)

      case FrontProtocol.DrawGameWin(winner, winData) =>
        gameScene.drawGameWin(player.id, winner, winData)
        isContinue = false

      case FrontProtocol.DrawBaseGame(data) =>
        gameScene.draw(player.id, data, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(player.id))
        if (grid.killInfo._2 != "" && grid.killInfo._3 != "" && player.id != grid.killInfo._1) {//
          gameScene.drawUserDieInfo(grid.killInfo._2, grid.killInfo._3)
          grid.lastTime -= 1
          if (grid.lastTime == 0) {
            grid.killInfo = ("", "", "")
          }
        }

      case FrontProtocol.DrawGameDie(killerName) =>
        if (isContinue) audioDie.play()
        gameScene.drawGameDie(killerName, myScore, maxArea)
        grid.killInfo = ("", "", "")
        isContinue = false
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
        Boot.addToPlatform {
          drawFunction = FrontProtocol.DrawGameWin(winner, finalData)
          winnerName = winner
          winnerData = Some(finalData)
          isWin = true
          audioWin.play()
          //        gameScene.drawGameWin(player.id, winner, finalData)
          grid.cleanData()
        }

      case Protocol.WinnerBestScore(score) =>
          maxArea = Math.max(maxArea,score)

      case UserLeft(id) =>
        Boot.addToPlatform {
          println(s"user $id left:::")
          if (grid.snakes.contains(id)) grid.snakes -= id
          grid.returnBackField(id)
          grid.grid ++= grid.grid.filter(_._2 match { case Body(_, fid) if fid.nonEmpty && fid.get == id => true case _ => false }).map { g =>
            Point(g._1.x, g._1.y) -> Body(g._2.asInstanceOf[Body].id, None)
          }
        }

      case x@Protocol.DeadPage(id, kill, area, start, end) =>
        println(s"recv userDead $x")
        Boot.addToPlatform {
          myScore = BaseScore(kill, area, start, end)
        }


      case Protocol.Ranks(current) =>
        Boot.addToPlatform {
          currentRank = current
          maxArea = Math.max(maxArea,currentRank.find(_.id == player.id).map(_.area).getOrElse(0))
          if (grid.getGridData.snakes.exists(_.id == player.id) && !isWin)
            gameScene.drawRank(player.id, grid.getGridData.snakes, current)
        }

      case data: Protocol.Data4TotalSync =>
        Boot.addToPlatform{
          syncGridData = Some(data)
        }

      case data: Protocol.NewSnakeInfo =>
        println(s"!!!!!!new snake join!!!")
        Boot.addToPlatform{
          newSnakeInfo = Some(data)
        }

      case Protocol.SomeOneKilled(killedId, killedName, killerName) =>
        Boot.addToPlatform {
          grid.killInfo = (killedId, killedName, killerName)
          grid.lastTime = 100
        }

      case data: Protocol.NewFieldInfo =>
        Boot.addToPlatform{
          if(data.fieldDetails.exists(_.uid == player.id))
            audioFinish.play()
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
          drawFunction match {
            case FrontProtocol.DrawBaseGame(_) =>
            case _ =>
              drawFunction = FrontProtocol.DrawGameWait
              audioWin.stop()
              audioDie.stop()
              firstCome = true
              if(isWin){
                isWin = false
                winnerName = "unknown"
              }
//              fieldNum = 0
              animationTimer.start()
              isContinue = true
          }
        }
        playActor ! PlayGameWebSocket.MsgToService(Protocol.Key(player.id, Constant.keyCode2Int(key), frame, actionId))
      }
    }

  }

}
