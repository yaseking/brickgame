package org.seekloud.brickgame.paperClient

import java.util.concurrent.atomic.AtomicInteger

import org.seekloud.brickgame.Routes
import org.seekloud.brickgame.common.Constant
import org.scalajs.dom.html.Canvas
import org.seekloud.brickgame.paperClient.Protocol._
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.html.{Document => _, _}
import org.scalajs.dom.raw._
import org.seekloud.brickgame.paperClient.WebSocketProtocol._
import org.seekloud.brickgame.ptcl.SuccessRsp
import org.seekloud.brickgame.util.{Component, Http}
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * User: Tao
  * Date: 9/1/2016
  * Time: 12:45 PM
  */

class NetGameHolder(nickname: String) {

  private var myId = 0

  var grid = new GridOnClient(Point(BorderSize.w, BorderSize.h))

  var isGetKiller = false
  var killerInfo: scala.Option[String] = None
  var firstCome = true
  var isSynced = false
  //  var justSynced = false
  var isWin = false
  var isPlay = true
  //  var winnerName = "unknown"
  var killInfo: scala.Option[(String, String, String, String)] = None
  var barrageDuration = 0

  var syncFrame: scala.Option[Protocol.SyncFrame] = None
  var syncGridData: scala.Option[Protocol.Data4TotalSync2] = None
  var isContinue = true
  var oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
  var drawFunction: FrontProtocol.DrawFunction = FrontProtocol.DrawGameWait
  val delay: Int = 1

  var pingMap = Map.empty[Short, Long] // id, 时间戳
  var isFirstTotalDataSync = false

  var pingId: Short = 0

  var frameTemp = 0

  var pingTimer = -1

  var gameLoopTimer = -1

  var renderId = 0

  private var myScore = BaseScore(0, 0, 0)
  private var maxArea: Short = 0

  private var recallFrame: scala.Option[Int] = None

  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

//  dom.document.addEventListener("visibilitychange", { e: Event =>
//    if (dom.document.visibilityState.asInstanceOf[VisibilityState] != VisibilityState.hidden) {
//      println("has Synced")
//      updateListener()
//    }
//  })

  private var logicFrameTime = System.currentTimeMillis()

  private[this] val drawGame: DrawGame = new DrawGame(ctx, canvas)
  private[this] val webSocketClient: WebSocketClient = new WebSocketClient(connectOpenSuccess, connectError, messageHandler, connectClose)

  def init(): Unit = {
    webSocketClient.setUp(nickname)
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
    gameLoopTimer = dom.window.setInterval(() => gameLoop(), 100) //frameRate=100
    dom.window.requestAnimationFrame(gameRender())
  }


  def gameRender(): Double => Unit = { _ =>
    val curTime = System.currentTimeMillis()
    val offsetTime = curTime - logicFrameTime
    draw(offsetTime)
    if (isContinue)
      renderId = dom.window.requestAnimationFrame(gameRender())
  }


  def gameLoop(): Unit = {
    logicFrameTime = System.currentTimeMillis()
    if ((oldWindowBoundary.x != dom.window.innerWidth.toFloat) || (oldWindowBoundary.y != dom.window.innerHeight.toFloat)) {
      drawGame.resetScreen()
      oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
    }

    val firstPart = System.currentTimeMillis() - logicFrameTime
    var secondPart = 0l
    var thirdPart = 0l
    var forthPart = 0l
    var detailPart = 0l

//    var isAlreadySendSync = false

    if (webSocketClient.getWsState) {
      recallFrame match {
        case Some(-1) => //无法回溯，请求全局变量
          println("!!!!!!!!:NeedToSync")
          webSocketClient.sendMessage(NeedToSync.asInstanceOf[UserAction])
//          isAlreadySendSync = true
          recallFrame = None

        case Some(frame) =>
          grid.setGridInGivenFrame(frame)
          recallFrame = None

        case None =>
      }

      secondPart = System.currentTimeMillis() - logicFrameTime - firstPart

      if (syncGridData.nonEmpty) { //全量数据
        println(s"!!!!!!!!data sync:grid.frame${grid.frameCount}, syncFrame: ${syncGridData.get.frameCount}")
        grid.initSyncGridData2(syncGridData.get)//初始化数据这里需要改变
        println(s"init finish: ${grid.frameCount}")
        syncGridData = None
      } else {
        grid.update
      }

      thirdPart = System.currentTimeMillis() - logicFrameTime - secondPart

      if (!isWin) {
        val startTime = System.currentTimeMillis()
        val gridData = grid.players //data直接用players就可以了
        detailPart = System.currentTimeMillis() - startTime
        drawFunction = gridData.find(_._1 == myId) match {
          case Some(_) =>
            if (firstCome) firstCome = false
            FrontProtocol.DrawBaseGame

          case None =>
            //gameOver，胜利消息从后台传送
            FrontProtocol.DrawGameDie(killerInfo)

          case _ =>
            FrontProtocol.DrawGameWait
        }
      }
      forthPart = System.currentTimeMillis() - logicFrameTime - thirdPart
    } else {
      drawFunction = FrontProtocol.DrawGameOff//断开连接
    }
    val dealTime = System.currentTimeMillis() - logicFrameTime
    if (dealTime > 50)
      println(s"logicFrame deal time:$dealTime;first:$firstPart;second:$secondPart;third:$thirdPart;forthpart:$forthPart;detailPart:$detailPart")
  }

  def draw(offsetTime: Long): Unit = {
    drawFunction match {
      case FrontProtocol.DrawGameWait =>
        drawGame.drawGameWait()

      case FrontProtocol.DrawGameOff =>
        drawGame.drawGameOff(firstCome, None, false, false)//断开连接

      case FrontProtocol.DrawBaseGame =>
        //        println(s"draw---DrawBaseGame!! snakes:${data.snakes.map(_.id)}")
        drawGameImage(myId, offsetTime)

      case FrontProtocol.DrawGameDie(killerName, data) => //
        if (data.nonEmpty) drawGameImage(myId, offsetTime)
        drawGame.drawGameDie(killerName, myScore, maxArea)
        killInfo = None
//        isContinue = false

      case _ =>
    }
  }


  def drawGameImage(uid: Int, offsetTime: Long): Unit = {
    if(grid.players.exists(_._1 == myId)) {
      drawGame.draw(myId, grid.players, offsetTime)
    }
  }

  private def connectOpenSuccess(event0: Event) = {
    startGame()
    canvas.focus()
    canvas.onkeydown = { e: dom.KeyboardEvent => {
      if (Constant.watchKeys.contains(e.keyCode)) {
        val frame = grid.frameCount + delay
        e.keyCode match {
          case KeyCode.Space =>
            drawFunction match {
              case FrontProtocol.DrawBaseGame =>
                val msg: Protocol.UserAction = InitAction(myId)
                webSocketClient.sendMessage(msg)
              case FrontProtocol.DrawGameDie(_, _) =>
                println("onkeydown:Space")
                isGetKiller = false
                killerInfo = None
                val msg: Protocol.UserAction = PressSpace
                webSocketClient.sendMessage(msg)
              case FrontProtocol.DrawGameWin(_, _) =>
                println("onkeydown:Space")
                val msg: Protocol.UserAction = PressSpace
                webSocketClient.sendMessage(msg)

              case _ =>
            }


          case _ =>
            drawFunction match {
              case FrontProtocol.DrawBaseGame => //前端发送命令
                val newKeyCode = e.keyCode.toByte
                grid.addActionWithFrame(myId, newKeyCode, frame)
                val msg: Protocol.UserAction = Key(newKeyCode, frame)
                webSocketClient.sendMessage(msg)
              case _ =>
            }
        }
        e.preventDefault()
      }

    }

      event0
    }
  }

  private def connectError(e: Event) = {
    drawGame.drawGameOff(firstCome, None, false, false)
    e
  }

  private def connectClose(e: Event, s: Boolean) = {
    if(s)
      drawGame.drawGameOff(firstCome, None, false, false)
    else
      drawGame.drawServerShutDown()
    e
  }

  private def messageHandler(data: GameMessage): Unit = {
    data match {
      case Protocol.Id(id) =>
        myId = id

      case r@Protocol.StartGame =>
        println(s"got msg: $r")
        grid.initAction(myId)

      case r@Protocol.SnakeAction(id, keyCode, frame) =>
        println(s"got msg: $r")
        if(grid.players.contains(id)) {
          grid.addActionWithFrame(id, keyCode, frame)
          if(frame < grid.frameCount) {
            println(s"frontendFrame: ${grid.frameCount}, backendFrame: $frame")
            recallFrame = grid.findRecallFrame(frame, recallFrame)
          }
        }


      case data: Protocol.Data4TotalSync2 =>
        println(s"===========recv total data")
        if (!isFirstTotalDataSync) {
          grid.initSyncGridData2(data) //立刻执行，不再等到逻辑帧
          isFirstTotalDataSync = true
        } else {
          syncGridData = Some(data)
          isSynced = true
        }

      case x@Protocol.DeadPage(kill, area, playTime) =>
        println(s"recv userDead $x")
        myScore = BaseScore(kill, area, playTime)
        maxArea = Constant.shortMax(maxArea, area)


      case x@Protocol.ReceivePingPacket(recvPingId) =>
        val currentTime = System.currentTimeMillis()
        if (pingMap.get(recvPingId).nonEmpty) {
          PerformanceTool.receivePingPackage(pingMap(recvPingId), currentTime)
          pingMap -= recvPingId
        }

      case x@_ =>
        println(s"receive unknown msg:$x")
    }
  }

  def spaceKey(): Unit = {
    killInfo = None
    grid.actionMap = grid.actionMap.filterNot(_._2.contains(myId))
    drawFunction = FrontProtocol.DrawGameWait
    //    audio1.pause()
    //    audio1.currentTime = 0
    firstCome = true
    isSynced = false
    isGetKiller = false
    killerInfo = None
    if (isWin) isWin = false
    myScore = BaseScore(0, 0, 0)
    isContinue = true
    //                  backBtn.style.display="none"
    //                  rankCanvas.addEventListener("",null)
    dom.window.requestAnimationFrame(gameRender())
  }

  def addSession(id: String) = {
    val url = Routes.Carnie.addSession+s"?id=$id"
    Http.getAndParse[SuccessRsp](url).map {
      case Right(rsp) =>
        if (rsp.errCode == 0) {
          println("Success to addSession!")
        }
        else {
          println(s"Some errors happened in addSession: ${rsp.msg}")
        }

      case Left(e) =>
        println(s"Some errors happened in addSession: $e")
    }

  }
}
