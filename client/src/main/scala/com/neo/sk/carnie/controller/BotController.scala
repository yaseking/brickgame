package com.neo.sk.carnie.controller

import java.awt.event.KeyEvent

import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.actor.BotActor
import com.neo.sk.carnie.paperClient._
import com.neo.sk.carnie.paperClient.Protocol.{NeedToSync, NewFieldInfo, UserAction, UserLeft}
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
  private var recallFrame: scala.Option[Long] = None

//  var allImageData:List[Array[Int]] = List.empty
  var currentRank = List.empty[Score]
  private val frameRate = 150
  var grid = new GridOnClient(Point(Boundary.w, Boundary.h))
  private val timeline = new Timeline()
  var newFieldInfo = Map.empty[Long, Protocol.NewFieldInfo] //[frame, newFieldInfo)
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None
  var newSnakeInfo: scala.Option[Protocol.NewSnakeInfo] = None
  var myCurrentRank = Score(player.id, player.name, 0)
  private var logicFrameTime = System.currentTimeMillis()

  def startGameLoop(): Unit = { //渲染帧
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
        println("!!!!!!!!:NeedToSync2")
        botActor ! BotActor.MsgToService(NeedToSync(player.id).asInstanceOf[UserAction])
        recallFrame = None

      case Some(frame) =>
        val time1 = System.currentTimeMillis()
        println(s"recall ...before frame:${grid.frameCount}")
        val oldGrid = grid
        oldGrid.recallGrid(frame, grid.frameCount)
        grid = oldGrid
        println(s"after recall time: ${System.currentTimeMillis() - time1}...after frame:${grid.frameCount}")
        recallFrame = None

      case None =>
    }

    if (newSnakeInfo.nonEmpty) {
      //        println(s"newSnakeInfo: ${newSnakeInfo.get.snake.map(_.id)}")
//      if (newSnakeInfo.get.snake.map(_.id).contains(player.id) && !firstCome) spaceKey()
      newSnakeInfo.get.snake.foreach { s =>
        grid.cleanSnakeTurnPoint(s.id) //清理死前拐点
      }
      grid.snakes ++= newSnakeInfo.get.snake.map(s => s.id -> s).toMap
      grid.addNewFieldInfo(NewFieldInfo(newSnakeInfo.get.frameCount, newSnakeInfo.get.filedDetails))

      newSnakeInfo = None
    }

    if (newFieldInfo.get(grid.frameCount + 1).nonEmpty && newFieldInfo(grid.frameCount + 1).fieldDetails.map(_.uid).contains(player.id)){
      println(s"11111111111111111")
      val snake = grid.snakes(player.id)
      val acts = grid.actionMap.getOrElse(grid.frameCount + 1, Map.empty[String, Int])
      val keyCode = acts.get(player.id)
      val newDirection = {
        val keyDirection = keyCode match {
          case Some(KeyEvent.VK_LEFT) => Point(-1, 0)
          case Some(KeyEvent.VK_RIGHT) => Point(1, 0)
          case Some(KeyEvent.VK_UP) => Point(0, -1)
          case Some(KeyEvent.VK_DOWN) => Point(0, 1)
          case _ => snake.direction
        }
        if (keyDirection + snake.direction != Point(0, 0)) {
          keyDirection
        } else {
          snake.direction
        }
      }
      val header = grid.grid.get(snake.header)
      val newHeader = grid.grid.get(snake.header + newDirection)

      println(s"old header:$header --new header:$newHeader ")
    }

    if (syncGridData.nonEmpty) { //逻辑帧更新数据
      grid.initSyncGridData(syncGridData.get)
      syncGridData = None
    } else {
      grid.update("f")
    }

    if (newFieldInfo.nonEmpty) {
      val minFrame = newFieldInfo.keys.min
      (minFrame to grid.frameCount).foreach { frame =>
        if (newFieldInfo.get(frame).nonEmpty) {
          val newFieldData = newFieldInfo(frame)
          if (newFieldData.fieldDetails.map(_.uid).contains(player.id))
            println("after newFieldInfo, my turnPoint:" + grid.snakeTurnPoints.get(player.id))
          grid.addNewFieldInfo(newFieldData)
          newFieldInfo -= frame
        }
      }
    }
//    if (newSnakeInfo.nonEmpty) {
//      grid.snakes ++= newSnakeInfo.get.snake.map(s => s.id -> s).toMap
//      grid.addNewFieldInfo(NewFieldInfo(newSnakeInfo.get.frameCount, newSnakeInfo.get.filedDetails))
//      newSnakeInfo = None
//    }
//
//    if (syncGridData.nonEmpty) {
//      grid.initSyncGridData(syncGridData.get)
//      syncGridData = None
//    } else {
//      grid.update("f")
//      if (newFieldInfo.nonEmpty) {
//        val frame = newFieldInfo.keys.min
//        val newFieldData = newFieldInfo(frame)
//        if (frame == grid.frameCount) {
//          grid.addNewFieldInfo(newFieldData)
//          newFieldInfo -= frame
//        } else if (frame < grid.frameCount) {
//          botActor ! BotActor.MsgToService(NeedToSync(player.id).asInstanceOf[UserAction])
//        }
//      }
//    }

    val gridData = grid.getGridData
    gridData.snakes.find(_.id == player.id) match {
      case Some(_) =>
        val offsetTime = System.currentTimeMillis() - logicFrameTime
        layeredGameScene.draw(currentRank,player.id, gridData, offsetTime, grid, currentRank.headOption.map(_.id).getOrElse(player.id))
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

      case Protocol.SnakeAction(id, keyCode, frame, actionId) =>
        Boot.addToPlatform {
          if (grid.snakes.exists(_._1 == id)) {
            if (id == player.id) { //收到自己的进行校验是否与预判一致，若不一致则回溯
              if (grid.myActionHistory.get(actionId).isEmpty) { //前端没有该项，则加入
                grid.addActionWithFrame(id, keyCode, frame)
                if (frame < grid.frameCount) {
                  if (grid.frameCount - frame <= (grid.maxDelayed - 1)) { //回溯
                    recallFrame = recallFrame match {
                      case Some(oldFrame) => Some(Math.min(frame, oldFrame))
                      case None => Some(frame)
                    }
                  } else {
                    recallFrame = Some(-1)
                  }
                }
              } else {
                if (grid.myActionHistory(actionId)._1 != keyCode || grid.myActionHistory(actionId)._2 != frame) { //若keyCode或则frame不一致则进行回溯
                  println(s"now:${grid.frameCount}...history:${grid.myActionHistory(actionId)._2}...backend:$frame")
                  grid.deleteActionWithFrame(id, grid.myActionHistory(actionId)._2)
                  grid.addActionWithFrame(id, keyCode, frame)
                  val miniFrame = Math.min(frame, grid.myActionHistory(actionId)._2)
                  if (miniFrame < grid.frameCount) {
                    if (grid.frameCount - miniFrame <= (grid.maxDelayed - 1)) { //回溯
                      recallFrame = recallFrame match {
                        case Some(oldFrame) => Some(Math.min(frame, oldFrame))
                        case None => Some(frame)
                      }
                    } else {
                      recallFrame = Some(-1)
                    }
                  }
                }
                grid.myActionHistory -= actionId
              }
            } else { //收到别人的动作则加入action，若帧号滞后则进行回溯
              grid.addActionWithFrame(id, keyCode, frame)
              if (frame < grid.frameCount) {
                if (grid.frameCount - frame <= (grid.maxDelayed - 1)) { //回溯
                  recallFrame = recallFrame match {
                    case Some(oldFrame) => Some(Math.min(frame, oldFrame))
                    case None => Some(frame)
                  }
                } else {
                  recallFrame = Some(-1)
                }
              }
            }
          }
        }

      case Protocol.SomeOneWin(winner, finalData) =>
        Boot.addToPlatform {
          drawFunction = FrontProtocol.DrawGameWin(winner, finalData)
          grid.cleanData()
        }

      case Protocol.UserLeft(id) =>
        Boot.addToPlatform {
          println(s"user $id left:::")
          if (grid.snakes.contains(id)) grid.snakes -= id
          grid.returnBackField(id)
          grid.grid ++= grid.grid.filter(_._2 match { case Body(_, fid) if fid.nonEmpty && fid.get == id => true case _ => false }).map { g =>
            Point(g._1.x, g._1.y) -> Body(g._2.asInstanceOf[Body].id, None)
          }
        }

      case x@Protocol.DeadPage(id, kill, area, start, end) =>
        println(s"recv userDead $x")
        Boot.addToPlatform {
          botActor ! Dead
        }


      case Protocol.Ranks(current, score, _) =>
        Boot.addToPlatform {
          currentRank = current
//          val myCurrent = current.find(_.id == player.id)
//          myCurrentRank = if (myCurrent.nonEmpty) {
//            myCurrent.get
//          } else myCurrentRank.copy(k = 0, area = 0)
          myCurrentRank = score
        }

      case data: Protocol.Data4TotalSync =>
        Boot.addToPlatform{
          syncGridData = Some(data)
          newFieldInfo = newFieldInfo.filterKeys(_ > data.frameCount)
        }

      case data: Protocol.NewSnakeInfo =>
        println(s"!!!!!!new snake join!!!")
        Boot.addToPlatform{
          newSnakeInfo = Some(data)
        }

      case Protocol.SomeOneKilled(killedId, killedName, killerName) =>
        Boot.addToPlatform {
          grid.killInfo = Some(killedId, killedName, killerName)
          grid.barrageDuration = 100
        }

      case data: Protocol.NewFieldInfo =>
        Boot.addToPlatform{
          if(data.fieldDetails.exists(_.uid == player.id))
          newFieldInfo += data.frameCount -> data
        }

      case x@Protocol.ReceivePingPacket(_) =>
        Boot.addToPlatform{
        }


      case unknown@_ =>
        log.debug(s"i receive an unknown msg:$unknown")
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
