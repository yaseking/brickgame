package com.neo.sk.carnie.controller

import com.neo.sk.carnie.paperClient.Protocol.NewFieldInfo
import com.neo.sk.carnie.paperClient._

/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 10:13 PM
  */
class GridOnClient(override val boundary: Point) extends Grid {

  override def debug(msg: String): Unit = println(msg)

  override def info(msg: String): Unit = println(msg)

  var myActionHistory : Map[Int, (Int, Int)] = Map[Int, (Int, Int)]() //(actionId, (keyCode, frameCount))

  //击杀弹幕
  var killInfo: scala.Option[(String, String, String)] = None
  var barrageDuration = 0



  def initSyncGridData(data: Protocol.Data4TotalSync): Unit = {
    var gridMap = grid.filter(_._2 match { case Body(_, _) => false case _ => true })
    data.bodyDetails.foreach{ bodys =>
      val uid = bodys.uid
      if(bodys.turn.turnPoint.nonEmpty) {
        var first = bodys.turn.turnPoint.head
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
      snakeTurnPoints += ((uid, bodys.turn.turnPoint))
    }

//    data.fieldDetails.foreach { baseInfo =>
//      baseInfo.scanField.foreach { fids =>
//        fids.y.foreach { ly => (ly._1 to ly._2 by 1).foreach{y =>
//          fids.x.foreach { lx => (lx._1 to lx._2 by 1).foreach(x => gridMap += Point(x, y) -> Field(baseInfo.uid)) }
//        }}
//      }
//    }

    if(data.fieldDetails.nonEmpty) {
      gridMap = gridMap.filter(_._2 match { case Field(_) => false case _ => true })
      data.fieldDetails.foreach { baseInfo =>
        baseInfo.scanField.foreach { fids =>
          fids.y.foreach { ly => (ly._1 to ly._2 by 1).foreach{y =>
            fids.x.foreach { lx => (lx._1 to lx._2 by 1).foreach(x => gridMap += Point(x, y) -> Field(baseInfo.uid)) }
          }}
        }
      }
    }

    frameCount = data.frameCount
    grid = gridMap
    actionMap = actionMap.filterKeys(_ >= (data.frameCount - maxDelayed))
    snakes = data.snakes.map(s => s.id -> s).toMap
//    killHistory = data.killHistory.map(k => k.killedId -> (k.killerId, k.killerName,k.frameCount)).toMap
  }

  def addNewFieldInfo(data: Protocol.NewFieldInfo): Unit = {
    data.fieldDetails.foreach { baseInfo =>
      baseInfo.scanField.foreach { fids =>
        fids.y.foreach { ly => (ly._1 to ly._2 by 1).foreach{y =>
          fids.x.foreach { lx => (lx._1 to lx._2 by 1).foreach(x => grid += Point(x, y) -> Field(baseInfo.uid)) }
        }}
      }
    }
  }

  def recallGrid(startFrame: Int, endFrame: Int): Unit = {
    historyStateMap.get(startFrame) match {
      case Some(state) =>
        println(s"recallGrid-start$startFrame-end-$endFrame")
        snakes = state._1
        grid = state._2
        (startFrame until endFrame).foreach { frame =>
          frameCount = frame
          updateSnakes("f")
          updateSpots()
          val newFrame = frame + 1

          historyFieldInfo.get(newFrame).foreach { data =>
            addNewFieldInfo(data)
          }

          historyNewSnake.get(newFrame).foreach { newSnakes =>
            newSnakes.snake.foreach { s => cleanSnakeTurnPoint(s.id) } //清理死前拐点
            snakes ++= newSnakes.snake.map(s => s.id -> s).toMap
            addNewFieldInfo(NewFieldInfo(frame, newSnakes.filedDetails))
          }

          historyDieSnake.get(newFrame).foreach { dieSnakes =>
            dieSnakes.foreach(sid => cleanDiedSnakeInfo(sid))
          }
        }

      case None =>
        println(s"???can't find-$startFrame-end is $endFrame!!!!tartget-${historyStateMap.keySet}")
    }
  }


}
