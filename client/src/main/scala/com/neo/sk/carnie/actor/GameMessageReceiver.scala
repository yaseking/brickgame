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
          Behaviors.same

        case Protocol.ReStartGame =>
          Behaviors.same

        case Protocol.SomeOneWin(winner, finalData) =>
          Behaviors.same

        case Protocol.Ranks(current) =>
          Behaviors.same

        case data: Protocol.Data4TotalSync =>
//          syncGridData = Some(data)
//          justSynced = true
          Behaviors.same

        case Protocol.SomeOneKilled(killedId, killedName, killerName) =>
//          killInfo = (killedId, killedName, killerName)
//          lastTime = 100
          Behaviors.same

        case data: Protocol.NewFieldInfo =>
//          newFieldInfo = Some(data)
          Behaviors.same

        case x@Protocol.ReceivePingPacket(_) =>
//          PerformanceTool.receivePingPackage(x)
          Behaviors.same

        case unknown@_ =>
          Behavior.same
      }
    }
  }
}
