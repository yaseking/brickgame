package com.neo.sk.carnie.paperClient

import java.awt.event.KeyEvent

import com.neo.sk.carnie.paperClient.Protocol.{NewFieldInfo, NewSnakeInfo, Point4Trans}

import scala.collection.mutable

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

  var carnieMap = Map.empty[Byte, String]

  def initSyncGridData(data: Protocol.Data4TotalSync): Unit = {
    println("back frame:" + data.frameCount)
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

    if (data.fieldDetails.nonEmpty) {
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
    carnieMap = data.snakes.map(s => s.carnieId -> s.id).toMap
  }

  def addNewFieldInfo(data: Protocol.NewFieldInfo): Unit = {
    data.fieldDetails.foreach { baseInfo =>
      baseInfo.scanField.foreach { fids =>
        fids.y.foreach { ly =>
          (ly._1 to ly._2 by 1).foreach { y =>
            fids.x.foreach { lx =>
              (lx._1 to lx._2 by 1).foreach { x =>
                grid.get(Point(x, y)) match {
                  case Some(Body(bid, _)) => grid += Point(x, y) -> Body(bid, Some(baseInfo.uid))
                  case _ => grid += Point(x, y) -> Field(baseInfo.uid)
                }
              }
            }
          }
        }
      }
    }
  }

  def recallGrid(startFrame: Int, endFrame: Int): Unit = {
    historyStateMap.get(startFrame) match {
      case Some(state) =>
        println(s"recallGrid-start$startFrame-end-$endFrame")
        snakes = state._1
        grid = state._2
        snakeTurnPoints = state._3
        (startFrame until endFrame).foreach { frame =>
          frameCount = frame
          updateSnakes("f")
          updateSpots()
          val newFrame = frame + 1

          historyFieldInfo.get(newFrame).foreach { data =>
            addNewFieldInfo(data)
          }

          historyDieSnake.get(newFrame).foreach { dieSnakes =>
            dieSnakes.foreach(sid => cleanDiedSnakeInfo(sid))
          }

          historyNewSnake.get(newFrame).foreach { newSnakes =>
            newSnakes.snake.foreach { s => cleanSnakeTurnPoint(s.id) } //清理死前拐点
            snakes ++= newSnakes.snake.map(s => s.id -> s).toMap
            addNewFieldInfo(NewFieldInfo(frame, newSnakes.filedDetails))
          }

        }
        frameCount += 1

      case None =>
        println(s"???can't find-$startFrame-end is $endFrame!!!!tartget-${historyStateMap.keySet}")
    }
  }

  def setGridInGivenFrame(frame: Int): Unit = {
    frameCount = frame
    val state = historyStateMap(frame)
    snakes = state._1
    grid = state._2
    snakeTurnPoints = state._3
    //    historyStateMap.get(frameCount) match {
    //      case Some(state) =>
    //        snakes = state._1
    //        grid = state._2
    //        snakeTurnPoints = state._3
    //
    //        updateSnakes("f")
    //        updateSpots()
    //        frameCount += 1
    //
    //        historyFieldInfo.get(frameCount).foreach { data =>
    //          addNewFieldInfo(data)
    //        }
    //
    //        historyDieSnake.get(frameCount).foreach { dieSnakes =>
    //          dieSnakes.foreach(sid => cleanDiedSnakeInfo(sid))
    //        }
    //
    //        historyNewSnake.get(frameCount).foreach { newSnakes =>
    //          newSnakes.snake.foreach { s => cleanSnakeTurnPoint(s.id) } //清理死前拐点
    //          snakes ++= newSnakes.snake.map(s => s.id -> s).toMap
    //          addNewFieldInfo(NewFieldInfo(frame, newSnakes.filedDetails))
    //        }
    //
    //
    //      case None =>
    //        println(s"setGridInGivenFrame...but can't find-${frame-1}!tartget-${historyStateMap.keySet}")
    //
    //    }
  }

  def findRecallFrame(receiveFame: Int, oldRecallFrame: Option[Int]): Option[Int] = {
    if (frameCount - receiveFame <= (maxDelayed - 1)) { //回溯
      oldRecallFrame match {
        case Some(oldFrame) => Some(Math.min(receiveFame, oldFrame))
        case None => Some(receiveFame)
      }
    } else {
      Some(-1)
    }
  }

  def updateSnakesInFront(): Unit = {

    def updateASnake(snake: SkDt, actMap: Map[String, Int]): UpdateSnakeInfo = {
      val keyCode = actMap.get(snake.id)
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

      if (newDirection != Point(0, 0)) {
        val newHeader = snake.header + newDirection
        grid.get(newHeader) match {
          case Some(x: Body) => //进行碰撞检测
            grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
              case Some(Field(fid)) if fid == snake.id =>
                snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(newHeader.x.toShort, newHeader.y.toShort))))
                UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header), x.fid)

              case Some(Body(bid, _)) if bid == snake.id && x.fid.getOrElse(-1L) == snake.id =>
                snakeTurnPoints -= snake.id
                UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), Some(snake.id))

              case _ =>
                if (snake.direction != newDirection)
                  snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(snake.header.x.toShort, snake.header.y.toShort))))
                UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), x.fid)
            }

          case Some(Field(id)) =>
            if (id == snake.id) {
              grid(snake.header) match {
                case Body(bid, _) if bid == snake.id => //回到了自己的领域
                  snakeTurnPoints -= snake.id
                  UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), Some(id))

                case _ =>
                  UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), Some(id))
              }
            } else { //进入到别人的领域
              grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
                case Some(Field(fid)) if fid == snake.id =>
                  snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(newHeader.x.toShort, newHeader.y.toShort))))
                  UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header), Some(id))

                case _ =>
                  if (snake.direction != newDirection)
                    snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(snake.header.x.toShort, snake.header.y.toShort))))
                  UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), Some(id))
              }
            }

          case _ =>
            grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
              case Some(Field(fid)) if fid == snake.id =>
                snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(newHeader.x.toShort, newHeader.y.toShort))))
                UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header))

              case _ =>
                if (snake.direction != newDirection)
                  snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(snake.header.x.toShort, snake.header.y.toShort))))
                UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection))
            }
        }
      }
      else UpdateSnakeInfo(snake, Some(snake.id))
    }

    historyStateMap += frameCount -> (snakes, grid, snakeTurnPoints)

    val acts = actionMap.getOrElse(frameCount, Map.empty[String, Int])

    val newSnakes = snakes.values.map(updateASnake(_, acts))

    newSnakes.foreach { s =>
      if (s.bodyInField.nonEmpty && s.bodyInField.get == s.data.id) grid += s.data.header -> Field(s.data.id)
      else grid += s.data.header -> Body(s.data.id, s.bodyInField)
    }

    snakes = newSnakes.map(s => (s.data.id, s.data)).toMap
  }

  def updateInFront(): Unit = {
    updateSnakesInFront()
    updateSpots()
    actionMap -= (frameCount - maxDelayed)
    historyFieldInfo = historyFieldInfo.filter(_._1 > (frameCount - (maxDelayed + 1)))
    historyStateMap = historyStateMap.filter(_._1 > (frameCount - (maxDelayed + 1)))
    historyNewSnake = historyNewSnake.filter(_._1 > (frameCount - (maxDelayed + 1)))
    historyDieSnake = historyDieSnake.filter(_._1 > (frameCount - (maxDelayed + 1)))
    frameCount += 1
  }


}
