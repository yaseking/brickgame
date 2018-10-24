package com.neo.sk.carnie.paperClient

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.neo.sk.carnie.paperClient.WsSourceProtocol.{FailMsgServer, WsMsgSource}

/**
  * Created by dry on 2018/10/23.
  **/
object NetGameHolder {

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
  var newFieldInfo: scala.Option[Protocol.NewFieldInfo] = None
  var syncGridData: scala.Option[Protocol.Data4TotalSync] = None
  var play = true
  val idGenerator = new AtomicInteger(1)
  private var myActionHistory = Map[Int, (Int, Long)]()

  def running(id: String, name: String): Behavior[WsMsgSource] = {
    Behaviors.receive[WsMsgSource] { (ctx, msg) =>
      msg match {
        case FailMsgServer(_) =>
          println("fail msg server")
          Behavior.same

        case x =>
          Behavior.same
      }
    }
  }


}
