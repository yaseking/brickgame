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
import com.neo.sk.carnie.util.{Component, JsFunc}

import scala.xml.Elem

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 12:45 PM
  */

class NetGameHolder(order: String, webSocketPara: WebSocketPara, mode: Int, img: Int = 0, frameRate: Int = 150) extends Component {
  //0:正常模式，1:反转模式, 2:2倍加速模式

  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]
  private var myId = ""

  var grid = new GridOnClient(Point(BorderSize.w, BorderSize.h))

  var firstCome = true
  var isSynced = false
  //  var justSynced = false
  var isWin = false
  //  var winnerName = "unknown"
  var killInfo: scala.Option[(String, String, String)] = None
  var barrageDuration = 0
  //  var winData: Protocol.Data4TotalSync = grid.getGridData
  var newFieldInfo = Map.empty[Long, Protocol.NewFieldInfo] //[frame, newFieldInfo)
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None
  var newSnakeInfo: scala.Option[Protocol.NewSnakeInfo] = None
  var isContinue = true
  var oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
  var drawFunction: FrontProtocol.DrawFunction = FrontProtocol.DrawGameWait
  val delay:Int = if(mode == 2) 4 else 2

  private var myScore = BaseScore(0, 0, 0l, 0l)
  private var maxArea: Int = 0
  private var winningData = WinData(0,Some(0))

  val idGenerator = new AtomicInteger(1)
  private var myActionHistory = Map[Int, (Int, Long)]() //(actionId, (keyCode, frameCount))
  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private[this] val audio1 = dom.document.getElementById("audio").asInstanceOf[HTMLAudioElement]
  private[this] val audioFinish = dom.document.getElementById("audioFinish").asInstanceOf[HTMLAudioElement]
  private[this] val audioKill = dom.document.getElementById("audioKill").asInstanceOf[HTMLAudioElement]
  private[this] val audioKilled = dom.document.getElementById("audioKilled").asInstanceOf[HTMLAudioElement]
  private[this] val bgm1 = dom.document.getElementById("bgm1").asInstanceOf[HTMLAudioElement]
  private[this] val bgm2 = dom.document.getElementById("bgm2").asInstanceOf[HTMLAudioElement]
  private[this] val bgm3 = dom.document.getElementById("bgm3").asInstanceOf[HTMLAudioElement]
  private[this] val bgm4 = dom.document.getElementById("bgm4").asInstanceOf[HTMLAudioElement]
  private[this] val bgm5 = dom.document.getElementById("bgm5").asInstanceOf[HTMLAudioElement]
  private[this] val bgm7 = dom.document.getElementById("bgm7").asInstanceOf[HTMLAudioElement]
  private[this] val bgm8 = dom.document.getElementById("bgm8").asInstanceOf[HTMLAudioElement]
  private[this] val bgmList = List(bgm1, bgm2, bgm3, bgm4, bgm5,  bgm7, bgm8)
  private val bgmAmount = bgmList.length
  private var BGM = dom.document.getElementById("bgm4").asInstanceOf[HTMLAudioElement]
  private[this] val rankCanvas = dom.document.getElementById("RankView").asInstanceOf[Canvas] //把排行榜的canvas置于最上层，所以监听最上层的canvas
  private val x = (dom.window.innerWidth / 2).toInt - 145
  private val y = (dom.window.innerHeight / 2).toInt - 180

  dom.document.addEventListener("visibilitychange", { e: Event =>
    if (dom.document.visibilityState.asInstanceOf[VisibilityState] != VisibilityState.hidden) {
      println("has Synced")
      updateListener()
    }
  })

  private var logicFrameTime = System.currentTimeMillis()

  private[this] val drawGame: DrawGame = new DrawGame(ctx, canvas, img)
  private[this] val webSocketClient: WebSocketClient = new WebSocketClient(connectOpenSuccess, connectError, messageHandler, connectError)

  def init(): Unit = {
    webSocketClient.setUp(order, webSocketPara)
  }

  def updateListener(): Unit = {
    webSocketClient.sendMessage(NeedToSync(myId).asInstanceOf[UserAction])
  }

  def getRandom(s: Int):Int = {
    val rnd = new scala.util.Random
    rnd.nextInt(s)
  }

  def startGame(): Unit = {
    drawGame.drawGameOn()
    BGM = bgmList(getRandom(bgmAmount))
    BGM.play()
    dom.window.setInterval(() => gameLoop(), frameRate)
    dom.window.setInterval(() => {
      webSocketClient.sendMessage(SendPingPacket(myId, System.currentTimeMillis()).asInstanceOf[UserAction])
    }, 100)
    dom.window.requestAnimationFrame(gameRender())
  }

  def gameRender(): Double => Unit = { _ =>
    val curTime = System.currentTimeMillis()
    //    println(s"requestAnimationTime: ${curTime - lastTime1}")
    val offsetTime = curTime - logicFrameTime
    draw(offsetTime)
    //    lastTime1 = curTime
    if (isContinue)
      dom.window.requestAnimationFrame(gameRender())
  }


  def gameLoop(): Unit = {
//    println(s"snakes:::::::::::::${grid.snakes.keys}")
    logicFrameTime = System.currentTimeMillis()
    if ((oldWindowBoundary.x != dom.window.innerWidth.toFloat) || (oldWindowBoundary.y != dom.window.innerHeight.toFloat)) {
      drawGame.resetScreen()
      oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
      if (!isContinue) {
        if (isWin) {
          val winInfo = drawFunction.asInstanceOf[FrontProtocol.DrawGameWin]
          drawGame.drawGameWin(myId, winInfo.winnerName, winInfo.winData,winningData)
        } else {
          drawGame.drawGameDie(grid.getKiller(myId).map(_._2), myScore, maxArea)
        }
      }
    }

    if (webSocketClient.getWsState) {
      if (newSnakeInfo.nonEmpty) {
        println(s"newSnakeInfo: $newSnakeInfo")
        newSnakeInfo.get.snake.foreach { s =>
          grid.cleanSnakeTurnPoint(s.id) //清理死前拐点
        }
        grid.snakes ++= newSnakeInfo.get.snake.map(s => s.id -> s).toMap
        grid.addNewFieldInfo(NewFieldInfo(newSnakeInfo.get.frameCount, newSnakeInfo.get.filedDetails))
        newSnakeInfo = None
      }

      if (syncGridData.nonEmpty) { //逻辑帧更新数据
        grid.initSyncGridData(syncGridData.get)
        syncGridData = None
      } else {
        grid.update("f")
        if (newFieldInfo.nonEmpty) {
          val frame = newFieldInfo.keys.min
          val newFieldData = newFieldInfo(frame)
          if (frame == grid.frameCount) {
            grid.addNewFieldInfo(newFieldData)
            newFieldInfo -= frame
          } else if (frame < grid.frameCount) {
            webSocketClient.sendMessage(NeedToSync(myId).asInstanceOf[UserAction])
          }
        }
      }

      if (!isWin) {
        val gridData = grid.getGridData
        drawFunction = gridData.snakes.find(_.id == myId) match {
          case Some(_) =>
            if (firstCome) firstCome = false
            if (BGM.paused) {
              BGM = bgmList(getRandom(bgmAmount))
              BGM.play()
            }
            FrontProtocol.DrawBaseGame(gridData)

          case None if !firstCome =>
            FrontProtocol.DrawGameDie(grid.getKiller(myId).map(_._2))

          case _ =>
            FrontProtocol.DrawGameWait
        }
      }
    } else {
      drawFunction = FrontProtocol.DrawGameOff
    }
  }

  def draw(offsetTime: Long): Unit = {
    drawFunction match {
      case FrontProtocol.DrawGameWait =>
        drawGame.drawGameWait()

      case FrontProtocol.DrawGameOff =>
        if(!BGM.paused){
          BGM.pause()
          BGM.currentTime = 0
        }
        drawGame.drawGameOff(firstCome, None, false, false)

      case FrontProtocol.DrawGameWin(winner, winData) =>
        if(!BGM.paused){
          BGM.pause()
          BGM.currentTime = 0
        }
        drawGame.drawGameWin(myId, winner, winData,winningData)
        audio1.play()
        isContinue = false

      case FrontProtocol.DrawBaseGame(data) =>
        //        println("draw---DrawBaseGame!!")
        drawGameImage(myId, data, offsetTime)
        if (killInfo.nonEmpty) {
          val killBaseInfo = killInfo.get
          if (killBaseInfo._3 == myId) audioKill.play()
          drawGame.drawBarrage(killBaseInfo._2, killBaseInfo._3)
          barrageDuration -= 1
          if (barrageDuration == 0) killInfo = None
        }

      case FrontProtocol.DrawGameDie(killerName) =>
        if(!BGM.paused){
          BGM.pause()
          BGM.currentTime = 0
        }
        if (isContinue) audioKilled.play()
        drawGame.drawGameDie(killerName, myScore, maxArea)
        killInfo = None
        isContinue = false
    }
  }


  def drawGameImage(uid: String, data: Data4TotalSync, offsetTime: Long): Unit = {
    drawGame.drawGrid(uid, data, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(myId), frameRate = frameRate)
    drawGame.drawSmallMap(data.snakes.filter(_.id == uid).map(_.header).head, data.snakes.filterNot(_.id == uid))
  }

  private def connectOpenSuccess(event0: Event, order: String) = {
    if (order == "playGame") {
      startGame()
      rankCanvas.focus()
      rankCanvas.onkeydown = { e: dom.KeyboardEvent => {
        if (Constant.watchKeys.contains(e.keyCode)) {
          val msg: Protocol.UserAction = {
            val frame = grid.frameCount + delay//2 4
            //            println(s"frame : $frame")
            val actionId = idGenerator.getAndIncrement()
            val newKeyCode =
              if (mode == 1)
                e.keyCode match {
                  case KeyCode.Left => KeyCode.Right
                  case KeyCode.Right => KeyCode.Left
                  case KeyCode.Down => KeyCode.Up
                  case KeyCode.Up => KeyCode.Down
                  case _ => KeyCode.Space
                } else e.keyCode
            println(s"onkeydown：$newKeyCode")
            grid.addActionWithFrame(myId, newKeyCode, frame)
            if (newKeyCode != KeyCode.Space) {
              myActionHistory += actionId -> (newKeyCode, frame)
            } else { //重新开始游戏
              drawFunction match {
                case FrontProtocol.DrawBaseGame(_) =>
                case _ =>
//                  grid.cleanData()
                  grid.cleanSnakeTurnPoint(myId)
                  grid.actionMap = grid.actionMap.filterNot(_._2.contains(myId))
                  drawFunction = FrontProtocol.DrawGameWait
                  audio1.pause()
                  audio1.currentTime = 0
                  audioKilled.pause()
                  audioKilled.currentTime = 0
                  firstCome = true
                  if (isWin) isWin = false
                  myScore = BaseScore(0, 0, 0l, 0l)
                  isContinue = true
//                  backBtn.style.display="none"
//                  rankCanvas.addEventListener("",null)
                  dom.window.requestAnimationFrame(gameRender())
              }
            }
            Key(myId, newKeyCode, frame, actionId)
          }
          webSocketClient.sendMessage(msg)
          e.preventDefault()
        }
      }
      }
      //退出房间触发事件
//      rankCanvas.onmousedown = { e:dom.MouseEvent =>
//        drawFunction match {
//          case FrontProtocol.DrawGameDie(_) =>
//            if(
//              e.pageX > x &&
//                e.pageX < x + 175 &&
//                e.pageY > y + 250 &&
//                e.pageY < y + 310
//            ) {
//              dom.document.location.reload() //重新进入游戏
////              dom.window.location.reload()
//            }
//          case _ =>
//        }
//        e.preventDefault()
//      }
    }
    event0
  }

  private def connectError(e: Event) = {
    drawGame.drawGameOff(firstCome, None, false, false)
    e
  }

  private def messageHandler(data: GameMessage): Unit = {
    data match {
      case Protocol.Id(id) =>
        myId = id

      case Protocol.SnakeAction(id, keyCode, frame, actionId) =>
        //        println(s"i got ${grid.frameCount}, frame : $frame")
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

      case UserLeft(id) =>
        println(s"user $id left:::")
        if (grid.snakes.contains(id)) grid.snakes -= id
        //        grid.cleanSnakeTurnPoint(id)
        grid.returnBackField(id)
        grid.grid ++= grid.grid.filter(_._2 match { case Body(_, fid) if fid.nonEmpty && fid.get == id => true case _ => false }).map { g =>
          Point(g._1.x, g._1.y) -> Body(g._2.asInstanceOf[Body].id, None)
        }

      case Protocol.SomeOneWin(winner, finalData) =>
        drawFunction = FrontProtocol.DrawGameWin(winner, finalData)
        isWin = true
        //        winnerName = winner
        //        winData = finalData
        grid.cleanData()

      case Protocol.WinnerBestScore(score) =>
        maxArea = Math.max(maxArea, score)


      case Protocol.Ranks(current) =>
        currentRank = current
        maxArea = Math.max(maxArea, currentRank.find(_.id == myId).map(_.area).getOrElse(0))
        if (grid.getGridData.snakes.exists(_.id == myId) && !isWin && isSynced)
          drawGame.drawRank(myId, grid.getGridData.snakes, currentRank)

      case data: Protocol.Data4TotalSync =>
        println(s"===========recv total data")
        //        drawGame.drawField(data.fieldDetails, data.snakes)
        syncGridData = Some(data)
        newFieldInfo = newFieldInfo.filterKeys(_ > data.frameCount)
        //        justSynced = true
        isSynced = true

      case data: Protocol.NewSnakeInfo =>
        println(s"!!!!!!new snake---${data.snake} join!!!isContinue$isContinue")
        newSnakeInfo = Some(data)

      case Protocol.SomeOneKilled(killedId, killedName, killerName) =>
        killInfo = Some(killedId, killedName, killerName)
        barrageDuration = 100

      case x@Protocol.DeadPage(id, kill, area, start, end) =>
        println(s"recv userDead $x")
        //        grid.cleanSnakeTurnPoint(id)
        myScore = BaseScore(kill, area, start, end)
        maxArea = Math.max(maxArea, historyRank.find(_.id == myId).map(_.area).getOrElse(0))

      case data: Protocol.NewFieldInfo =>
        println(s"((((((((((((recv new field info")
        if (data.fieldDetails.exists(_.uid == myId))
          audioFinish.play()
        newFieldInfo += data.frameCount -> data

      case x@Protocol.ReceivePingPacket(_) =>
        PerformanceTool.receivePingPackage(x)

      case x@Protocol.WinData(winnerScore,yourScore) =>
        println(s"receive winningData msg:$x")
        winningData = x

      case x@_ =>
        println(s"receive unknown msg:$x")
    }
  }

  override def render: Elem = {
    init()
    <div></div>
  }
}
