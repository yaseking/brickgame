package com.neo.sk.carnie.paperClient

import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicInteger

import com.neo.sk.carnie.common.Constant
import org.scalajs.dom.html.Canvas
import com.neo.sk.carnie.paperClient.Protocol._
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.html.{Document => _, _}
import org.scalajs.dom.raw._
import com.neo.sk.carnie.paperClient.WebSocketProtocol._
import com.neo.sk.carnie.util.Component

import scala.xml.Elem

/**
  * User: Tao
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
  var isPlay = false
  //  var winnerName = "unknown"
  var killInfo: scala.Option[(String, String, String)] = None
  var barrageDuration = 0
  //  var winData: Protocol.Data4TotalSync = grid.getGridData
  var deadUser = Map.empty[Int, List[String]] //frame, userId
  var newFieldInfo = Map.empty[Int, Protocol.NewFieldInfo] //[frame, newFieldInfo)
  var newSnakeInfo = Map.empty[Int, NewSnakeInfo]

  var syncFrame: scala.Option[Protocol.SyncFrame] = None
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None
  var isContinue = true
  var oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
  var drawFunction: FrontProtocol.DrawFunction = FrontProtocol.DrawGameWait
  val delay: Int = if (mode == 2) 2 else 1

  var pingMap = Map.empty[Short, Long] // id, 时间戳

  var pingId: Short = 0

  private var myScore = BaseScore(0, 0, 0)
  private var maxArea: Short = 0
  private var winningData = WinData(0, Some(0))

  private var recallFrame: scala.Option[Int] = None

  val idGenerator = new AtomicInteger(1)
  private var myActionHistory = Map[Int, (Int, Int)]() //(actionId, (keyCode, frameCount))
  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  //  private[this] val audio1 = dom.document.getElementById("audio").asInstanceOf[HTMLAudioElement]
  private[this] val audioFinish = dom.document.getElementById("audioFinish").asInstanceOf[HTMLAudioElement]
  private[this] val audioKill = dom.document.getElementById("audioKill").asInstanceOf[HTMLAudioElement]
  private[this] val audioKilled = dom.document.getElementById("audioKilled").asInstanceOf[HTMLAudioElement]
  //  private[this] val bgm4 = dom.document.getElementById("bgm4").asInstanceOf[HTMLAudioElement]
  //  private[this] val bgmList = List(bgm4)
  //  private val bgmAmount = bgmList.length
  //  private var BGM = dom.document.getElementById("bgm4").asInstanceOf[HTMLAudioElement]
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
    webSocketClient.sendMessage(NeedToSync.asInstanceOf[UserAction])
  }

  def getRandom(s: Int): Int = {
    val rnd = new scala.util.Random
    rnd.nextInt(s)
  }

  def startGame(): Unit = {
    drawGame.drawGameOn()
    //    BGM = bgmList(getRandom(bgmAmount))
    //    BGM.play()
    //    isPlay = true
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
    //    println(s"snakes:::::::::::::${grid.snakes.keys}")
    logicFrameTime = System.currentTimeMillis()
    if ((oldWindowBoundary.x != dom.window.innerWidth.toFloat) || (oldWindowBoundary.y != dom.window.innerHeight.toFloat)) {
      drawGame.resetScreen()
      oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
      if (!isContinue) {
        if (isWin) {
          val winInfo = drawFunction.asInstanceOf[FrontProtocol.DrawGameWin]
          drawGame.drawGameWin(myId, winInfo.winnerName, winInfo.winData, winningData)
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
            grid.update("f")
            addBackendInfo(grid.frameCount)
          }
        } else {
          webSocketClient.sendMessage(NeedToSync.asInstanceOf[UserAction])
        }
        syncFrame = None
      } else {
        grid.update("f")
        addBackendInfo(grid.frameCount)
      }

      if (!isWin) {
        val gridData = grid.getGridData
        drawFunction = gridData.snakes.find(_.id == myId) match {
          case Some(_) =>
            //            println(s"draw base game")
            if (firstCome) firstCome = false
            //            if (!isPlay) {
            ////              println("you can't play")
            //              BGM = bgmList(getRandom(bgmAmount))
            //              BGM.play()
            //              isPlay = true
            //            }
            //            if (BGM.paused) {
            //              isPlay = false
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
    //    println(s"drawFunction:::$drawFunction")
    drawFunction match {
      case FrontProtocol.DrawGameWait =>
        //        println(s"drawFunction::: drawGameWait")
        drawGame.drawGameWait()

      case FrontProtocol.DrawGameOff =>
        //        println(s"drawFunction::: drawGameOff")
        //        if(isPlay){
        //          BGM.pause()
        //          BGM.currentTime = 0
        //          isPlay = false
        //        }
        drawGame.drawGameOff(firstCome, None, false, false)

      case FrontProtocol.DrawGameWin(winner, winData) =>
        //        if(isPlay){
        //          BGM.pause()
        //          BGM.currentTime = 0
        //          isPlay = false
        //        }
        drawGame.drawGameWin(myId, winner, winData, winningData)
        //        audio1.play()
        isContinue = false

      case FrontProtocol.DrawBaseGame(data) =>
        //        println(s"draw---DrawBaseGame!! snakes:${data.snakes.map(_.id)}")
        drawGameImage(myId, data, offsetTime)
        if (killInfo.nonEmpty) {
          val killBaseInfo = killInfo.get
          if (killBaseInfo._3 == myId) audioKill.play()
          drawGame.drawBarrage(killBaseInfo._2, killBaseInfo._3)
          barrageDuration -= 1
          if (barrageDuration == 0) killInfo = None
        }

      case FrontProtocol.DrawGameDie(killerName) =>
        //        println(s"drawFunction::: drawGameDie")
        //        if(isPlay){
        //          BGM.pause()
        //          BGM.currentTime = 0
        //          isPlay = false
        //        }
        if (isContinue) audioKilled.play()
        drawGame.drawGameDie(killerName, myScore, maxArea)
        killInfo = None
        isContinue = false
    }
  }


  def drawGameImage(uid: String, data: Data4TotalSync, offsetTime: Long): Unit = {
    drawGame.drawGrid(uid, data, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(myId), frameRate = frameRate, newFieldInfo = newFieldInfo.get(grid.frameCount + 1))
    drawGame.drawSmallMap(data.snakes.filter(_.id == uid).map(_.header).head, data.snakes.filterNot(_.id == uid))
  }

  private def connectOpenSuccess(event0: Event, order: String) = {
    if (order == "playGame") {
      startGame()
      rankCanvas.focus()
      rankCanvas.onkeydown = { e: dom.KeyboardEvent => {
        if (Constant.watchKeys.contains(e.keyCode)) {
          val frame = grid.frameCount + delay
          e.keyCode match {
            case KeyCode.Space =>
              drawFunction match {
                case FrontProtocol.DrawBaseGame(_) =>
                case _ =>
                  println("onkeydown:Space")
                  val msg: Protocol.UserAction = PressSpace
                  webSocketClient.sendMessage(msg)
              }


            case _ =>
              drawFunction match {
                case FrontProtocol.DrawBaseGame(_) =>
                  val actionFrame = grid.getUserMaxActionFrame(myId, frame)
                  if (actionFrame < frame + maxContainableAction) {
                    val actionId = idGenerator.getAndIncrement()
                    val newKeyCode =
                      if (mode == 1) {
                        val code = e.keyCode match {
                          case KeyCode.Left => KeyCode.Right
                          case KeyCode.Right => KeyCode.Left
                          case KeyCode.Down => KeyCode.Up
                          case KeyCode.Up => KeyCode.Down
                          case _ => KeyCode.Space
                        }
                        code.toByte
                      } else e.keyCode.toByte
                    //                    println(s"onkeydown：${Key(myId, newKeyCode, actionFrame, actionId)}")
                    grid.addActionWithFrame(myId, newKeyCode, actionFrame)
                    myActionHistory += actionId -> (newKeyCode, actionFrame)
                    val msg: Protocol.UserAction = Key(newKeyCode, actionFrame, actionId)
                    webSocketClient.sendMessage(msg)
                  }
                case _ =>
              }
          }
          e.preventDefault()
        }
      }
      }

      rankCanvas.onmouseup = { e: dom.MouseEvent =>
        val myField = grid.grid.filter(_._2 == Field(myId))
        val myBody = grid.snakeTurnPoints.getOrElse(myId, Nil)


        val myGroupField = FieldByColumn(myId, myField.keys.groupBy(_.y).map { case (y, target) =>
          (y.toShort, Tool.findContinuous(target.map(_.x.toShort).toArray.sorted)) //read
        }.toList.groupBy(_._2).map { case (r, target) =>
          ScanByColumn(Tool.findContinuous(target.map(_._1).toArray.sorted), r)
        }.toList)


        println(s"=======myField:$myGroupField, myBody:$myBody")

        //              FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
        //                ScanByColumn(y.toInt, Tool.findContinuous(target.map(_.x.toInt).toArray.sorted))//read
        //              }.toList)
        //        }
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

      case r@Protocol.SnakeAction(carnieId, keyCode, frame, actionId, sendTime) =>
//        println(s"recv time:$sendTime..now:${System.currentTimeMillis()}")
        if (grid.snakes.contains(grid.carnieMap.getOrElse(carnieId, ""))) {
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
//            println(s"addActionWithFrame time:${System.currentTimeMillis() - sendTime}")
            if (frame < grid.frameCount) {
              println(s"recall for other Action,backend:$frame,frontend:${grid.frameCount}")
              recallFrame = grid.findRecallFrame(frame, recallFrame)
            }
          }
        }

      case UserLeft(id) =>
        println(s"user $id left:::")
        grid.carnieMap = grid.carnieMap.filterNot(_._2 == id)
        grid.cleanDiedSnakeInfo(id)

      case Protocol.SomeOneWin(winner) =>
        drawFunction = FrontProtocol.DrawGameWin(winner, grid.getGridData)
        isWin = true
        //        winnerName = winner
        //        winData = finalData
        grid.cleanData()

      case Protocol.WinnerBestScore(score) =>
        maxArea = Constant.shortMax(maxArea, score)

      case Protocol.Ranks(ranks, personalScore, personalRank) =>
        currentRank = ranks
        if (grid.getGridData.snakes.exists(_.id == myId) && !isWin && isSynced)
          drawGame.drawRank(myId, grid.getGridData.snakes, currentRank, personalScore, personalRank)

      case data: Protocol.Data4TotalSync =>
        println(s"===========recv total data")
        //        drawGame.drawField(data.fieldDetails, data.snakes)
        syncGridData = Some(data)
        if (data.fieldDetails.nonEmpty) newFieldInfo = newFieldInfo.filterKeys(_ > data.frameCount)
        //        justSynced = true
        isSynced = true

      case data: Protocol.SyncFrame =>
        syncFrame = Some(data)
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

      case x@Protocol.DeadPage(kill, area, playTime) =>
        println(s"recv userDead $x")
        myScore = BaseScore(kill, area, playTime)
        maxArea = Constant.shortMax(maxArea, area)

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
        if (pingMap.get(actionId).nonEmpty) {
          PerformanceTool.receivePingPackage(pingMap(actionId))
          pingMap -= actionId
        }

      case x@Protocol.WinData(_, _) =>
        println(s"receive winningData msg:$x")
        winningData = x

      case x@_ =>
        println(s"receive unknown msg:$x")
    }
  }

  def spaceKey(): Unit = {
    grid.cleanSnakeTurnPoint(myId)
    killInfo = None
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
        if (grid.snakes.contains(sid)) {
          println(s"snake dead in backend:$sid")
          grid.cleanDiedSnakeInfo(sid)
        }
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

  private var myGroupField: FieldByColumn = FieldByColumn(myId, Nil)

  private def getMyField(): Unit = {
    val myField = grid.grid.filter(_._2 == Field(myId))
    val myBody = grid.snakeTurnPoints.getOrElse(myId, Nil)

    //        newField = myField.map { f =>
    myGroupField = FieldByColumn(myId, myField.keys.groupBy(_.y).map { case (y, target) =>
      (y.toShort, Tool.findContinuous(target.map(_.x.toShort).toArray.sorted)) //read
    }.toList.groupBy(_._2).map { case (r, target) =>
      ScanByColumn(Tool.findContinuous(target.map(_._1).toArray.sorted), r)
    }.toList)


    //    println(s"=======myField:$myGroupField, myBody:$myBody")
  }

  override def render: Elem = {
    init()
    <div></div>
  }
}
