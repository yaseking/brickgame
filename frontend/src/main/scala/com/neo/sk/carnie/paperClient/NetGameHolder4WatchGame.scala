package com.neo.sk.carnie.paperClient

import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicInteger

import com.neo.sk.carnie.common.Constant

//import com.neo.sk.carnie.common.Constant
import com.neo.sk.carnie.paperClient.Protocol._
import com.neo.sk.carnie.paperClient.WebSocketProtocol._
import com.neo.sk.carnie.util.Component
import org.scalajs.dom
//import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.html.{Canvas, Document => _}
import org.scalajs.dom.raw._

import scala.xml.Elem

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 12:45 PM
  */

class NetGameHolder4WatchGame(order: String, webSocketPara: WebSocketPara) extends Component {
  //0:正常模式，1:反转模式, 2:2倍加速模式

  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]
  private var myId = ""
  private var watcherId = ""

  var grid = new GridOnClient(Point(BorderSize.w, BorderSize.h))

  var firstCome = true
  var isSynced = false
  //  var justSynced = false
  var isWin = false
  //  var winnerName = "unknown"
  var killInfo: scala.Option[(String, String, String)] = None
  var barrageDuration = 0
  //  var winData: Protocol.Data4TotalSync = grid.getGridData
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None
  var deadUser = Map.empty[Int, List[String]] //frame, userId
  var newFieldInfo = Map.empty[Int, Protocol.NewFieldInfo] //[frame, newFieldInfo)
  var newSnakeInfo = Map.empty[Int, NewSnakeInfo]

  var syncFrame: scala.Option[Protocol.SyncFrame] = None
  //  var totalData: scala.Option[Protocol.Data4TotalSync] = None
  var isContinue = true
  var frameRate: Int = frameRate1
  var oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
  var drawFunction: FrontProtocol.DrawFunction = FrontProtocol.DrawGameWait

  private var recallFrame: scala.Option[Int] = None
  private var myScore = BaseScore(0, 0, 0)
  private var maxArea: Short = 0
  private var winningData = WinData(0,Some(0))

  var pingMap = Map.empty[Short, Long] // id, 时间戳

  var pingId: Short = 0

  val idGenerator = new AtomicInteger(1)
  private var myActionHistory = Map[Int, (Int, Int)]() //(actionId, (keyCode, frameCount))
  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
//  private[this] val audio1 = dom.document.getElementById("audio").asInstanceOf[HTMLAudioElement]
  private[this] val audioFinish = dom.document.getElementById("audioFinish").asInstanceOf[HTMLAudioElement]
  private[this] val audioKill = dom.document.getElementById("audioKill").asInstanceOf[HTMLAudioElement]
  private[this] val audioKilled = dom.document.getElementById("audioKilled").asInstanceOf[HTMLAudioElement]
  private[this] val bgm4 = dom.document.getElementById("bgm4").asInstanceOf[HTMLAudioElement]
  private[this] val bgmList = List(bgm4)
  private val bgmAmount = bgmList.length
  private var BGM = dom.document.getElementById("bgm4").asInstanceOf[HTMLAudioElement]

  dom.document.addEventListener("visibilitychange", { e: Event =>
    if (dom.document.visibilityState.asInstanceOf[VisibilityState] != VisibilityState.hidden) {
      println("has Synced")
      updateListener()
    }
  })

  private var logicFrameTime = System.currentTimeMillis()

  private[this] var drawGame: DrawGame = new DrawGame(ctx, canvas)
  private[this] val webSocketClient: WebSocketClient = new WebSocketClient(connectOpenSuccess, connectError, messageHandler, connectError)

  def init(): Unit = {
    webSocketClient.setUp(order, webSocketPara)
  }

  def updateListener(): Unit = {
    webSocketClient.sendMessage(NeedToSync.asInstanceOf[UserAction])
  }

  def getRandom(s: Int):Int = {
    val rnd = new scala.util.Random
    rnd.nextInt(s)
  }

  def startGame(): Unit = {
    drawGame.drawGameOn()
//    BGM = bgmList(getRandom(bgmAmount))
//    BGM.play()
    dom.window.setInterval(() => gameLoop(), frameRate)
    dom.window.setInterval(() => {
      if (pingId > 10000) pingId = 0 else pingId = (pingId + 1).toShort
      pingMap += (pingId -> System.currentTimeMillis())
      webSocketClient.sendMessage(SendPingPacket(pingId).asInstanceOf[UserAction])
    }, 250)
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
      recallFrame match {
        case Some(-1) =>
          println("!!!!!!!!:NeedToSync")
          webSocketClient.sendMessage(NeedToSync.asInstanceOf[UserAction])
          recallFrame = None

        case Some(frame) =>
          val time1 = System.currentTimeMillis()
          val oldGrid = grid
          println(s"before recall...myTurnPoints:${grid.snakeTurnPoints.get(myId)}")
          grid.historyDieSnake.filter{d => d._2.contains(myId) && d._1 > frame}.keys.headOption match {
            case Some(dieFrame) =>
              if (dieFrame - 1 == frame) grid.setGridInGivenFrame(frame)
              else oldGrid.recallGrid(frame, dieFrame - 1)

            case None =>
              oldGrid.recallGrid(frame, grid.frameCount)
          }
          println(s"after recall...myTurnPoints:${grid.snakeTurnPoints.get(myId)}")
          grid = oldGrid
          println(s"after recall time: ${System.currentTimeMillis() - time1}...after frame:${grid.frameCount}")
          recallFrame = None

        case None =>
      }

      if (syncGridData.nonEmpty) { //全量数据
        if(grid.snakes.nonEmpty){
          println("total syncGridData")
          grid.historyStateMap += grid.frameCount -> (grid.snakes, grid.grid, grid.snakeTurnPoints)
        }
        grid.initSyncGridData(syncGridData.get)
        addBackendInfo4Sync(grid.frameCount)
        syncGridData = None
      } else if (syncFrame.nonEmpty) { //局部数据仅同步帧号
        val frontend = grid.frameCount
        val backend = syncFrame.get.frameCount
        val advancedFrame = Math.abs(backend - frontend)
        if (frontend > backend && grid.historyStateMap.get(backend).nonEmpty) {
          println(s"frontend advanced backend,frontend$frontend,backend:$backend")
          grid.setGridInGivenFrame(backend)
        } else if (backend >= frontend && advancedFrame < (grid.maxDelayed - 1)) {
          println(s"backend advanced frontend,frontend$frontend,backend:$backend")
          (1 to advancedFrame).foreach { _ =>
            addBackendInfo(grid.frameCount)
            grid.updateOnClient()
          }
        } else {
          webSocketClient.sendMessage(NeedToSync.asInstanceOf[UserAction])
        }
        syncFrame = None
      } else {
        addBackendInfo(grid.frameCount)
        grid.updateOnClient()
        addBackendInfo(grid.frameCount)
      }

      if (!isWin) {
        val gridData = grid.getGridData
        drawFunction = gridData.snakes.find(_.id == myId) match {
          case Some(_) =>
            if (firstCome) firstCome = false
//            if (BGM.paused) {
//              BGM = bgmList(getRandom(bgmAmount))
//              BGM.play()
//            }
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
//        if(!BGM.paused){
//          BGM.pause()
//          BGM.currentTime = 0
//        }
        drawGame.drawGameOff(firstCome, None, false, false)

      case FrontProtocol.DrawGameWin(winner, winData) =>
//        if(!BGM.paused){
//          BGM.pause()
//          BGM.currentTime = 0
//        }
        drawGame.drawGameWin(myId, winner, winData,winningData)
//        audio1.play()
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
//        if(!BGM.paused){
//          BGM.pause()
//        BGM.currentTime = 0
//        }
        if (isContinue) audioKilled.play()
        drawGame.drawGameDie(killerName, myScore, maxArea)
        killInfo = None
        isContinue = false
    }
  }


  def drawGameImage(uid: String, data: Data4TotalSync, offsetTime: Long): Unit = {
    drawGame.drawGrid(uid, data, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(myId), frameRate = frameRate, newFieldInfo = newFieldInfo.get(grid.frameCount + 1 ))
    drawGame.drawSmallMap(data.snakes.filter(_.id == uid).map(_.header).head, data.snakes.filterNot(_.id == uid))
  }

  private def connectOpenSuccess(event0: Event, order: String):Unit = {
    drawGame.drawGameWait()
  }

  private def connectError(e: Event) = {
    drawGame.drawGameOff(firstCome, None, false, false)
    e
  }

  private def messageHandler(data: GameMessage): Unit = {
    data match {
      case Protocol.Id4Watcher(id, watcher) =>
        println(s"id: $id, watcher: $watcher")
        myId = id
        watcherId = watcher

      case Protocol.StartWatching(mode, img) =>
        drawGame = new DrawGame(ctx, canvas, img)
        frameRate = if(mode==2) frameRate1 else frameRate1
        startGame()

      case r@Protocol.SnakeAction(carnieId, keyCode, frame, actionId) =>
        println(s"got $r")
        if (grid.carnieMap.contains(carnieId) && grid.snakes.contains(grid.carnieMap(carnieId))) {
          val id = grid.carnieMap(carnieId)
          if (id == myId) { //收到自己的进行校验是否与预判一致，若不一致则回溯
            //            println(s"recv:$r")
            if (myActionHistory.get(actionId).isEmpty) { //前端没有该项，则加入
              grid.addActionWithFrame(id, keyCode, frame)
              if (frame < grid.frameCount) {
                println(s"recall for my Action,backend:$frame,frontend:${grid.frameCount}")
                recallFrame = grid.findRecallFrame(frame, recallFrame)
              }
            } else {
              if (myActionHistory(actionId)._1 != keyCode || myActionHistory(actionId)._2 != frame) { //若keyCode或则frame不一致则进行回溯
                //                println(s"now:${grid.frameCount}...history:${myActionHistory(actionId)._2}...backend:$frame")
                grid.deleteActionWithFrame(id, myActionHistory(actionId)._2)
                grid.addActionWithFrame(id, keyCode, frame)
                val miniFrame = Math.min(frame, myActionHistory(actionId)._2)
                if (miniFrame < grid.frameCount) {
                  println(s"recall for my Action,backend:$frame,frontend:${grid.frameCount}")
                  recallFrame = grid.findRecallFrame(miniFrame, recallFrame)
                }
              }
              myActionHistory -= actionId
            }
          } else { //收到别人的动作则加入action，若帧号滞后则进行回溯
            grid.addActionWithFrame(id, keyCode, frame)
            if (frame < grid.frameCount) {
              println(s"recall for other Action,backend:$frame,frontend:${grid.frameCount}")
              recallFrame = grid.findRecallFrame(frame, recallFrame)
            }
          }
        }

      case ReStartGame =>
        grid.cleanData()
        drawFunction = FrontProtocol.DrawGameWait
//        audio1.pause()
//        audio1.currentTime = 0
        audioKilled.pause()
        audioKilled.currentTime = 0
        firstCome = true
        myScore = BaseScore(0, 0, 0)
        if (isWin) {
          isWin = false
        }
        isContinue = true
        dom.window.requestAnimationFrame(gameRender())

      case UserLeft(id) =>
        println(s"user $id left:::")
        grid.carnieMap = grid.carnieMap.filterNot(_._2 == id)
        grid.cleanDiedSnakeInfo(id)

      case Protocol.SomeOneWin(winner) =>
        val finalData = grid.getGridData
        drawFunction = FrontProtocol.DrawGameWin(winner, finalData)
        isWin = true
        //        winnerName = winner
        //        winData = finalData
        grid.cleanData()

      case Protocol.WinnerBestScore(score) =>
        maxArea =Constant.shortMax(maxArea, score)

      case Protocol.Ranks(ranks, personalScore, personalRank) =>
        currentRank = ranks
//        maxArea = Constant.shortMax(maxArea, personalScore.area)
        if (grid.getGridData.snakes.exists(_.id == myId) && !isWin && isSynced)
          drawGame.drawRank(myId, grid.getGridData.snakes, currentRank, personalScore, personalRank)

      case data: Protocol.SyncFrame =>
        syncFrame = Some(data)

      case data: Protocol.Data4TotalSync =>
        println(s"===========recv total data")
        //        drawGame.drawField(data.fieldDetails, data.snakes)
        syncGridData = Some(data)
        if (data.fieldDetails.nonEmpty) newFieldInfo = newFieldInfo.filterKeys(_ > data.frameCount)
        //        justSynced = true
        isSynced = true

      case data: Protocol.NewSnakeInfo =>
        grid.historyNewSnake += data.frameCount -> data
        data.snake.foreach { s => grid.carnieMap += s.carnieId -> s.id }
        if (data.frameCount < grid.frameCount + 1) {
          println(s"recall for NewSnakeInfo,backend:${data.frameCount},frontend:${grid.frameCount}")
          recallFrame = grid.findRecallFrame(data.frameCount - 1, recallFrame)
        } else {
          newSnakeInfo += data.frameCount -> data
        }

      case Protocol.UserDeadMsg(frame, deadInfo) =>
        grid.historyDieSnake += frame -> deadInfo.map(_.id)
        deadInfo.filter(_.killerName.nonEmpty).foreach { i =>
          killInfo = Some(i.id, i.name, i.killerName.get)
          barrageDuration = 100
        }
        if (frame < grid.frameCount + 1) {
          println(s"recall for UserDeadMsg,backend:$frame,frontend:${grid.frameCount}")
          recallFrame = grid.findRecallFrame(frame - 1, recallFrame)
        } else {
          deadUser += frame -> deadInfo.map(_.id)
        }


      case x@Protocol.DeadPage(kill, area, playTime) =>
        println(s"recv userDead $x")
        //        grid.cleanSnakeTurnPoint(id)
        myScore = BaseScore(kill, area, playTime)
        maxArea = Constant.shortMax(maxArea, historyRank.find(_.id == myId).map(_.area).getOrElse(0))


      case data: Protocol.NewFieldInfo =>
        if (data.fieldDetails.exists(_.uid == myId))
          audioFinish.play()
        grid.historyFieldInfo += data.frameCount -> data
        if (data.frameCount < grid.frameCount + 1) {
          println(s"recall for NewFieldInfo,backend:${data.frameCount},frontend:${grid.frameCount}")
          recallFrame = grid.findRecallFrame(data.frameCount - 1, recallFrame)
        } else {
          newFieldInfo += data.frameCount -> data
        }

      case x@Protocol.ReceivePingPacket(actionId) =>
        println("got pingPacket.")
        if (pingMap.get(actionId).nonEmpty) {
          PerformanceTool.receivePingPackage(pingMap(actionId))
          pingMap -= actionId
        }

      case x@Protocol.WinData(winnerScore,yourScore) =>
        winningData = x

      case x@_ =>
        println(s"receive unknown msg:$x")
    }
  }

  def spaceKey(): Unit = {
    grid.cleanSnakeTurnPoint(myId)
    grid.actionMap = grid.actionMap.filterNot(_._2.contains(myId))
    drawFunction = FrontProtocol.DrawGameWait
//    audio1.pause()
//    audio1.currentTime = 0
    audioKilled.pause()
    audioKilled.currentTime = 0
    firstCome = true
    if (isWin) isWin = false
    myScore = BaseScore(0, 0, 0)
    isContinue = true
    //                  backBtn.style.display="none"
    //                  rankCanvas.addEventListener("",null)
    dom.window.requestAnimationFrame(gameRender())
  }

  def addBackendInfo(frame: Int) = {
    newFieldInfo.get(frame).foreach { data =>
      grid.addNewFieldInfo(data)
      newFieldInfo -= frame
    }

    deadUser.get(frame).foreach { deadSnake =>
      deadSnake.foreach { sid =>
        grid.cleanDiedSnakeInfo(sid)
      }
      deadUser -= frame
    }

    newSnakeInfo.get(frame).foreach { newSnakes =>
      if (newSnakes.snake.map(_.id).contains(myId) && !firstCome) spaceKey()
      newSnakes.snake.foreach { s => grid.cleanSnakeTurnPoint(s.id) } //清理死前拐点
      grid.snakes ++= newSnakes.snake.map(s => s.id -> s).toMap
      grid.addNewFieldInfo(NewFieldInfo(frame, newSnakes.filedDetails))
      newSnakeInfo -= frame
    }
  }

  def addBackendInfo4Sync(frame: Int): Unit = {
    newFieldInfo -= frame
    deadUser -= frame
    newSnakeInfo.get(frame).foreach { newSnakes =>
      if (newSnakes.snake.map(_.id).contains(myId) && !firstCome) spaceKey()
      newSnakeInfo -= frame
    }
  }

  override def render: Elem = {
    init()
    <div></div>
  }
}
