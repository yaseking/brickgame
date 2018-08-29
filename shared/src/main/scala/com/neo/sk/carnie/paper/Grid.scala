package com.neo.sk.carnie.paper

import java.awt.event.KeyEvent
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 5:34 PM
  */
trait Grid {

  val boundary: Point

  def debug(msg: String): Unit

  def info(msg: String): Unit

  val random = new Random(System.nanoTime())

  val maxDelayed = 6 //最大接收5帧以内的延时
  val historyRankLength = 5
  var frameCount = 0l
  var grid = Map[Point, Spot]()
  var snakes = Map.empty[Long, SkDt]
  var actionMap = Map.empty[Long, Map[Long, Int]]
  var killHistory = Map.empty[Long, (Long, String)] //killedId, (killerId, killerName)
  var mayBeDieSnake = Map.empty[Long, Long] //可能死亡的蛇 killedId,killerId
  var mayBeSuccess = Map.empty[Long, Map[Point, Spot]] //圈地成功后的被圈点
  var historyStateMap = Map.empty[Long, (Map[Long, SkDt], Map[Point, Spot])] //保留近期的状态以方便回溯

  List(0, BorderSize.w - 1).foreach(x => (0 until BorderSize.h).foreach(y => grid += Point(x, y) -> Border))
  List(0, BorderSize.h - 1).foreach(y => (0 until BorderSize.w).foreach(x => grid += Point(x, y) -> Border))

  def removeSnake(id: Long): Option[SkDt] = {
    val r = snakes.get(id)
    if (r.isDefined) {
      snakes -= id
    }
    r
  }


  def addAction(id: Long, keyCode: Int) = {
    addActionWithFrame(id, keyCode, frameCount)
  }

  def addActionWithFrame(id: Long, keyCode: Int, frame: Long) = {
    val map = actionMap.getOrElse(frame, Map.empty)
    val tmp = map + (id -> keyCode)
    actionMap += (frame -> tmp)
  }

  def deleteActionWithFrame(id: Long, frame: Long) = {
    val map = actionMap.getOrElse(frame, Map.empty)
    val tmp = map - id
    actionMap += (frame -> tmp)
  }

  def nextDirection(id: Long) = {
    val map = actionMap.getOrElse(frameCount, Map.empty)
    map.get(id) match {
      case Some(KeyEvent.VK_LEFT) => Some(Point(-1, 0))
      case Some(KeyEvent.VK_RIGHT) => Some(Point(1, 0))
      case Some(KeyEvent.VK_UP) => Some(Point(0, -1))
      case Some(KeyEvent.VK_DOWN) => Some(Point(0, 1))
      case _ => None
    }
  }

  def checkActionWithFrame(id: Long, keyCode: Int, frame: Long) = {
    actionMap.get(frame) match {
      case Some(action) =>
        action.get(id) match {
          case Some(key) => if (key == keyCode) true else false

          case None => false
        }
      case None => false
    }
  }


  def update() = {
    val isFinish = updateSnakes()
    updateSpots()
    actionMap -= (frameCount - maxDelayed)
    historyStateMap = historyStateMap.filter(_._1 > (frameCount - (maxDelayed + 1)))
    frameCount += 1
    isFinish
  }

  private[this] def updateSpots() = {
    grid = grid.filter { case (p, spot) =>
      spot match {
        case Body(id,_) if snakes.contains(id) => true
        case Field(id) if snakes.contains(id) => true
        case Border => true
        case _ => false
      }
    }
  }

  def randomEmptyPoint(size: Int): Point = {
    var p = Point(random.nextInt(boundary.x.toInt - size), random.nextInt(boundary.y.toInt - size))
    while ((0 until size).flatMap { x =>
      (0 until size).map { y =>
        grid.contains(p.copy(x = p.x + x, y = p.y + y))
      }
    }.contains(true)) {
      p = Point(random.nextInt(boundary.x.toInt - size), random.nextInt(boundary.y.toInt - size))
    }
    p
  }

  private[this] def updateSnakes() = {
    var isFinish = false //改帧更新中是否有圈地 若圈地了则后台传输数据到前端

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
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header)))
            case _ =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
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
                  if (stillStart) {
                    isFinish = true
                    var finalFillPoll = grid.filter(_._2 match {case Body(bodyId, _) if bodyId == snake.id => true case _ => false})

                    grid ++= finalFillPoll.keys.map(p => p -> Field(snake.id))

                    val myFieldPoint = grid.filter(_._2 match { case Field(fid) if fid == snake.id => true case _ => false }).keys

                    val (xMin, xMax, yMin, yMax) = Short.findMyRectangle(myFieldPoint)

                    var targets = Set.empty[Point] //所有需要检查的坐标值的集

                    for (x <- xMin until xMax) {
                      for (y <- yMin until yMax) {
                        grid.get(Point(x, y)) match {
                          case Some(x: Field) if x.id == snake.id => //donothing
                          case _ => targets = targets + Point(x, y)
                        }
                      }
                    }

                    while (targets.nonEmpty) {
                      var iter = List.empty[Point]
                      iter = iter :+ targets.head
                      targets = targets.tail

                      var fillPool = List.empty[Point] //该次填色需要上色的所有坐标
                      var in_bound = true //这次上色是否为内部区域
                      while (iter.nonEmpty) {
                        val curr = iter.head
                        iter = iter.tail
                        Array(Point(-1, 0), Point(0, -1), Point(0, 1), Point(1, 0)).foreach { dir =>
                          if (targets.contains(dir + curr)) { //如果 targets 包含该坐标，则将该坐标从targets中移除并添加至iter
                            targets = targets - (dir + curr)
                            iter = iter :+ (dir + curr)
                          }
                        }
                        if (in_bound) {
                          //如果curr紧邻field_border(boundary)，将in_bound设为False；否则向fill_pool中加入curr
                          val aroundPoints = List(Point(-1, 0), Point(1, 0), Point(0, -1), Point(0, 1)).map(p => p + curr)
                          if (aroundPoints.head.x <= xMin || aroundPoints(1).x >= xMax || aroundPoints(2).y <= yMin || aroundPoints(3).y >= yMax) {
                            in_bound = false
                          } else {
                            fillPool ::= curr
                          }
                        }
                      }
                      if (in_bound) { //如果 in_bound 为真则将 fill_pool中所有坐标填充为当前玩家id
                        var newGrid = grid
                        for (p <- fillPool) {
                          grid.get(p) match {
                            case Some(Body(bodyId, _)) => newGrid += p -> Body(bodyId, Some(snake.id))
                            case Some(Border) => //doNothing
                            case x => newGrid += p -> Field(snake.id)
                              x match {
                                case Some(Field(fid)) => finalFillPoll += p -> Field(fid)
                                case _ => finalFillPoll += p -> Blank
                              }
                          }
                        }
                        grid = newGrid
                      }
                    }
                    mayBeSuccess += (snake.id -> finalFillPoll)

                  } else returnBackField(snake.id)
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
          returnBackField(snake.id)
          grid ++= grid.filter(_._2 match {case Body(_, fid) if fid.nonEmpty && fid.get == snake.id => true case _ => false}).map{ g =>
            Point(g._1.x, g._1.y) -> Body(g._2.asInstanceOf[Body].id, None)
          }
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
      returnBackField(s._1)
      grid ++= grid.filter(_._2 match {case Body(_, fid) if fid.nonEmpty && fid.get == s._1 => true case _ => false}).map{ g =>
        Point(g._1.x, g._1.y) -> Body(g._2.asInstanceOf[Body].id, None)
      }
    }

    mayBeDieSnake = Map.empty[Long, Long]
    mayBeSuccess = Map.empty[Long, Map[Point, Spot]]


    //if two (or more) headers go to the same point,die at the same time
    val snakesInDanger = updatedSnakes.groupBy(_.data.header).filter(_._2.lengthCompare(1) > 0).values
    val deadSnakes = snakesInDanger.flatMap { hits => hits.map(_.data.id) }.toSet
    val noFieldSnake = snakes.keySet &~ grid.map(_._2 match { case x@Field(uid) => uid case _ => 0 }).toSet.filter(_ != 0) //若领地全被其它玩家圈走则死亡

    val newSnakes = updatedSnakes.filterNot(s => deadSnakes.contains(s.data.id) || killedSnaked.contains(s.data.id) ||
      noFieldSnake.contains(s.data.id) ).map { s =>
      mapKillCounter.get(s.data.id) match {
        case Some(k) => s.copy(data = s.data.copy(kill = k + s.data.kill))
        case None => s
      }
    }

    newSnakes.foreach(s =>
      if (s.bodyInField.nonEmpty && s.bodyInField.get == s.data.id) grid += s.data.header -> Field(s.data.id)
      else grid += s.data.header -> Body(s.data.id, s.bodyInField)
    )
    snakes = newSnakes.map(s => (s.data.id, s.data)).toMap

    isFinish
  }

  def getGridData = {
    var bodyDetails: List[Bd] = Nil
    var fieldDetails: List[Fd] = Nil
    var bordDetails: List[Bord] = Nil
    grid.foreach {
      case (p, Body(id, fid)) => bodyDetails ::= Bd(id, fid, p.x.toInt, p.y.toInt)
      case (p, Field(id)) => fieldDetails ::= Fd(id, p.x.toInt, p.y.toInt)
      case (p, Border) => bordDetails ::= Bord(p.x.toInt, p.y.toInt)
      case _ => //doNothing
    }
    Protocol.GridDataSync(
      frameCount,
      snakes.values.toList,
      bodyDetails,
      fieldDetails,
      bordDetails,
      killHistory.map(k => Kill(k._1, k._2._1, k._2._2)).toList
    )
  }

  def getKiller(myId: Long) = {
    killHistory.get(myId)
  }

  def cleanData() = {
    snakes = Map.empty[Long, SkDt]
    actionMap = Map.empty[Long, Map[Long, Int]]
    grid = grid.filter(_._2 match { case Border => true case _ => false })
    killHistory = Map.empty[Long, (Long, String)]
  }

  def returnBackField(snakeId: Long) = {
    val bodyGrid = grid.filter(_._2 match{ case Body(bid, _) if bid == snakeId => true case _ => false })
    var newGrid = grid
    bodyGrid.foreach {
      case (p, Body(_, fid)) if fid.nonEmpty => newGrid += p -> Field(fid.get)
      case (p, _) => newGrid -= p
    }
    grid = newGrid
  }

  def recallGrid(startFrame: Long, endFrame: Long) = {
    historyStateMap.get(startFrame) match {
      case Some(state) =>
        println(s"recallGrid-start$startFrame-end-$endFrame")
        snakes = state._1
        grid = state._2
        (startFrame to endFrame).foreach { frame =>
          frameCount = frame
          updateSnakes()
        }

      case None =>
        println(s"???can't find-$startFrame-end is $endFrame!!!!tartget-${historyStateMap.keySet}")

    }
  }


}

