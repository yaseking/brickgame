package com.neo.sk.carnie.paperClient

import java.awt.event.KeyEvent

//import com.neo.sk.carnie.paperClient.NetGameHolder.grid

/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 10:13 PM
  */
class GridOnClient(override val boundary: Point) extends Grid {

  override def debug(msg: String): Unit = println(msg)

  override def info(msg: String): Unit = println(msg)

//  override def checkEvents(enclosure: List[(String, List[Point])]): Unit = {}

  def initSyncGridData(data: Protocol.Data4TotalSync): Unit = {
    println("back frame:"+ data.frameCount)
    val gridField = grid.filter(_._2 match { case Field(_) => true case _ => false })
    var gridMap = grid.filter(_._2 match { case Body(_, _) => false case _ => true })
    data.bodyDetails.foreach { bodies =>
      val uid = bodies.uid
      if (bodies.turn.turnPoint.nonEmpty) {
        var first = bodies.turn.turnPoint.head
        var remainder = bodies.turn.turnPoint.tail
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
      bodies.turn.pointOnField.foreach { p => gridMap += Point(p._1.x, p._1.y) -> Body(uid, Some(p._2)) }
      snakeTurnPoints += ((uid, bodies.turn.turnPoint))
    }

    gridMap ++= gridField

    if(data.fieldDetails.nonEmpty) {
      gridMap = gridMap.filter(_._2 match { case Field(_) => false case _ => true })
      data.fieldDetails.foreach { baseInfo =>
        baseInfo.scanField.foreach { fids =>
          fids.y.foreach { ly =>
            (ly._1 to ly._2 by 1).foreach { y =>
              fids.x.foreach { lx => (lx._1 to lx._2 by 1).foreach(x => gridMap += Point(x, y) -> Field(baseInfo.uid)) }
            }
          }
        }
      }
    }

    frameCount = data.frameCount
    grid = gridMap
    actionMap = actionMap.filterKeys(_ >= (data.frameCount - maxDelayed))
    snakes = data.snakes.map(s => s.id -> s).toMap
  }

  def addNewFieldInfo(data: Protocol.NewFieldInfo): Unit = {
//    data.fieldDetails.foreach { baseInfo =>
//      baseInfo.scanField.foreach { fids =>
//        fids.x.foreach { l => (l._1 to l._2 by 1).foreach(x => grid += Point(x, fids.y) -> Field(baseInfo.uid)) }
//      }
//    }
    data.fieldDetails.foreach { baseInfo =>
      baseInfo.scanField.foreach { fids =>
        fids.y.foreach { ly => (ly._1 to ly._2 by 1).foreach{y =>
          fids.x.foreach { lx => (lx._1 to lx._2 by 1).foreach(x => grid += Point(x, y) -> Field(baseInfo.uid)) }
        }}
      }
    }
  }





}
