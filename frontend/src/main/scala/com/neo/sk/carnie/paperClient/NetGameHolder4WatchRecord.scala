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
  var loading = true
  private var killInfo = ("", "", "")
  var lastTime = 0
  var winData: Protocol.Data4TotalSync = grid.getGridData
  var fieldNum = 1
  var snakeNum = 1
  var syncGridData4Replay: scala.Option[Protocol.Data4TotalSync] = None
  var snapshotMap = Map.empty[Long, Snapshot]
  var encloseMap = Map.empty[Long, NewFieldInfo]
  var spaceEvent = Map.empty[Long, SpaceEvent]
  var rankEvent = Map.empty[Long, RankEvent]
  var oldWindowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
  var replayFinish = false
  var gameLoopInterval = -1
  var pingInterval = -1
  var requestAnimationInterval = -1

  private var myScore = BaseScore(0, 0, 0l, 0l)
  private var maxArea: Int = 0
  private var scale = 1.0


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
    println(s"start game======")
    drawGame.drawGameOn()
    gameLoopInterval = dom.window.setInterval(() => gameLoop(), Protocol.frameRate)
    pingInterval = dom.window.setInterval(() => {
      webSocketClient.sendMessage(SendPingPacket(myId, System.currentTimeMillis()).asInstanceOf[UserAction])
    }, 100)
    requestAnimationInterval = dom.window.requestAnimationFrame(gameRender())
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
      drawGame.resetScreen()
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

      if(spaceEvent.contains(grid.frameCount)) {
        replayMessageHandler(spaceEvent(grid.frameCount), grid.frameCount.toInt)
        spaceEvent -= grid.frameCount
      }

      if(encloseMap.contains(grid.frameCount)) {
        encloseMap(grid.frameCount).fieldDetails.map(_.uid).foreach { id =>
          grid.cleanTurnPoint4Reply(id)
        }
//        grid.cleanTurnPoint4Reply(myId)
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
        drawGame.drawGameOff(firstCome, Some(true), false, false)
      } else if (loading) {
        drawGame.drawGameOff(firstCome, Some(false), true, false)
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
              if (scoreFlag) {
                myScore = BaseScore(0, 0, System.currentTimeMillis(), 0l)
//                drawGame.cleanMyScore
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
                if (isContinue) audioKilled.play()
                currentRank.filter(_.id == myId).foreach { score =>
                  myScore = myScore.copy(kill = score.k, area = score.area, endTime = System.currentTimeMillis())
                }
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
    scale = drawGame.drawGrid(uid, data, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(myId),scale)
    drawGame.drawSmallMap(data.snakes.filter(_.id == uid).map(_.header).head, data.snakes.filterNot(_.id == uid))
//    drawGame.drawRank(myId, grid.getGridData.snakes, currentRank)
  }

  private def connectOpenSuccess(event0: Event, order: String) = {
//    startGame()
    event0
  }

  private def connectError(e: Event) = {
    drawGame.drawGameOff(firstCome, None, false, false)
    e
  }

  private def messageHandler(data: GameMessage): Unit = {
    data match {
      case Protocol.Id(id) => myId = id
        println(s"receive ID = $id")

      case Protocol.StartLoading(frame) =>
        println(s"start loading  =========")
        dom.window.clearInterval(gameLoopInterval)
        dom.window.clearInterval(pingInterval)
        dom.window.clearInterval(requestAnimationInterval)
        loading = true
        drawGame.drawGameOff(firstCome, Some(false), loading, false)
        grid.frameCount = frame.toLong
        grid.initSyncGridData(Protocol.Data4TotalSync(grid.frameCount, List(), List(), List(), List()))
        snapshotMap = Map.empty[Long, Snapshot]
        encloseMap = Map.empty[Long, NewFieldInfo]



      case Protocol.StartReplay(firstSnapshotFrame,firstReplayFrame) =>
        println(s"firstSnapshotFrame::$firstSnapshotFrame")
        println(s"firstReplayFrame::$firstReplayFrame")
        for(i <- firstSnapshotFrame until firstReplayFrame)  {
          if (webSocketClient.getWsState) {
             if(snapshotMap.contains(grid.frameCount)) {
              val data = snapshotMap(grid.frameCount)
              grid.initSyncGridData(Protocol.Data4TotalSync(grid.frameCount, data.snakes, data.bodyDetails, data.fieldDetails, data.killHistory))
               if(data.snakes.exists(_.id == myId)) firstCome = false
              //        println(s"state 重置 via Map")
              snapshotMap = snapshotMap.filter(_._1 > grid.frameCount)
            }
            if(spaceEvent.contains(grid.frameCount)) {
              replayMessageHandler(spaceEvent(grid.frameCount), grid.frameCount.toInt)
              spaceEvent -= grid.frameCount
            }
            if(rankEvent.contains(grid.frameCount)) {
              replayMessageHandler(rankEvent(grid.frameCount), grid.frameCount.toInt)
              rankEvent -= grid.frameCount
            }

            if(encloseMap.contains(grid.frameCount)) {
              encloseMap(grid.frameCount).fieldDetails.map(_.uid).foreach { id =>
                grid.cleanTurnPoint4Reply(id)
              }
              grid.addNewFieldInfo(encloseMap(grid.frameCount))
              encloseMap -= grid.frameCount
              //        println(s"圈地 via Map")
            }
              grid.update("f")
          }

        }
        if(!isContinue) firstCome = false
        loading = false
        startGame()
//        grid.frameCount = firstReplayframe.toLong

      case Protocol.InitReplayError(info) =>
        drawGame.drawGameOff(firstCome, Some(false), loading, true)

      case Protocol.SomeOneWin(winner, finalData) =>
        isWin = true
        winnerName = winner
        winData = finalData
        grid.cleanData()

      case x@Protocol.ReplayFinish(_) =>
        println("get message replay finish")
        replayFinish = true

      case Protocol.ReplayFrameData(frameIndex, eventsData, stateData) =>
        println(s"receive replayFrameData,grid.frameCount:${grid.frameCount},frameIndex:$frameIndex")
//        println(s"grid.frameCount:${grid.frameCount}")
//        println(s"frameIndex:$frameIndex")
        if(frameIndex == 0) grid.frameCount = 0
        if(stateData.nonEmpty) {
          stateData.get match {
            case msg: Snapshot =>
//              println(s"snapshot get:$msg")
              replayMessageHandler(msg, frameIndex)
            case Protocol.DecodeError() =>
              println("state decode error")
            case _ =>
          }
        }


        eventsData match {
          case EventData(events) =>
//            println(s"eventsData:$eventsData")
            events.foreach { event =>
              (event, loading) match {
                case (EncloseEvent(_), true) => replayMessageHandler(event, frameIndex)
                case (DirectionEvent(_, _), true) => replayMessageHandler(event, frameIndex)
                case (e@SpaceEvent(_), true) => spaceEvent += (frameIndex.toLong -> e)
                case (e@RankEvent(_), true) => rankEvent += (frameIndex.toLong -> e)
                case (_, false) => replayMessageHandler(event, frameIndex)
                case _ =>
              }
            }

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
//        println(s"receive data: $data")
//        println(s"grid.frameCount:${grid.frameCount}")
//        if (grid.frameCount < frameIndex) {
//          if(joinOrLeftMap.get(frameIndex).nonEmpty) {
//            joinOrLeftMap += ((frameIndex, data :: joinOrLeftMap(frameIndex)))
//          } else {
//            joinOrLeftMap += ((frameIndex, List(data)))
//          }
//        } else {
//          grid.snakes += (id -> snakeInfo.get)
//        }



      case Protocol.LeftEvent(id, name) =>
//        if (grid.frameCount < frameIndex) {
//          if(joinOrLeftMap.get(frameIndex).nonEmpty) {
//            joinOrLeftMap += ((frameIndex, data :: joinOrLeftMap(frameIndex)))
//          } else {
//            joinOrLeftMap += ((frameIndex, List(data)))
//          }
//        } else {
//          grid.snakes -= id
//        }



      case DirectionEvent(id, keyCode) =>
        grid.addActionWithFrame(id, keyCode, frameIndex.toLong)

      case SpaceEvent(id) =>
        if(id == myId) {
          audio1.pause()
          audio1.currentTime = 0
          audioKilled.pause()
          audioKilled.currentTime = 0
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
        println(s"当前帧号：${grid.frameCount}")
        println(s"传输帧号：$frameIndex")
        if(grid.frameCount < frameIndex.toLong) {
          encloseMap += (frameIndex.toLong -> NewFieldInfo(frameIndex.toLong, enclosure))
        } else if(grid.frameCount == frameIndex.toLong){
//          println(s"圈地")
          enclosure.map(_.uid).foreach { id =>
            grid.cleanTurnPoint4Reply(id)
          }
//          grid.cleanTurnPoint4Reply(myId)
          grid.addNewFieldInfo(NewFieldInfo(frameIndex.toLong, enclosure))
        }

      case RankEvent(current) =>
        currentRank = current
        maxArea = Math.max(currentRank.find(_.id == myId).map(_.area).getOrElse(0), maxArea)
        if(grid.getGridData.snakes.exists(_.id == myId) && !isWin) drawGame.drawRank(myId, grid.getGridData.snakes, currentRank)


      case msg@Snapshot(snakes, bodyDetails, fieldDetails, killHistory) =>
        snapshotMap += frameIndex.toLong + 1 -> msg
        if(grid.frameCount >= frameIndex.toLong + 1) { //重置
          syncGridData4Replay = Some(Protocol.Data4TotalSync(frameIndex.toLong + 1, snakes, bodyDetails, fieldDetails, killHistory))
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
