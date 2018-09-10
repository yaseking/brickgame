package com.neo.sk.carnie.paperClient

import java.awt.event.KeyEvent

import com.neo.sk.carnie.paperClient.NetGameHolder.grid

/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 10:13 PM
  */
class GridOnClient(override val boundary: Point) extends Grid {

  override def debug(msg: String): Unit = println(msg)

  override def info(msg: String): Unit = println(msg)

  def initSyncGridData(data: Protocol.Data4TotalSync): Unit = {
    var gridMap = grid.filter(_._2 match { case Border => true case _ => false })
    data.bodyDetails.foreach{ bodys =>
      val uid = bodys.uid
      if(bodys.turn.turnPoint.nonEmpty) {
        var first =bodys.turn.turnPoint.head
        var remainder = bodys.turn.turnPoint.tail
        while (remainder.nonEmpty) {
          if (first.x == remainder.head.x) { //同x
            (Math.min(first.y, remainder.head.y) to Math.max(first.y, remainder.head.y)).foreach(y =>
              gridMap += Point(first.x, y) -> Body(uid, None)
            )
          } else { // 同y
            (Math.min(first.x, remainder.head.x) to Math.max(first.x, remainder.head.x)).foreach(x =>
              gridMap += Point(x, first.y) -> Body(uid, None)
            )
          }
          first = remainder.head
          remainder = remainder.tail
        }
      }
      bodys.turn.pointOnField.foreach{p =>  gridMap += Point(p._1.x, p._1.y) -> Body(uid, Some(p._2))}
    }

    data.fieldDetails.foreach { baseInfo =>
      baseInfo.scanField.foreach { fids =>
        fids.x.foreach { l => (l._1 to l._2 by 1).foreach(x => gridMap += Point(x, fids.y) -> Field(baseInfo.uid)) }
      }
    }

    frameCount = data.frameCount
    grid = gridMap
    actionMap = actionMap.filterKeys(_ >= (data.frameCount - maxDelayed))
    snakes = data.snakes.map(s => s.id -> s).toMap
    killHistory = data.killHistory.map(k => k.killedId -> (k.killerId, k.killerName)).toMap
  }

  def addNewFieldInfo(data: Protocol.NewFieldInfo): Unit = {
    data.fieldDetails.foreach { baseInfo =>
      baseInfo.scanField.foreach { fids =>
        fids.x.foreach { l => (l._1 to l._2 by 1).foreach(x => grid += Point(x, fids.y) -> Field(baseInfo.uid)) }
      }
    }
  }





}
