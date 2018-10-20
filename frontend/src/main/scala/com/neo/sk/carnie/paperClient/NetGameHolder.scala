package com.neo.sk.carnie.paperClient

import java.util.concurrent.atomic.AtomicInteger

import com.neo.sk.carnie.paperClient.Protocol._
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.html.{Document => _, _}
import org.scalajs.dom.raw._


import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 12:45 PM
  */
@JSExportTopLevel("paperClient.NetGameHolder")
object NetGameHolder extends js.JSApp {

  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]
  private var myId = ""

  var grid = new GridOnClient(Point(BorderSize.w, BorderSize.h))

  var firstCome = true
  var justSynced = false
  var scoreFlag = true
  var isWin = false
  var winnerName = "unknown"
  private var killInfo = ("", "", "")
  var lastTime = 0
  var winData: Protocol.Data4TotalSync = grid.getGridData
  var fieldNum = 1
  var snakeNum = 1
  var newFieldInfo: scala.Option[Protocol.NewFieldInfo] = None
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None
  var play = true

  val idGenerator = new AtomicInteger(1)
  private var myActionHistory = Map[Int, (Int, Long)]() //(actionId, (keyCode, frameCount))

  private[this] val nameField = dom.document.getElementById("name").asInstanceOf[HTMLInputElement]
  private[this] val joinButton = dom.document.getElementById("join").asInstanceOf[HTMLButtonElement]
  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private[this] val audio1 = dom.document.getElementById("audio").asInstanceOf[HTMLAudioElement]
  private[this] val audioFinish = dom.document.getElementById("audioFinish").asInstanceOf[HTMLAudioElement]
  private[this] val audioKill = dom.document.getElementById("audioKill").asInstanceOf[HTMLAudioElement]
  private[this] val audioKilled = dom.document.getElementById("audioKilled").asInstanceOf[HTMLAudioElement]

  private var nextFrame = 0
  private var isContinue = true
  private var logicFrameTime = System.currentTimeMillis()

  private[this] val drawGame: DrawGame = new DrawGame(ctx, canvas)
  private[this] val webSocketClient: WebSocketClient = new WebSocketClient(connectOpenSuccess, connectError, messageHandler, connectError)


  def main(): Unit = {
    joinButton.onclick = { event: MouseEvent =>
      if (nameField.value == "")
        dom.window.alert("您的游戏昵称不能为空！")
      else if (nameField.value.length > 16)
        dom.window.alert("您的游戏昵称不能超过16位！")
      else {
        webSocketClient.joinGame(nameField.value)
        event.preventDefault()
      }
    }
    nameField.focus()
    nameField.onkeypress = { event: KeyboardEvent =>
      if (event.keyCode == 13) {
        joinButton.click()
        event.preventDefault()
      }
    }
  }

  def startGame(): Unit = {
    drawGame.drawGameOn()
    dom.window.setInterval(() => gameLoop(), Protocol.frameRate)
    dom.window.setInterval(() => {
      webSocketClient.sendMessage(SendPingPacket(myId, System.currentTimeMillis()).asInstanceOf[UserAction])
    }, 100)
    dom.window.requestAnimationFrame(gameRender())
  }

  private var tempRender = System.currentTimeMillis()

  def gameRender(): Double => Unit = { _ =>
    val curTime = System.currentTimeMillis()
    val offsetTime = curTime - logicFrameTime
//    println(s"drawRender time:${curTime - tempRender}")
    tempRender = curTime
    draw(offsetTime)

    if (isContinue)
      nextFrame = dom.window.requestAnimationFrame(gameRender())
  }


  def gameLoop(): Unit = {
    logicFrameTime = System.currentTimeMillis()

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

  private var tempDraw = System.currentTimeMillis()

  def draw(offsetTime: Long): Unit = {
//    println(s"drawDraw time:${System.currentTimeMillis() - tempDraw}")
    tempDraw = System.currentTimeMillis()
    if (webSocketClient.getWsState) {
      val data = grid.getGridData
      if (isWin) {
        ctx.clearRect(0, 0, dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
        drawGame.drawWin(myId, winnerName, winData)
        audio1.play()
        dom.window.cancelAnimationFrame(nextFrame)
        isContinue = false
      } else {
        data.snakes.find(_.id == myId) match {
          case Some(snake) =>
//            println(s"data里有蛇：：：：：：")
            firstCome = false
            if (scoreFlag) {
              drawGame.cleanMyScore
              scoreFlag = false
            }
            data.killHistory.foreach {
              i => if (i.frameCount + 1 == data.frameCount && i.killerId == myId) audioKill.play()
            }
            var num = 0
            data.fieldDetails.find(_.uid == myId).get.scanField.foreach {
              row =>
                row.x.foreach {
                  x => num += (x._2 - x._1)
                }
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
              if (play) audioKilled.play()
              play = false
              drawGame.drawGameDie(grid.getKiller(myId).map(_._2))
              killInfo = ("", "", "")
              dom.window.cancelAnimationFrame(nextFrame)
              isContinue = false
            }
        }
      }
    } else {
      drawGame.drawGameOff(firstCome)
    }
  }


  private var temp = System.currentTimeMillis()

  def drawGameImage(uid: String, data: Data4TotalSync, offsetTime: Long): Unit = {
    val starTime = System.currentTimeMillis()
//    println(s"draw time:${starTime - temp}")
    temp = starTime
    drawGame.drawGrid(uid, data, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(myId), currentRank.filter(_.id == uid).map(_.area).headOption.getOrElse(0))
    //    drawGame.drawRank(uid, data.snakes, currentRank)
    drawGame.drawSmallMap(data.snakes.filter(_.id == uid).map(_.header).head, data.snakes.filterNot(_.id == uid))
    drawGame.drawRank(myId, grid.getGridData.snakes, currentRank)
//    println(s"drawGame time:${System.currentTimeMillis() - starTime}")
  }

  private def connectOpenSuccess(e: Event) = {
    startGame()
    canvas.focus()
    canvas.onkeydown = { e: dom.KeyboardEvent => {
      if (Constant.watchKeys.contains(e.keyCode)) {
        val msg: Protocol.UserAction = {
          val frame = grid.frameCount + 2
          val actionId = idGenerator.getAndIncrement()
          grid.addActionWithFrame(myId, e.keyCode, frame)
          if (e.keyCode != KeyCode.Space) {
            myActionHistory += actionId -> (e.keyCode, frame)
          } else { //重新开始游戏
            println("AAAAAA")
            audio1.pause()
            audio1.currentTime = 0
            audioKilled.pause()
            audioKilled.currentTime = 0
            play = true
            scoreFlag = true
            firstCome = true
            if (isWin) {
              isWin = false
              winnerName = "unknown"
            }
            nextFrame = dom.window.requestAnimationFrame(gameRender())
            isContinue = true
          }
          Key(myId, e.keyCode, frame, actionId)
        }
        webSocketClient.sendMessage(msg)
      }
    }
    }
    e
  }

  private def connectError(e: Event) = {
    println("connectError..")
    drawGame.drawGameOff(firstCome)
    e
  }

  private def messageHandler(data: GameMessage) = {
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

      case Protocol.SomeOneWin(winner, finalData) =>
        isWin = true
        winnerName = winner
        winData = finalData
        grid.cleanData()

      case Protocol.Ranks(current) =>
        currentRank = current
        if (grid.getGridData.snakes.exists(_.id == myId))
          drawGame.drawRank(myId, grid.getGridData.snakes, current)

      case data: Protocol.Data4TotalSync =>
//        println(s"receive data========================")
        syncGridData = Some(data)
        justSynced = true

      case Protocol.SomeOneKilled(killedId, killedName, killerName) =>
        killInfo = (killedId, killedName, killerName)
        lastTime = 100

      case data: Protocol.NewFieldInfo =>
        newFieldInfo = Some(data)

      case x@Protocol.ReceivePingPacket(_) =>
        PerformanceTool.receivePingPackage(x)

      case x@_ =>
        println(s"receive unknown msg:$x")
    }
  }


}
