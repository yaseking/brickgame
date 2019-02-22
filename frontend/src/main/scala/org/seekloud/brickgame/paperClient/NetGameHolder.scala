package org.seekloud.brickgame.paperClient

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

  var grid = new GridOnClient()

  var firstCome = true
  var isWin = false
  var hasStarted = false
//  var barrageDuration = 0

  var syncGridData: scala.Option[Protocol.Data4TotalSync2] = None
  var isContinue = true
  var oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
  var drawFunction: FrontProtocol.DrawFunction = FrontProtocol.DrawGameWait
  val delay: Int = 1

  var pingMap = Map.empty[Short, Long] // id, 时间戳
  var isFirstTotalDataSync = false

//  var pingId: Short = 0

//  var frameTemp = 0

//  var pingTimer = -1

  var gameLoopTimer = -1

  var renderId = 0


  private var recallFrame: scala.Option[Int] = None

  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  dom.document.addEventListener("visibilitychange", { e: Event =>
    if (dom.document.visibilityState.asInstanceOf[VisibilityState] != VisibilityState.hidden) {
      println("has Synced")
      updateListener()
    }
  })

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


    if (webSocketClient.getWsState) {
      recallFrame match {
        case Some(-1) => //无法回溯，请求全局变量
          println("!!!!!!!!:NeedToSync")
          webSocketClient.sendMessage(NeedToSync.asInstanceOf[UserAction])
          recallFrame = None

        case Some(frame) =>
          grid.setGridInGivenFrame(frame)
          recallFrame = None

        case None =>
      }


      if (syncGridData.nonEmpty) { //全量数据
        println(s"!!!!!!!!data sync:grid.frame${grid.frameCount}, syncFrame: ${syncGridData.get.frameCount}")
        grid.initSyncGridData2(syncGridData.get)//初始化数据这里需要改变
        println(s"init finish: ${grid.frameCount}")
        syncGridData = None
      } else {
        grid.update
      }


      val gridData = grid.players
      gridData.find(_._1 == myId) match {
        case Some(_) =>
          if (firstCome) firstCome = false
          drawFunction = FrontProtocol.DrawBaseGame

        case None if isWin =>
          //gameOver，胜利消息从后台传送
//          FrontProtocol.DrawGameWin("")

        case _ => //多余
          FrontProtocol.DrawGameWait //匹配中
      }
    } else {
      drawFunction = FrontProtocol.DrawGameOff//断开连接
    }
  }

  def draw(offsetTime: Long): Unit = {
    drawFunction match {
      case FrontProtocol.DrawGameWait =>
        drawGame.drawGameWait()

      case FrontProtocol.DrawGameOff =>
        drawGame.drawGameOff(firstCome)//断开连接

      case FrontProtocol.DrawBaseGame =>
        drawGameImage(myId, offsetTime)
        drawGame.drawGameDuration(grid.gameDuration)

      case FrontProtocol.DrawGameWin(name) => //
//        if (data.nonEmpty) drawGameImage(myId, offsetTime)
        drawGame.drawGameWin(name)

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
                if(!hasStarted) {
                  val msg: Protocol.UserAction = InitAction
                  webSocketClient.sendMessage(msg)
                  hasStarted = true
                }
              case FrontProtocol.DrawGameWin(_) =>
                println("onkeydown:Space")
                val msg: Protocol.UserAction = PressSpace
                webSocketClient.sendMessage(msg)
                drawFunction = FrontProtocol.DrawGameWait

              case _ =>
            }


          case _ =>
            drawFunction match {
              case FrontProtocol.DrawBaseGame => //前端发送命令
                val newKeyCode = e.keyCode.toByte
//                grid.addActionWithFrame(myId, newKeyCode, frame)
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
    drawGame.drawGameOff(firstCome)
    e
  }

  private def connectClose(e: Event, serverState: Boolean) = {
    if(serverState)
      drawGame.drawGameOff(firstCome)
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
//        println(s"got msg: $r")
        if(grid.players.contains(id)) {
          grid.addActionWithFrame(id, keyCode, frame)
          if(frame < grid.frameCount) {
            println(s"frontendFrame: ${grid.frameCount}, backendFrame: $frame")
            recallFrame = grid.findRecallFrame(frame, recallFrame)
          }
        }

      case ReStartGame =>
        println("got msg: ReStartGame.")
        spaceKey()

      case m@WinPage(id) =>
        println(s"got msg: $m")
        val winnerName = grid.players(id).name
        grid.players = Map.empty[Int, PlayerDt]
        isWin = true
        drawFunction = FrontProtocol.DrawGameWin(winnerName)

      case m@Protocol.UpdatePlayerInfo(info) =>
        grid.players += info.id -> info

      case m@Reborn(id) =>
        println(s"got msg: $m")
        if(myId == id)
          hasStarted = false
        val playerInfo = grid.players(id)
        val newField = grid.reBornPlank(id)
        grid.players += id -> playerInfo.copy(location = plankOri, velocityX = 0, velocityY = 0, ballLocation = Point(10, 29), field = newField, state = 0)

      case m@ChangeState(id, newState) =>
        println(s"got msg: $m")
        val playerInfo = grid.players(id)
        grid.players += id -> playerInfo.copy(state = newState)

      case data: Protocol.Data4TotalSync2 =>
        println(s"===========recv total data")
        if (!isFirstTotalDataSync) {
          grid.initSyncGridData2(data) //立刻执行，不再等到逻辑帧
          isFirstTotalDataSync = true
        } else {
          syncGridData = Some(data)
        }

      case Protocol.DeadPage=>
        println("recv userDead")
        isWin=true


      case Protocol.GameDuration(time) =>
        grid.gameDuration = time
//        drawGame.drawGameDuration(time)

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
//    grid.actionMap = grid.actionMap.filterNot(_._2.contains(myId))
//    drawFunction = FrontProtocol.DrawGameWait
    hasStarted = false
    firstCome = true
    if (isWin) isWin = false
    isContinue = true
//    dom.window.requestAnimationFrame(gameRender())
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
