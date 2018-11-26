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
  var isSynced = false
  var justSynced = false
  var isWin = false
  var winnerName = "unknown"
  var killInfo: scala.Option[(String, String, String)] = None
  var barrageDuration = 0
  var winData: Protocol.Data4TotalSync = grid.getGridData
  var newFieldInfo: scala.Option[Protocol.NewFieldInfo] = None
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None
  var newSnakeInfo: scala.Option[Protocol.NewSnakeInfo] = None
  var totalData: scala.Option[Protocol.Data4TotalSync] = None
  var isContinue = true
  var oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)

  private var myScore = BaseScore(0, 0, 0l, 0l)
  private var maxArea: Int = 0

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

//  var lastTime1 = 0L
  def gameRender(): Double => Unit = { _ =>
    val curTime = System.currentTimeMillis()
//    println(s"requestAnimationTime: ${curTime - lastTime1}")
    val offsetTime = curTime - logicFrameTime
    draw(offsetTime)
//    lastTime1 = curTime
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
      if (newSnakeInfo.nonEmpty) {
        newSnakeInfo.get.snake.foreach{ s =>
          grid.cleanSnakeTurnPoint(s.id)
        }
        grid.snakes ++= newSnakeInfo.get.snake.map(s => s.id -> s).toMap
        grid.addNewFieldInfo(NewFieldInfo(newSnakeInfo.get.frameCount, newSnakeInfo.get.filedDetails))
        newSnakeInfo = None
      }

      if (!justSynced) { //前端更新
        grid.update("f")
        if (newFieldInfo.nonEmpty && newFieldInfo.get.frameCount <= grid.frameCount) {
          if (newFieldInfo.get.frameCount == grid.frameCount) {
            grid.addNewFieldInfo(newFieldInfo.get)
//            if(newFieldInfo.get.fieldDetails.exists(_.uid == myId))
//              audioFinish.play()
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
      totalData = Some(grid.getGridData)
    }
  }


  def draw(offsetTime: Long): Unit = {

    if (webSocketClient.getWsState) {
        if(totalData.nonEmpty){
          val data = totalData.get
          if (isWin) {
            drawGame.drawGameWin(myId, winnerName, winData)
            audio1.play()
            dom.window.cancelAnimationFrame(nextFrame)
            isContinue = false
          } else {
            data.snakes.find(_.id == myId) match {
              case Some(_) =>
                if(firstCome) firstCome = false
//                val endTime1 = System.currentTimeMillis()
//                println(s"TimeBefore: ${endTime1 - currentTime}")
                drawGameImage(myId, data, offsetTime)
                if (killInfo.nonEmpty) {
                  val killBaseInfo = killInfo.get
                  if(killBaseInfo._3 == myId) audioKill.play()
                  drawGame.drawUserDieInfo(killBaseInfo._2, killBaseInfo._3)
                  barrageDuration -= 1
                  if (barrageDuration == 0) killInfo = None
                }
//                val endTime = System.currentTimeMillis()
//                println(s"TimeAfter: ${endTime - time2}")
//                println(s"drawTime: ${endTime - currentTime}")

              case None =>
                if (firstCome) drawGame.drawGameWait()
                else {
                  if (isContinue) audioKilled.play()
                  drawGame.drawGameDie(grid.getKiller(myId).map(_._2), myScore, maxArea)
                  killInfo = None
                  dom.window.cancelAnimationFrame(nextFrame)
                  isContinue = false
                }
            }
          }
        } else {
          drawGame.drawGameWait()
        }
    } else {
      drawGame.drawGameOff(firstCome, None, false, false)
    }
  }

  def drawGameImage(uid: String, data: Data4TotalSync, offsetTime: Long): Unit = {
    drawGame.drawGrid(uid, data, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(myId))
//    val endTime1 = System.currentTimeMillis()
//    println(s"drawGridTime: ${endTime1 - currentTime}")
    drawGame.drawSmallMap(data.snakes.filter(_.id == uid).map(_.header).head, data.snakes.filterNot(_.id == uid))
//    val endTime2 = System.currentTimeMillis()
//    println(s"drawSmallMapTime: ${endTime2 - endTime1}")
//    endTime2
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

      case UserLeft(id) =>
        println(s"user $id left:::$id")
        if (grid.snakes.contains(id)) grid.snakes -= id
        grid.returnBackField(id)
        grid.grid ++= grid.grid.filter(_._2 match { case Body(_, fid) if fid.nonEmpty && fid.get == id => true case _ => false }).map { g =>
          Point(g._1.x, g._1.y) -> Body(g._2.asInstanceOf[Body].id, None)
        }

      case Protocol.SomeOneWin(winner, finalData) =>
        isWin = true
        winnerName = winner
        winData = finalData
        grid.cleanData()

      case Protocol.WinnerBestScore(score) =>
          maxArea = Math.max(maxArea,score)


      case Protocol.Ranks(current) =>
        currentRank = current
        maxArea = Math.max(maxArea,currentRank.find(_.id == myId).map(_.area).getOrElse(0))
        if (grid.getGridData.snakes.exists(_.id == myId) && !isWin && isSynced) drawGame.drawRank(myId, grid.getGridData.snakes, currentRank)

      case data: Protocol.Data4TotalSync =>
        println(s"===========recv total data")
//        drawGame.drawField(data.fieldDetails, data.snakes)
        syncGridData = Some(data)
        justSynced = true
        isSynced = true

      case data: Protocol.NewSnakeInfo =>
        println(s"!!!!!!new snake---${data.snake} join!!!")
        if(data.filedDetails.exists(_.uid == myId))
          audioFinish.play()
        newSnakeInfo = Some(data)

      case Protocol.SomeOneKilled(killedId, killedName, killerName) =>
        killInfo = Some(killedId, killedName, killerName)
        barrageDuration = 100

      case x@Protocol.DeadPage(kill, area, start, end) =>
        println(s"recv userDead $x")
        myScore = BaseScore(kill, area, start, end)
        maxArea = Math.max(maxArea ,historyRank.find(_.id == myId).map(_.area).getOrElse(0))


      case data: Protocol.NewFieldInfo =>
        println(s"((((((((((((recv new field info")
//        if(data.fieldDetails.exists(_.uid == myId))
//          audioFinish.play()
        newFieldInfo = Some(data)

      case x@Protocol.ReceivePingPacket(_) =>
        PerformanceTool.receivePingPackage(x)


      case x@_ =>
        println(s"receive unknown msg:$x")
    }
  }
}
