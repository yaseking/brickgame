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
      val uid = bodys._1
      if(bodys._2._1.nonEmpty) {
        var first = bodys._2._1.head
        var remainder = bodys._2._1.tail
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
      bodys._2._2.foreach{p =>  gridMap += Point(p._1.x, p._1.y) -> Body(uid, Some(p._2))}
    }
    data.fieldDetails.foreach { users =>
      users._2.foreach { x =>
        x._2.foreach { l => (l._1 to l._2).foreach(y => gridMap += Point(x._1, y) -> Field(users._1)) }
      }
    }
    frameCount = data.frameCount
    grid = gridMap
    actionMap = actionMap.filterKeys(_ >= (data.frameCount - maxDelayed))
    snakes = data.snakes.map(s => s.id -> s).toMap
    killHistory = data.killHistory.map(k => k.killedId -> (k.killerId, k.killerName)).toMap
  }

  def setSyncGridData(data: Protocol.Data4Sync): Unit = {
    var newGrid = grid
    grid.foreach { g =>
      g._2 match {
        case Body(_, fid) if fid.nonEmpty => newGrid += g._1 -> Field(fid.get)
        case Body(_, fid) if fid.isEmpty => newGrid -= g._1
        case _ => //
      }
    }

    data.bodyDetails.foreach{ bodys =>
      val uid = bodys._1
      if(bodys._2._1.nonEmpty) {
        var first = bodys._2._1.head
        var remainder = bodys._2._1.tail
        while (remainder.nonEmpty) {
          if (first.x == remainder.head.x) { //同x
            (Math.min(first.y, remainder.head.y) to Math.max(first.y, remainder.head.y)).foreach(y =>
              newGrid += Point(first.x, y) -> Body(uid, None)
            )
          } else { // 同y
            (Math.min(first.x, remainder.head.x) to Math.max(first.x, remainder.head.x)).foreach(x =>
              newGrid += Point(x, first.y) -> Body(uid, None)
            )
          }
          first = remainder.head
          remainder = remainder.tail
        }
      }
      bodys._2._2.foreach{p =>  newGrid += Point(p._1.x, p._1.y) -> Body(uid, Some(p._2))}
    }

    data.blankDetails.foreach { blank =>
      blank._2.foreach { l => (l._1 to l._2 by 1).foreach(y => newGrid -= Point(blank._1, y)) }
    }
    data.fieldDetails.foreach { users =>
      users._2.foreach { x =>
        x._2.foreach { l => (l._1 to l._2 by 1).foreach(y => newGrid += Point(x._1, y) -> Field(users._1)) }
      }
    }
    frameCount = data.frameCount
    grid = newGrid
    actionMap = actionMap.filterKeys(_ >= (data.frameCount - maxDelayed))
    snakes = data.snakes.map(s => s.id -> s).toMap
    killHistory = data.killHistory.map(k => k.killedId -> (k.killerId, k.killerName)).toMap
  }





}
