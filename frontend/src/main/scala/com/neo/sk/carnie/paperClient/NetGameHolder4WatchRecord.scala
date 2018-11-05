package com.neo.sk.carnie.paperClient

import java.util.concurrent.atomic.AtomicInteger

import com.neo.sk.carnie.paperClient.Protocol._
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.html.{Canvas, Document => _}
import org.scalajs.dom.raw._
import com.neo.sk.carnie.paperClient.WebSocketProtocol._

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 12:45 PM
  */
//@JSExportTopLevel("paperClient.NetGameHolder")
class NetGameHolder4WatchRecord(webSocketPara: WatchRecordPara){

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
  var syncGridData4Replay: scala.Option[Protocol.Data4TotalSync] = None
  var play = true
  var snapshotMap = Map.empty[Long, Snapshot]
  var encloseMap = Map.empty[Long, NewFieldInfo]
  var oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
  var joinOrLeftMap = Map.empty[Long, List[GameEvent]]

  var replayFinish = false

//  private[this] val nameField = dom.document.getElementById("name").asInstanceOf[HTMLInputElement]
//  private[this] val joinButton = dom.document.getElementById("join").asInstanceOf[HTMLButtonElement]
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


  def init(): Unit = {
    webSocketClient.setUp("watchRecord", webSocketPara)
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
    if((oldWindowBoundary.x != dom.window.innerWidth.toFloat) || (oldWindowBoundary.y != dom.window.innerHeight.toFloat)) {
      drawGame.reSetScreen()
      oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
    }

    if (webSocketClient.getWsState) {
      if (syncGridData4Replay.nonEmpty) {
        grid.initSyncGridData(syncGridData4Replay.get)
        syncGridData4Replay = None
        justSynced = false
      } else if(snapshotMap.contains(grid.frameCount)) {
        val data = snapshotMap(grid.frameCount)
        grid.initSyncGridData(Protocol.Data4TotalSync(grid.frameCount, data.snakes, data.bodyDetails, data.fieldDetails, data.killHistory))
//        println(s"state 重置 via Map")
        snapshotMap = snapshotMap.filter(_._1 > grid.frameCount - 150)
      }
      if(joinOrLeftMap.contains(grid.frameCount)) {
        joinOrLeftMap.filter(_._1 == grid.frameCount).head._2.foreach {
          case JoinEvent(id, Some(snakeInfo)) =>
            println(s"receive joinEvent:::::$snakeInfo")
            grid.snakes += ((id, snakeInfo))
          case LeftEvent(id, name) => grid.snakes -= id
          case _ =>
        }
        joinOrLeftMap -= grid.frameCount
      }
      if(encloseMap.contains(grid.frameCount)) {
        grid.addNewFieldInfo(encloseMap(grid.frameCount))
//        println(s"圈地 via Map")
      }

      if (!justSynced) { //前端更新
        grid.update("f")

      }
    }
  }


  def draw(offsetTime: Long): Unit = {
    if (webSocketClient.getWsState) {
      if(replayFinish) {
        drawGame.drawGameOff(firstCome, Some(true))
      } else {
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
      }
    } else {
      drawGame.drawGameOff(firstCome, None)
    }
  }

  def drawGameImage(uid: String, data: Data4TotalSync, offsetTime: Long): Unit = {
    drawGame.drawGrid(uid, data, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(myId))
    drawGame.drawSmallMap(data.snakes.filter(_.id == uid).map(_.header).head, data.snakes.filterNot(_.id == uid))
    drawGame.drawRank(myId, grid.getGridData.snakes, currentRank)
  }

  private def connectOpenSuccess(event0: Event, order: String) = {
    startGame()
    event0
  }

  private def connectError(e: Event) = {
    drawGame.drawGameOff(firstCome, None)
    e
  }

  private def messageHandler(data: GameMessage): Unit = {
    data match {
      case Protocol.Id(id) => myId = id
        println(s"receive ID = $id")

      case Protocol.SomeOneWin(winner, finalData) =>
        isWin = true
        winnerName = winner
        winData = finalData
        grid.cleanData()

      case x@Protocol.ReplayFinish(_) =>
        println("get message replay finish")
        replayFinish = true

      case Protocol.ReplayFrameData(frameIndex, eventsData, stateData) =>
        println(s"receive replayFrameData")
        if(stateData.nonEmpty) {
          stateData.get match {
            case msg: Snapshot =>
              println(s"snapshot get")
              replayMessageHandler(msg, frameIndex)
            case Protocol.DecodeError() =>
//              println("state decode error")
            case _ =>
          }
        }
        eventsData match {
          case EventData(events) =>
            println(s"eventsData:$eventsData")
            events.foreach (event => replayMessageHandler(event, frameIndex))
          case Protocol.DecodeError() =>
          //            println("events decode error")
          case _ =>
        }

      case x@_ =>
        println(s"receive unknown msg:$x")
    }
  }



  private def replayMessageHandler(data: GameEvent, frameIndex: Int): Unit = {
    data match {
      case Protocol.JoinEvent(id, snakeInfo) =>
        println(s"receive data: $data")
        if (grid.frameCount < frameIndex) {
          if(joinOrLeftMap.get(frameIndex).nonEmpty) {
            joinOrLeftMap += ((frameIndex, data :: joinOrLeftMap(frameIndex)))
          } else {
            joinOrLeftMap += ((frameIndex, List(data)))
          }
        } else {
          grid.snakes += (id -> snakeInfo.get)
        }



      case Protocol.LeftEvent(id, name) =>
        if (grid.frameCount < frameIndex) {
          if(joinOrLeftMap.get(frameIndex).nonEmpty) {
            joinOrLeftMap += ((frameIndex, data :: joinOrLeftMap(frameIndex)))
          } else {
            joinOrLeftMap += ((frameIndex, List(data)))
          }
        } else {
          grid.snakes -= id
        }



      case DirectionEvent(id, keyCode) =>
        grid.addActionWithFrame(id, keyCode, frameIndex.toLong)

      case SpaceEvent(id) =>
        if(id == myId) {
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

//      case Protocol.SomeOneKilled(killedId, killedName, killerName) =>
//        killInfo = (killedId, killedName, killerName)
//        lastTime = 100

      case EncloseEvent(enclosure) =>
//        println(s"got enclose event")
//        println(s"当前帧号：${grid.frameCount}")
//        println(s"传输帧号：$frameIndex")
        if(grid.frameCount < frameIndex.toLong) {
          encloseMap += (frameIndex.toLong -> NewFieldInfo(frameIndex.toLong, enclosure))
        } else if(grid.frameCount == frameIndex.toLong){
//          println(s"圈地")
          grid.addNewFieldInfo(NewFieldInfo(frameIndex.toLong, enclosure))
        }
      case RankEvent(current) =>
        currentRank = current
        if (grid.getGridData.snakes.exists(_.id == myId))
          drawGame.drawRank(myId, grid.getGridData.snakes, current)

      case msg@Snapshot(snakes, bodyDetails, fieldDetails, killHistory) =>

        snapshotMap += frameIndex.toLong -> msg
        if(grid.frameCount >= frameIndex.toLong) { //重置
          syncGridData4Replay = Some(Protocol.Data4TotalSync(frameIndex.toLong, snakes, bodyDetails, fieldDetails, killHistory))
          justSynced = true
        }

      case _ =>
    }
  }

  import org.seekloud.byteobject.ByteObject._
  import org.seekloud.byteobject.MiddleBufferInJs

  import scala.scalajs.js.typedarray.ArrayBuffer

  private def replayEventDecode(a:ArrayBuffer): GameEvent={
    val middleDataInJs = new MiddleBufferInJs(a)
    if (a.byteLength > 0) {
      bytesDecode[List[GameEvent]](middleDataInJs) match {
        case Right(r) =>
          Protocol.EventData(r)
        case Left(e) =>
          Protocol.DecodeError()
      }
    }else{
      Protocol.DecodeError()
    }
  }

  private def replayStateDecode(a:ArrayBuffer): GameEvent={
    val middleDataInJs = new MiddleBufferInJs(a)
    bytesDecode[Snapshot](middleDataInJs) match {
      case Right(r) =>
        r
      case Left(e) =>
        println(e.message)
        Protocol.DecodeError()
    }
  }


}
