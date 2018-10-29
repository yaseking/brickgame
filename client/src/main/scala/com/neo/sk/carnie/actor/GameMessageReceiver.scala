package com.neo.sk.carnie.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.neo.sk.carnie.Boot
import com.neo.sk.carnie.controller.GridOnClient
import com.neo.sk.carnie.paperClient.Protocol
import com.neo.sk.carnie.paperClient.WsSourceProtocol.WsMsgSource
import org.slf4j.LoggerFactory

/**
  * Created by dry on 2018/10/26.
  **/
object GameMessageReceiver {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  case class GridInitial(grid: GridOnClient) extends WsMsgSource


  def create(myId: String = "", roomId: Long = -1l): Behavior[WsMsgSource] = {
    Behaviors.receive[WsMsgSource] { (ctx, msg) =>
      msg match {
        case GridInitial(grid) =>
          idle(myId, roomId, grid)

        case unknown@_ =>
          Behavior.same
      }
    }
  }

  def idle(myId: String = "", roomId: Long = -1l, grid: GridOnClient): Behavior[WsMsgSource] = {
    Behaviors.receive[WsMsgSource] { (ctx, msg) =>
      msg match {
        case Protocol.Id(id) =>
          Boot.addToPlatform (grid.myId = id)
          idle(id, roomId, grid)

        case Protocol.SnakeAction(id, keyCode, frame, actionId) =>
          if (grid.snakes.exists(_._1 == id)) {
            if (id == myId) { //收到自己的进行校验是否与预判一致，若不一致则回溯
              if (grid.myActionHistory.get(actionId).isEmpty) { //前端没有该项，则加入
                grid.addActionWithFrame(id, keyCode, frame)
                if (frame < grid.frameCount && grid.frameCount - frame <= (grid.maxDelayed - 1)) { //回溯
                  val oldGrid = grid
                  oldGrid.recallGrid(frame, grid.frameCount)
//                  grid = oldGrid
                }
              } else {
                if (grid.myActionHistory(actionId)._1 != keyCode || grid.myActionHistory(actionId)._2 != frame) { //若keyCode或则frame不一致则进行回溯
                  grid.deleteActionWithFrame(id, grid.myActionHistory(actionId)._2)
                  grid.addActionWithFrame(id, keyCode, frame)
                  val miniFrame = Math.min(frame, grid.myActionHistory(actionId)._2)
                  if (miniFrame < grid.frameCount && grid.frameCount - miniFrame <= (grid.maxDelayed - 1)) { //回溯
                    val oldGrid = grid
                    oldGrid.recallGrid(miniFrame, grid.frameCount)
//                    grid.grid = oldGrid
                  }
                }
                grid.myActionHistory -= actionId
              }
            } else { //收到别人的动作则加入action，若帧号滞后则进行回溯
              grid.addActionWithFrame(id, keyCode, frame)
              if (frame < grid.frameCount && grid.frameCount - frame <= (grid.maxDelayed - 1)) { //回溯
                val oldGrid = grid
                oldGrid.recallGrid(frame, grid.frameCount)
//                grid = oldGrid
              }
            }
          }
          Behaviors.same

        case Protocol.ReStartGame =>
          if (grid.isWin) {
            grid.isWin = false
            grid.winnerName = "unknown"
          }
          Behaviors.same

        case Protocol.SomeOneWin(winner, finalData) =>
          Boot.addToPlatform {
            grid.isWin = true
            grid.winnerName = winner
            grid.winData = finalData
            grid.cleanData()
          }
          Behaviors.same

        case Protocol.Ranks(current) =>
            Boot.addToPlatform{
              grid.currentRank = current
//              if (grid.getGridData.snakes.exists(_.id == myId))
//                drawGame.drawRank(myId, grid.getGridData.snakes, current)
            }
          Behaviors.same

        case data: Protocol.Data4TotalSync =>
          Boot.addToPlatform{
            grid.syncGridData = Some(data)
            grid.justSynced = true
          }
          Behaviors.same

        case Protocol.SomeOneKilled(killedId, killedName, killerName) =>
          Boot.addToPlatform {
            grid.killInfo = (killedId, killedName, killerName)
            grid.lastTime = 100
          }
          Behaviors.same

        case data: Protocol.NewFieldInfo =>
          Boot.addToPlatform(grid.newFieldInfo = Some(data))
          Behaviors.same

        case x@Protocol.ReceivePingPacket(_) =>
//          PerformanceTool.receivePingPackage(x)
          Behaviors.same

        case unknown@_ =>
          log.debug(s"i receive an unknown msg:$unknown")
          Behavior.same
      }
    }
  }
}
