package com.neo.sk.carnie.controller

import java.awt.event.KeyEvent

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.actor.BotActor
import com.neo.sk.carnie.paperClient._
import com.neo.sk.carnie.paperClient.Protocol._
import javafx.animation.{Animation, AnimationTimer, KeyFrame, Timeline}
import javafx.util.Duration
import org.slf4j.LoggerFactory
import akka.actor.typed.scaladsl.adapter._
import com.neo.sk.carnie.actor.BotActor.{Dead, Observation}
import com.neo.sk.carnie.common.Context
import com.neo.sk.carnie.paperClient.ClientProtocol.PlayerInfoInClient
import com.neo.sk.carnie.scene.{GameScene, LayeredGameScene}
import org.seekloud.esheepapi.pb.observations.{ImgData, LayeredObservation}

/**
  * Created by dry on 2018/12/4.
  **/
class BotController(player: PlayerInfoInClient,
                    stageCtx: Context,
                    layeredGameScene: LayeredGameScene,
                    mode: Int =0,
                    ) {

  private val botActor = Boot.system.spawn(BotActor.create(this, player), "botActor")

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  private var drawFunction: FrontProtocol.DrawFunction = FrontProtocol.DrawGameWait
  private var recallFrame: scala.Option[Int] = None

//  var allImageData:List[Array[Int]] = List.empty
  var currentRank = List.empty[Score]
  private val frameRate = 150
  var grid = new GridOnClient(Point(Boundary.w, Boundary.h))
  private val timeline = new Timeline()
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None
  var myCurrentRank = Score(player.id, player.name, 0)
  private var myActions = Map.empty[Int,Int]
  private var logicFrameTime = System.currentTimeMillis()
  var syncFrame: scala.Option[Protocol.SyncFrame] = None

  def startGameLoop(): Unit = { //渲染帧
    layeredGameScene.cleanGameWait(stageCtx.getStage.getWidth.toInt, stageCtx.getStage.getHeight.toInt)
    logicFrameTime = System.currentTimeMillis()
    timeline.setCycleCount(Animation.INDEFINITE)
    val keyFrame = new KeyFrame(Duration.millis(frameRate), { _ =>
      logicLoop()
    })
    timeline.getKeyFrames.add(keyFrame)
    timeline.play()
  }

  private def logicLoop(): Unit = { //逻辑帧
//    allImageData = getAllImage
    logicFrameTime = System.currentTimeMillis()

    recallFrame match {
      case Some(-1) =>
        println("!!!!!!!!:NeedToSync")
        botActor ! BotActor.MsgToService(NeedToSync.asInstanceOf[UserAction])
        recallFrame = None

      case Some(frame) =>
        val time1 = System.currentTimeMillis()
        println(s"before recall...frame:${grid.frameCount}")
        grid.historyDieSnake.filter{d => d._2.contains(player.id) && d._1 > frame}.keys.headOption match {
          case Some(dieFrame) =>
            if (dieFrame - 2 > frame) grid.recallGrid(frame, dieFrame - 2)
            else grid.setGridInGivenFrame(frame)

          case None =>
            grid.recallGrid(frame, grid.frameCount)
        }
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
      if (backend > frontend && advancedFrame == 1) {
        println(s"backend advanced frontend,frontend$frontend,backend:$backend")
        addBackendInfo(grid.frameCount)
        grid.updateOnClient()
        addBackendInfo(grid.frameCount)
      } else if(frontend > backend && grid.historyStateMap.get(backend).nonEmpty){
        println(s"frontend advanced backend,frontend$frontend,backend:$backend")
        grid.setGridInGivenFrame(backend)
      } else if (backend >= frontend && advancedFrame < (grid.maxDelayed - 1)) {
        println(s"backend advanced frontend,frontend$frontend,backend:$backend")
        val endFrame = grid.historyDieSnake.filter{d => d._2.contains(player.id) && d._1 > frontend}.keys.headOption match {
          case Some(dieFrame) => Math.min(dieFrame - 1, backend)
          case None => backend
        }

        (frontend until endFrame).foreach { frame =>
          grid.frameCount = frame
          addBackendInfo(frame)
          grid.updateOnClient()
        }
        println(s"after speed,frame:${grid.frameCount}")
      } else {
        botActor ! BotActor.MsgToService(NeedToSync.asInstanceOf[UserAction])
      }
      syncFrame = None
    } else {
      addBackendInfo(grid.frameCount)
      grid.updateOnClient()
      addBackendInfo(grid.frameCount)
    }

    val gridData = grid.getGridData4Draw
    gridData.snakes.find(_.id == player.id) match {
      case Some(_) =>
        val offsetTime = System.currentTimeMillis() - logicFrameTime
        layeredGameScene.draw(currentRank,player.id, gridData, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(player.id),myActions)
        drawFunction = FrontProtocol.DrawBaseGame(gridData)

      case None =>
        drawFunction = FrontProtocol.DrawGameDie(grid.getKiller(player.id).map(_._2))

      case _ =>
        drawFunction = FrontProtocol.DrawGameWait
    }
  }

  def gameMessageReceiver(msg: WsSourceProtocol.WsMsgSource): Unit = {
    msg match {
      case Protocol.Id(id) =>
        log.debug(s"i receive my id:$id")

      case Protocol.RoomId(roomId) =>
        botActor ! BotActor.RoomId(roomId)
        log.debug(s"i receive roomId:$roomId")

      case Protocol.SnakeAction(carnieId, keyCode, frame, actionId) =>
        Boot.addToPlatform {
          Boot.addToPlatform {
            if (grid.snakes.contains(grid.carnieMap.getOrElse(carnieId, ""))) {
              val id = grid.carnieMap(carnieId)
              if (id == player.id) { //收到自己的进行校验是否与预判一致，若不一致则回溯
                myActions += frame -> keyCode
                //            println(s"recv:$r")
                if (grid.myActionHistory.get(actionId).isEmpty) { //前端没有该项，则加入
                  grid.addActionWithFrame(id, keyCode, frame)
                  if (frame < grid.frameCount) {
                    if(frame == grid.frameCount) println("!!!!!!!!!!!!!!frame == frontendFrame")
                    println(s"recall for my Action1,backend:$frame,frontend:${grid.frameCount}")
                    recallFrame = grid.findRecallFrame(frame, recallFrame)
                  }
                } else {
                  if (grid.myActionHistory(actionId)._1 != keyCode || grid.myActionHistory(actionId)._2 != frame) { //若keyCode或则frame不一致则进行回溯
                    //                println(s"now:${grid.frameCount}...history:${grid.myActionHistory(actionId)._2}...backend:$frame")
                    grid.deleteActionWithFrame(id, grid.myActionHistory(actionId)._2)
                    grid.addActionWithFrame(id, keyCode, frame)
                    val miniFrame = Math.min(frame, grid.myActionHistory(actionId)._2)
                    if (miniFrame < grid.frameCount) {
                      println(s"recall for my Action2,backend:$miniFrame,frontend:${grid.frameCount}")
                      recallFrame = grid.findRecallFrame(miniFrame, recallFrame)
                    }
                  }
                  grid.myActionHistory -= actionId
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
          }
        }

      case OtherAction(carnieId, keyCode, frame) =>
        if (grid.snakes.contains(grid.carnieMap.getOrElse(carnieId, ""))) {
          val id = grid.carnieMap(carnieId)
          grid.addActionWithFrame(id, keyCode, frame)
          if (frame < grid.frameCount) {
            println(s"recall for other Action,backend:$frame,frontend:${grid.frameCount}")
            recallFrame = grid.findRecallFrame(frame, recallFrame)
          }
        }

      case data: Protocol.SyncFrame =>
        syncFrame = Some(data)

      case Protocol.SomeOneWin(winner) =>
        Boot.addToPlatform {
          val finalData = grid.getGridData4Draw
          drawFunction = FrontProtocol.DrawGameWin(winner, finalData)
          grid.cleanData()
        }

      case Protocol.UserDeadMsg(frame, deadInfo) =>
        Boot.addToPlatform{
          val deadList = deadInfo.map(baseInfo => grid.carnieMap.getOrElse(baseInfo.carnieId, ""))
          grid.historyDieSnake += frame -> deadList
          deadInfo.filter(_.killerId.nonEmpty).foreach { i =>
            val idOp = grid.carnieMap.get(i.carnieId)
            if (idOp.nonEmpty) {
              val id = idOp.get
              val name = grid.snakes.get(id).map(_.name).getOrElse("unknown")
              val killerName = grid.snakes.get(grid.carnieMap.getOrElse(i.killerId.get, "")).map(_.name).getOrElse("unknown")
              grid.killInfo = Some(id, name, killerName)
              grid.barrageDuration = 100
            }
          }
          if (frame < grid.frameCount) {
            println(s"recall for UserDeadMsg,backend:$frame,frontend:${grid.frameCount}")
            val deadRecallFrame = if (deadList.contains(player.id)) frame - 2 else frame - 1
            recallFrame = grid.findRecallFrame(deadRecallFrame, recallFrame)
          }
        }


//      case Protocol.UserLeft(id) =>
//        Boot.addToPlatform {
//          println(s"user $id left:::")
//          if (grid.snakes.contains(id)) grid.snakes -= id
//          grid.returnBackField(id)
//          grid.grid ++= grid.grid.filter(_._2 match { case Body(_, fid) if fid.nonEmpty && fid.get == id => true case _ => false }).map { g =>
//            Point(g._1.x, g._1.y) -> Body(g._2.asInstanceOf[Body].id, None)
//          }
//        }

      case UserLeft(id) =>
        Boot.addToPlatform {
          println(s"user $id left:::")
          grid.carnieMap = grid.carnieMap.filterNot(_._2 == id)
          grid.cleanDiedSnakeInfo(id)
        }


      case x@Protocol.DeadPage(_, _, _) =>
        println(s"recv userDead $x")
        Boot.addToPlatform {
          botActor ! Dead
        }


      case Protocol.Ranks(current, score, _, _) =>
        Boot.addToPlatform {
          println("rank!!!")
          currentRank = current
          myCurrentRank = score
          layeredGameScene.drawRank(player.id,grid.getGridData.snakes,currentRank)
        }

      case data: Protocol.Data4TotalSync =>
//        println("data!!!")
        Boot.addToPlatform{
          syncGridData = Some(data)
        }

      case data: Protocol.NewSnakeInfo =>
        Boot.addToPlatform{
          data.snake.foreach { s => grid.carnieMap += s.carnieId -> s.id }
          grid.historyNewSnake += data.frameCount -> (data.snake, data.filedDetails.map { f =>
            Protocol.FieldByColumn(grid.carnieMap.getOrElse(f.uid, ""), f.scanField)
          })
          if (data.frameCount < grid.frameCount) {
            println(s"recall for NewSnakeInfo,backend:${data.frameCount},frontend:${grid.frameCount}")
            recallFrame = grid.findRecallFrame(data.frameCount - 1, recallFrame)
          }
        }

      case data: Protocol.NewFieldInfo =>
//        println(s"====================new field")
        Boot.addToPlatform {
          val fields = data.fieldDetails.map{f => Protocol.FieldByColumn(grid.carnieMap.getOrElse(f.uid, ""), f.scanField)}
          grid.historyFieldInfo += data.frameCount -> fields
          if (data.frameCount < grid.frameCount) {
            println(s"recall for NewFieldInfo,backend:${data.frameCount},frontend:${grid.frameCount}")
            recallFrame = grid.findRecallFrame(data.frameCount - 1, recallFrame)
          }
        }

      case x@Protocol.ReceivePingPacket(_) =>
        Boot.addToPlatform{
        }

      case unknown@_ =>
        log.debug(s"i receive an unknown msg:$unknown")
    }
  }

  def addBackendInfo(frame: Int): Unit = {
    grid.historyFieldInfo.get(frame).foreach { data =>
      grid.addNewFieldInfo(data)
    }

    grid.historyDieSnake.get(frame).foreach { deadSnake =>
      deadSnake.foreach { sid =>
        grid.cleanDiedSnakeInfo(sid)
      }
    }

    grid.historyNewSnake.get(frame).foreach { newSnakes =>
//      if (newSnakes._1.map(_.id).contains(player.id) && !firstCome && !isContinue) spaceKey()
      newSnakes._1.foreach { s => grid.cleanSnakeTurnPoint(s.id) } //清理死前拐点
      grid.snakes ++= newSnakes._1.map(s => s.id -> s).toMap
      grid.addNewFieldInfo(newSnakes._2)
    }
  }

  def addBackendInfo4Sync(frame: Int): Unit = {
    grid.historyNewSnake.get(frame).foreach { newSnakes =>
//      if (newSnakes._1.map(_.id).contains(player.id) && !firstCome && !isContinue) spaceKey()
    }
  }

  def getAllImage  = {
    Boot.addToPlatform{
      val imageList = layeredGameScene.layered.getAllImageData
      val humanObservation : _root_.scala.Option[ImgData] = imageList.find(_._1 == "6").map(_._2)
      val layeredObservation : LayeredObservation = LayeredObservation(
        imageList.find(_._1 == "0").map(_._2),
        imageList.find(_._1 == "1").map(_._2),
        imageList.find(_._1 == "2").map(_._2),
        imageList.find(_._1 == "3").map(_._2),
        imageList.find(_._1 == "4").map(_._2),
        imageList.find(_._1 == "5").map(_._2)
      )
      val observation = (humanObservation,Some(layeredObservation), grid.frameCount, true)
      botActor ! Observation(observation)
    }
    }
}
