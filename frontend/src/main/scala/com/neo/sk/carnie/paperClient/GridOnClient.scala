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

  def updateInClient() = {
    updateSnakes()
    super.updateSpots()
    actionMap -= (frameCount - maxDelayed)
    historyStateMap = historyStateMap.filter(_._1 > (frameCount - (maxDelayed + 1)))
    frameCount += 1
  }

  private[this] def updateSnakes() : Unit = {
    def updateASnake(snake: SkDt, actMap: Map[Long, Int]): Either[Option[Long], UpdateSnakeInfo] = {
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

      val newHeader = snake.header + newDirection

      grid.get(newHeader) match {
        case Some(x: Body) => //进行碰撞检测
          debug(s"snake[${snake.id}] hit wall.")
          if (x.id != snake.id) { //撞到了别人的身体
            killHistory += x.id -> (snake.id, snake.name)
          }
          mayBeDieSnake += x.id -> snake.id
          grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
            case Some(Field(fid)) if fid == snake.id =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header), x.fid))
            case _ =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), x.fid))
          }

        case Some(Field(id)) =>
          if (id == snake.id) {
            grid(snake.header) match {
              case Body(bid, _) if bid == snake.id => //回到了自己的领域
                if (mayBeDieSnake.keys.exists(_ == snake.id)) { //如果在即将完成圈地的时候身体被撞击则不死但此次圈地作废
                  killHistory -= snake.id
                  mayBeDieSnake -= snake.id
                  returnBackField(snake.id)
                } else {
                  val stillStart = if (grid.get(snake.startPoint) match {
                    case Some(Field(fid)) if fid == snake.id => true
                    case _ => false
                  }) true else false //起点是否被圈走
                  if (!stillStart) returnBackField(snake.id)
                }
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), Some(id)))

              case _ =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), Some(id)))
            }
          } else { //进入到别人的领域
            grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
              case Some(Field(fid)) if fid == snake.id =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header), Some(id)))
              case _ =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), Some(id)))
            }
          }

        case Some(Border) =>
          Left(None)

        case _ =>
          grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
            case Some(Field(fid)) if fid == snake.id =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header)))
            case _ =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
          }
      }
    }

    var mapKillCounter = Map.empty[Long, Int]
    var updatedSnakes = List.empty[UpdateSnakeInfo]
    var killedSnaked = List.empty[Long]

    historyStateMap += frameCount -> (snakes, grid)

    val acts = actionMap.getOrElse(frameCount, Map.empty[Long, Int])

    snakes.values.map(updateASnake(_, acts)).foreach {
      case Right(s) =>
        updatedSnakes ::= s

      case Left(_) =>
    }

    val intersection = mayBeSuccess.keySet.filter(p => mayBeDieSnake.keys.exists(_ == p))
    if(intersection.nonEmpty){
      intersection.foreach{ snakeId =>  // 在即将完成圈地的时候身体被撞击则不死但此次圈地作废
        mayBeSuccess(snakeId).foreach{i =>
          i._2 match {
            case Body(_, fid) if fid.nonEmpty => grid += i._1 -> Field(fid.get)
            case Field(fid) => grid += i._1 -> Field(fid)
            case _ => grid -= i._1
          }
        }
        mayBeDieSnake -= snakeId
        killHistory -= snakeId
      }
    }

    mayBeDieSnake.foreach { s =>
      mapKillCounter += s._2 -> (mapKillCounter.getOrElse(s._2, 0) + 1)
      killedSnaked ::= s._1
    }

    mayBeDieSnake = Map.empty[Long, Long]
    mayBeSuccess = Map.empty[Long, Map[Point, Spot]]

    //if two (or more) headers go to the same point,die at the same time
    val snakesInDanger = updatedSnakes.groupBy(_.data.header).filter(_._2.lengthCompare(1) > 0).values
    val deadSnakes = snakesInDanger.flatMap{ hits => hits.map(_.data.id)}.toList
    val noFieldSnake = snakes.keySet &~ grid.map(_._2 match { case x@Field(uid) => uid case _ => 0 }).toSet.filter(_ != 0) //若领地全被其它玩家圈走则死亡

    val finalDie = deadSnakes ::: killedSnaked ::: noFieldSnake.toList

    finalDie.foreach { sid =>
      returnBackField(sid)
      grid ++= grid.filter(_._2 match { case Body(_, fid) if fid.nonEmpty && fid.get == sid => true case _ => false }).map { g =>
        Point(g._1.x, g._1.y) -> Body(g._2.asInstanceOf[Body].id, None)
      }
    }

    val newSnakes = updatedSnakes.filterNot(s => finalDie.contains(s.data.id)).map { s =>
      mapKillCounter.get(s.data.id) match {
        case Some(k) => s.copy(data = s.data.copy(kill = k + s.data.kill))
        case None => s
      }
    }

    newSnakes.foreach { s =>
      if (s.bodyInField.nonEmpty && s.bodyInField.get == s.data.id) grid += s.data.header -> Field(s.data.id)
      else grid += s.data.header -> Body(s.data.id, s.bodyInField)
    }

    snakes = newSnakes.map(s => (s.data.id, s.data)).toMap
  }

  def initSyncGridData(data: Protocol.Data4TotalSync): Unit = {
    val bodyMap = data.bodyDetails.map(b => Point(b.x, b.y) -> Body(b.id, b.fid)).toMap
    val bordMap = grid.filter(_._2 match { case Border => true case _ => false })
    var gridMap = bodyMap ++ bordMap
    data.fieldDetails.foreach { users =>
      users._2.foreach { x =>
        x._2.foreach { l => (l._1 to l._2 by 1).foreach(y => gridMap += Point(x._1, y) -> Field(users._1)) }
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
    data.blankDetails.foreach { blank =>
      blank._2.foreach { l => (l._1 to l._2 by 1).foreach(y => newGrid -= Point(blank._1, y)) }
    }
    data.fieldDetails.foreach { users =>
      users._2.foreach { x =>
        x._2.foreach { l => (l._1 to l._2 by 1).foreach(y => newGrid += Point(x._1, y) -> Field(users._1)) }
      }
    }
    data.bodyDetails.foreach(b => newGrid += Point(b.x, b.y) -> Body(b.id, b.fid))

    frameCount = data.frameCount
    grid = newGrid
    actionMap = actionMap.filterKeys(_ >= (data.frameCount - maxDelayed))
    snakes = data.snakes.map(s => s.id -> s).toMap
    killHistory = data.killHistory.map(k => k.killedId -> (k.killerId, k.killerName)).toMap
  }





}
