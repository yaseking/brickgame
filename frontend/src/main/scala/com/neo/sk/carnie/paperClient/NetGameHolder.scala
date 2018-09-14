package com.neo.sk.carnie.paperClient

import java.util.concurrent.atomic.AtomicInteger

import com.neo.sk.carnie.paperClient.Constant.ColorsSetting
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
  private var myId = -1l

  var grid = new GridOnClient(Point(BorderSize.w, BorderSize.h))

  var firstCome = true
  var justSynced = false
  var scoreFlag = true
  var isWin = false
  var winnerName = "unknown"
  var killInfo=(0L,"","")
  var lastTime=0
  var newFieldInfo: scala.Option[Protocol.NewFieldInfo] = None
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None

  val idGenerator = new AtomicInteger(1)
  private var myActionHistory = Map[Int, (Int, Long)]() //(actionId, (keyCode, frameCount))

  private[this] val nameField = dom.document.getElementById("name").asInstanceOf[HTMLInputElement]
  private[this] val joinButton = dom.document.getElementById("join").asInstanceOf[HTMLButtonElement]
  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  private var nextFrame = 0
  private var logicFrameTime = System.currentTimeMillis()

  private[this] val drawGame:DrawGame = new DrawGame(ctx, canvas)
  private[this] val webSocketClient: WebSocketClient = new WebSocketClient(connectOpenSuccess,connectError,messageHandler,connectError)


  def main(): Unit = {
    joinButton.onclick = { event: MouseEvent =>
      webSocketClient.joinGame(nameField.value)
      event.preventDefault()
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
    dom.window.setInterval(()=>{webSocketClient.sendMessage(SendPingPacket(myId, System.currentTimeMillis()).asInstanceOf[UserAction])}, 100)
    dom.window.requestAnimationFrame(gameRender())
  }

  def gameRender(): Double => Unit = { _ =>
    val curTime = System.currentTimeMillis()
    val offsetTime = curTime - logicFrameTime
    draw(offsetTime)

    nextFrame = dom.window.requestAnimationFrame(gameRender())
  }


  def gameLoop(): Unit = {
    logicFrameTime = System.currentTimeMillis()

    if (webSocketClient.getWsState) {
      if (!justSynced) { //前端更新
        grid.update("f")
        if(newFieldInfo.nonEmpty && newFieldInfo.get.frameCount <= grid.frameCount) {
          if (newFieldInfo.get.frameCount == grid.frameCount) {
            grid.addNewFieldInfo(newFieldInfo.get)
          } else { //主动要求同步数据
            webSocketClient.sendMessage(RequireSync(myId))
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
      if (isWin) {
        drawGame.drawGameWin(winnerName)
        dom.window.cancelAnimationFrame(nextFrame)
      } else {
        val data = grid.getGridData
        data.snakes.find(_.id == myId) match {
          case Some(snake) =>
            firstCome = false
            if (scoreFlag) {
              drawGame.cleanMyScore
              scoreFlag = false
            }
            drawGame(myId, data, offsetTime)
            if(killInfo._2!=""&&killInfo._3!=""&&snake.id!=killInfo._1)
            {
              drawGame.drawUserDieInfo(killInfo._2,killInfo._3)
              lastTime-=1
              if(lastTime==0){
                killInfo=(0,"","")
              }
            }

          case None =>
            if(firstCome) drawGame.drawGameWait()
            else {
              drawGame.drawGameDie(grid.getKiller(myId).map(_._2), historyRank.find(_.id == myId).map(_.area))
              dom.window.cancelAnimationFrame(nextFrame)
            }
        }
      }
    } else {
      drawGame.drawGameOff(firstCome)
    }
  }

  def drawGame(uid: Long, data: Data4TotalSync, offsetTime: Long): Unit = {
//    val starTime = System.currentTimeMillis()
    drawGame.drawGrid(uid, data, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(0l), currentRank.filter(_.id == uid).map(_.area).headOption.getOrElse(0))
    drawGame.drawRank(uid, data.snakes, currentRank)
    drawGame.drawSmallMap(data.snakes.filter(_.id == uid).map(_.header).head, data.snakes.filterNot(_.id == uid))
//    println(s"drawGame time:${System.currentTimeMillis() - starTime}")
  }

  private def connectOpenSuccess(e:Event) = {
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
            scoreFlag = true
            firstCome = true
            if (isWin) {
              isWin = false
              winnerName = "unknown"
            }
          }
          Key(myId, e.keyCode, frame, actionId)
        }
        webSocketClient.sendMessage(msg)
      }}
    }
    e
  }

  private def connectError(e:Event) = {
    drawGame.drawGameOff(firstCome)
    e
  }

  private def messageHandler(data: GameMessage) = {
    data match {
      case Protocol.Id(id) => myId = id

      case Protocol.SnakeAction(id, keyCode, frame, actionId) =>
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

      case Protocol.SomeOneWin(winner) =>
        isWin = true
        winnerName = winner
        grid.cleanData()

      case Protocol.Ranks(current, history) =>
        currentRank = current
        historyRank = history

      case data: Protocol.Data4TotalSync =>
        syncGridData = Some(data)
        justSynced = true

      case Protocol.SomeOneKilled(killedId,killedName,killerName) =>
        killInfo=(killedId,killedName,killerName)
        lastTime=100

      case data: Protocol.NewFieldInfo =>
        newFieldInfo = Some(data)

      case x@Protocol.ReceivePingPacket(_) =>
        PerformanceTool.receivePingPackage(x)

      case x@_ =>
        println(s"receive unknown msg:$x")
    }
  }


}
