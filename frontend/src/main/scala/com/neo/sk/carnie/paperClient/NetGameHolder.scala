package com.neo.sk.carnie.paperClient

import java.util.concurrent.atomic.AtomicInteger
import com.neo.sk.carnie.common.Constant
import org.scalajs.dom.html.Canvas
import com.neo.sk.carnie.paperClient.Protocol._
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.html.{Document => _, _}
import org.scalajs.dom.raw._
import com.neo.sk.carnie.paperClient.WebSocketProtocol._
/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 12:45 PM
  */

class NetGameHolder(order: String, webSocketPara: WebSocketPara) {

  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]
  private var myId = ""

  var grid = new GridOnClient(Point(BorderSize.w, BorderSize.h))

  var firstCome = true
  var justSynced = false
//  var scoreFlag = true
  var isWin = false
  var winnerName = "unknown"
  private var killInfo = ("", "", "")
  var lastTime = 0
  var winData: Protocol.Data4TotalSync = grid.getGridData
  var fieldNum = 1
  var snakeNum = 1
  var newFieldInfo: scala.Option[Protocol.NewFieldInfo] = None
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None
  //  var play = true
  var isContinue = true
  var oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)

  private var myScore = BaseScore(0, 0, 0l, 0l)
  private var maxArea: Int = 0
  private var scale = 1.0

  var replayFinish = false

  val idGenerator = new AtomicInteger(1)
  private var myActionHistory = Map[Int, (Int, Long)]() //(actionId, (keyCode, frameCount))
  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private[this] val audio1 = dom.document.getElementById("audio").asInstanceOf[HTMLAudioElement]
  private[this] val audioFinish = dom.document.getElementById("audioFinish").asInstanceOf[HTMLAudioElement]
  private[this] val audioKill = dom.document.getElementById("audioKill").asInstanceOf[HTMLAudioElement]
  private[this] val audioKilled = dom.document.getElementById("audioKilled").asInstanceOf[HTMLAudioElement]
  private[this] val rankCanvas = dom.document.getElementById("RankView").asInstanceOf[Canvas] //把排行榜的canvas置于最上层，所以监听最上层的canvas


  private var nextFrame = 0
  private var logicFrameTime = System.currentTimeMillis()

  private[this] val drawGame: DrawGame = new DrawGame(ctx, canvas)
  private[this] val webSocketClient: WebSocketClient = new WebSocketClient(connectOpenSuccess, connectError, messageHandler, connectError)

  def init(): Unit = {
    webSocketClient.setUp(order, webSocketPara)
  }


  def startGame(): Unit = {
    drawGame.drawGameOn()
    dom.window.setInterval(() => gameLoop(), Protocol.frameRate)
    dom.window.setInterval(() => {
      webSocketClient.sendMessage(SendPingPacket(myId, System.currentTimeMillis()).asInstanceOf[UserAction])
    }, 100)
    dom.window.requestAnimationFrame(gameRender())
  }

  def gameRender(): Double => Unit = { _ =>
    val curTime = System.currentTimeMillis()
    val offsetTime = curTime - logicFrameTime
    draw(offsetTime)

    if (isContinue)
      nextFrame = dom.window.requestAnimationFrame(gameRender())
  }


  def gameLoop(): Unit = {
    logicFrameTime = System.currentTimeMillis()
    if ((oldWindowBoundary.x != dom.window.innerWidth.toFloat) || (oldWindowBoundary.y != dom.window.innerHeight.toFloat)) {
      drawGame.resetScreen()
      oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
      if(!isContinue) {
        if(isWin) {
          drawGame.drawGameWin(myId, winnerName, winData)
        } else {
          drawGame.drawGameDie(grid.getKiller(myId).map(_._2), myScore, maxArea)
        }
      }
    }

    if (webSocketClient.getWsState) {
      if (!justSynced) { //前端更新
        grid.update("f")

        if (newFieldInfo.nonEmpty && newFieldInfo.get.frameCount <= grid.frameCount) {
          if (newFieldInfo.get.frameCount == grid.frameCount) {
            grid.addNewFieldInfo(newFieldInfo.get)
          } else { //主动要求同步数据
            webSocketClient.sendMessage(NeedToSync(myId).asInstanceOf[UserAction])
          }
          newFieldInfo = None
        }
      } else if (syncGridData.nonEmpty) {
        grid.initSyncGridData(syncGridData.get)
        syncGridData = None
        justSynced = false
      }
    }
  }


  def draw(offsetTime: Long): Unit = {

    if (webSocketClient.getWsState) {
      if (replayFinish) {
        drawGame.drawGameOff(firstCome, Some(true), false, false)
      } else {
        val data = grid.getGridData
        if (isWin) {
          ctx.clearRect(0, 0, dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
          drawGame.drawGameWin(myId, winnerName, winData)
          audio1.play()
          dom.window.cancelAnimationFrame(nextFrame)
          isContinue = false
        } else {
          data.snakes.find(_.id == myId) match {
            case Some(snake) =>
              firstCome = false
//              if (scoreFlag) {
//                myScore = BaseScore(0, 0, System.currentTimeMillis(), 0l)
//                scoreFlag = false
//              }
              data.killHistory.foreach { i =>
                if (i.frameCount + 1 == data.frameCount && i.killerId == myId) audioKill.play()
              }
              var num = 0
              data.fieldDetails.find(_.uid == myId).map(_.scanField).getOrElse(Nil).foreach { row =>
                row.x.foreach { y => num += (y._2 - y._1) }
              }
              if (fieldNum < num && snake.id == myId) {
                audioFinish.play()
              }
              fieldNum = num
              drawGameImage(myId, data, offsetTime)
              if (killInfo._2 != "" && killInfo._3 != "" && snake.id != killInfo._1) {
                drawGame.drawUserDieInfo(killInfo._2, killInfo._3)
                lastTime -= 1
                if (lastTime == 0) {
                  killInfo = ("", "", "")
                }
              }

            case None =>
              if (firstCome) drawGame.drawGameWait()
              else {
                if (isContinue) audioKilled.play()
//                currentRank.filter(_.id == myId).foreach { score =>
//                  myScore = myScore.copy(kill = score.k, area = score.area, endTime = System.currentTimeMillis())
//                }
                drawGame.drawGameDie(grid.getKiller(myId).map(_._2), myScore, maxArea)
                killInfo = ("", "", "")
                dom.window.cancelAnimationFrame(nextFrame)
                isContinue = false
              }
          }
        }
      }
    } else {
      drawGame.drawGameOff(firstCome, None, false, false)
    }
  }

  def drawGameImage(uid: String, data: Data4TotalSync, offsetTime: Long): Unit = {
    scale = drawGame.drawGrid(uid, data, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(myId), scale)
    drawGame.drawSmallMap(data.snakes.filter(_.id == uid).map(_.header).head, data.snakes.filterNot(_.id == uid))
  }

  private def connectOpenSuccess(event0: Event, order: String) = {
    startGame()
    if (order == "playGame") {
      rankCanvas.focus()
      rankCanvas.onkeydown = { e: dom.KeyboardEvent => {
        if (Constant.watchKeys.contains(e.keyCode)) {
          println(s"onkeydown：${e.keyCode}")
          val msg: Protocol.UserAction = {
            val frame = grid.frameCount + 2
            val actionId = idGenerator.getAndIncrement()
            grid.addActionWithFrame(myId, e.keyCode, frame)
            if (e.keyCode != KeyCode.Space) {
              myActionHistory += actionId -> (e.keyCode, frame)
            } else { //重新开始游戏
              audio1.pause()
              audio1.currentTime = 0
              audioKilled.pause()
              audioKilled.currentTime = 0
//              scoreFlag = true
              firstCome = true
              if (isWin) {
                isWin = false
                winnerName = "unknown"
              }
              myScore = BaseScore(0, 0, 0l, 0l)
              isContinue = true
              nextFrame = dom.window.requestAnimationFrame(gameRender())
            }
            Key(myId, e.keyCode, frame, actionId)
          }
          webSocketClient.sendMessage(msg)
          e.preventDefault()
        }
      }
      }
    }
    event0
  }

  private def connectError(e: Event) = {
    drawGame.drawGameOff(firstCome, None, false, false)
    e
  }

  private def messageHandler(data: GameMessage): Unit = {
    data match {
      case Protocol.Id(id) => myId = id

      case Protocol.SnakeAction(id, keyCode, frame, actionId) =>
        if (grid.snakes.exists(_._1 == id)) {
          if (id == myId) { //收到自己的进行校验是否与预判一致，若不一致则回溯
            if (myActionHistory.get(actionId).isEmpty) { //前端没有该项，则加入
              grid.addActionWithFrame(id, keyCode, frame)
              if (frame < grid.frameCount && grid.frameCount - frame <= (grid.maxDelayed - 1)) { //回溯
                val oldGrid = grid
                oldGrid.recallGrid(frame, grid.frameCount)
                grid = oldGrid
              }
            } else {
              if (myActionHistory(actionId)._1 != keyCode || myActionHistory(actionId)._2 != frame) { //若keyCode或则frame不一致则进行回溯
                grid.deleteActionWithFrame(id, myActionHistory(actionId)._2)
                grid.addActionWithFrame(id, keyCode, frame)
                val miniFrame = Math.min(frame, myActionHistory(actionId)._2)
                if (miniFrame < grid.frameCount && grid.frameCount - miniFrame <= (grid.maxDelayed - 1)) { //回溯
                  val oldGrid = grid
                  oldGrid.recallGrid(miniFrame, grid.frameCount)
                  grid = oldGrid
                }
              }
              myActionHistory -= actionId
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

      case ReStartGame =>
        audio1.pause()
        audio1.currentTime = 0
        audioKilled.pause()
        audioKilled.currentTime = 0
//        scoreFlag = true
        firstCome = true
        myScore = BaseScore(0, 0, 0l, 0l)
        if (isWin) {
          isWin = false
          winnerName = "unknown"
        }
        isContinue = true
        nextFrame = dom.window.requestAnimationFrame(gameRender())

      case Protocol.SomeOneWin(winner, finalData) =>
        isWin = true
        winnerName = winner
        winData = finalData
        grid.cleanData()

      case Protocol.Ranks(current) =>
        currentRank = current
        maxArea = Math.max(currentRank.find(_.id == myId).map(_.area).getOrElse(0), maxArea)
        if (grid.getGridData.snakes.exists(_.id == myId) && !isWin) drawGame.drawRank(myId, grid.getGridData.snakes, currentRank)

      case data: Protocol.Data4TotalSync =>
        drawGame.drawField(data.fieldDetails, data.snakes)
        syncGridData = Some(data)
        justSynced = true

      case Protocol.SomeOneKilled(killedId, killedName, killerName) =>
        killInfo = (killedId, killedName, killerName)
        lastTime = 100

      case x@Protocol.DeadPage(kill, area, start, end) =>
        println(s"recv userDead $x")
        myScore = BaseScore(kill, area, start, end)

      case data: Protocol.NewFieldInfo =>
        newFieldInfo = Some(data)

      case x@Protocol.ReceivePingPacket(_) =>
        PerformanceTool.receivePingPackage(x)

      case x@Protocol.ReplayFinish(_) =>
        println("get message replay finish")
        replayFinish = true

      case x@_ =>
        println(s"receive unknown msg:$x")
    }
  }
}
