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
  var bodyField = Map.empty[Long, Map[Point, Long]] //(body点，原归属者的id)
  var mayBeDieSnake = Map.empty[Long, Long] //可能死亡的蛇
  var mayBeSuccess = Map.empty[Long, List[Point]] //圈地成功后的被圈点
  var historyStateMap = Map.empty[Long, (Map[Long, SkDt], Map[Point, Spot])] //保留近期的状态以方便回溯

  List(0, BorderSize.w).foreach(x => (0 to BorderSize.h).foreach(y => grid += Point(x, y) -> Border))
  List(0, BorderSize.h).foreach(y => (0 to BorderSize.w).foreach(x => grid += Point(x, y) -> Border))

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
        case Body(id) if snakes.contains(id) => true
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
      val (newDirection, turnPoint) = {
        val keyDirection = keyCode match {
          case Some(KeyEvent.VK_LEFT) => Point(-1, 0)
          case Some(KeyEvent.VK_RIGHT) => Point(1, 0)
          case Some(KeyEvent.VK_UP) => Point(0, -1)
          case Some(KeyEvent.VK_DOWN) => Point(0, 1)
          case _ => snake.direction
        }
        if (keyDirection + snake.direction != Point(0, 0)) {
          if (keyDirection != snake.direction) (keyDirection, Some(snake.header))
          else (keyDirection, None)
        } else {
          (snake.direction, None)
        }
      }

      val newHeader = snake.header + newDirection

      val value = grid.get(newHeader) match {
        case Some(x: Body) => //进行碰撞检测
          debug(s"snake[${snake.id}] hit wall.")
          if (x.id != snake.id) { //撞到了别人的身体
            killHistory += x.id -> (snake.id, snake.name)
          }
          mayBeDieSnake += x.id -> snake.id
          grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
            case Some(Field(fid)) if fid == snake.id =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header, turnDirection = List(newDirection))))
            case _ =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
          }

        case Some(Field(id)) =>
          if (id == snake.id) {
            grid(snake.header) match {
              case Body(bid) if bid == snake.id => //回到了自己的领域
                if(mayBeDieSnake.keys.exists(_ == snake.id)){ //如果在即将完成圈地的时候身体被撞击则不死但此次圈地作废
                  killHistory -= snake.id
                  mayBeDieSnake -= snake.id
                  returnBackField(snake.id)
                } else {
                  val stillStart = if (grid.get(snake.startPoint) match {
                    case Some(Field(fid)) if fid == snake.id => true
                    case _ => false
                  }) true else false //起点是否被圈走
                  if (stillStart) {
//                    isFinish = true

                    val myFieldPoint = ArrayBuffer[Point]()
                    grid = grid.map {
                      case (p, Body(bodyId)) if bodyId == snake.id =>
                        myFieldPoint += p
                        (p, Field(id))

                      case x@(p, Field(fid)) if fid == snake.id =>
                        myFieldPoint += p
                        x

                      case x => x
                    }

                    val xMin = myFieldPoint.minBy(_.x).x.toInt - 1
                    val xMax = myFieldPoint.maxBy(_.x).x.toInt + 1
                    val yMin = myFieldPoint.minBy(_.y).y.toInt - 1
                    val yMax = myFieldPoint.maxBy(_.y).y.toInt + 1

                    var targets = Set.empty[Point] //所有需要检查的坐标值的集

                    for(x <- xMin until xMax) {
                      for (y <- yMin until yMax) {
                        grid.get(Point(x, y)) match {
                          case Some(x: Field) if x.id == snake.id => //donothing
                          case _ => targets = targets + Point(x, y)
                        }
                      }
                    }

                    var finalFillPoll = List.empty[Point]
                    while (targets.nonEmpty){
                      var iter = List.empty[Point]
                      iter = iter :+ targets.head
                      targets = targets.tail

                      var fillPool = List.empty[Point] //该次填色需要上色的所有坐标
                      var in_bound = true //这次上色是否为内部区域
                      while (iter.nonEmpty){
                        val curr = iter.head
                        iter = iter.tail
                        Array(Point(-1, 0),  Point(0, -1), Point(0, 1), Point(1,0)).foreach { dir =>
                          if (targets.contains(dir + curr)) { //如果 targets 包含该坐标，则将该坐标从targets中移除并添加至iter
                            targets = targets - (dir + curr)
                            iter = iter :+ (dir + curr)
                          }
                        }
                        if(in_bound) {
                          //如果curr紧邻field_border(boundary)，将in_bound设为False；否则向fill_pool中加入curr
                          val aroundPoints = List(Point(-1, 0), Point(1, 0), Point(0, -1), Point(0, 1)).map(p => p + curr)
                          if (aroundPoints.head.x <= xMin || aroundPoints(1).x >= xMax || aroundPoints(2).y <= yMin || aroundPoints(3).y >= yMax) {
                            in_bound = false
                          } else {
                            fillPool ::= curr
                          }
                        }
                      }
                      if(in_bound){ //如果 in_bound 为真则将 fill_pool中所有坐标填充为当前玩家id
                        var newGrid = grid
                        finalFillPoll = finalFillPoll ::: fillPool
                        for(p <- fillPool ){
                          grid.get(p) match {
                            case Some(Border) => newGrid += p -> Border
                            case Some(Body(bodyId)) => newGrid += p -> Body(bodyId)
                            case x => newGrid += p -> Field(snake.id)
                          }
                        }
                        grid = newGrid
                      }
                    }
                    mayBeSuccess += (snake.id -> finalFillPoll)
//                    println(s"end -- +${System.currentTimeMillis()}")
                  } else returnBackField(snake.id)
                }
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, turnPoint = Nil), true))

              case _ =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), true))
            }
          } else { //进入到别人的领域
            val tmp = bodyField.getOrElse(snake.id, Map.empty) + (newHeader -> id)
            bodyField += (snake.id -> tmp)
            grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
              case Some(Field(fid)) if fid == snake.id =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header, turnDirection = List(newDirection))))
              case _ =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
            }
          }

        case Some(Border) =>
          returnBackField(snake.id)
          Left(None)

        case _ =>
          grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
            case Some(Field(fid)) if fid == snake.id =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header, turnDirection = List(newDirection))))
            case _ =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
          }
      }
      value match {
        case Right(v) =>
          val newData = if (turnPoint.nonEmpty && !v.isFiled) { //判断是顺时针还是逆时针行走
            v.copy(v.data.copy(turnPoint = v.data.turnPoint ::: List(turnPoint.get), turnDirection = v.data.turnDirection ::: List(newDirection)))
          } else v
          Right(newData)

        case _ => value
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

    val interset = mayBeSuccess.keySet.filter(p => mayBeDieSnake.keys.exists(_ == p))
    if(interset.nonEmpty){
      interset.foreach{ snakeId =>  //在即将完成圈地的时候身体被撞击则不死但此次圈地作废
        grid --= mayBeSuccess(snakeId)
        bodyField.get(snakeId) match {
          case Some(points) => //圈地还原
            points.foreach(p => grid += p._1 -> Field(p._2))
          case _ =>
        }
        bodyField -= snakeId
        mayBeDieSnake -= snakeId
        killHistory -= snakeId
      }
    }
    mayBeDieSnake.foreach { s =>
      mapKillCounter += s._2 -> (mapKillCounter.getOrElse(s._2, 0) + 1)
      killedSnaked ::= s._1
      returnBackField(s._1)
    }
    mayBeDieSnake = Map.empty[Long, Long]
    mayBeSuccess = Map.empty[Long, List[Point]]


    //if two (or more) headers go to the same point,die at the same time
    val snakesInDanger = updatedSnakes.groupBy(_.data.header).filter(_._2.lengthCompare(1) > 0).values
    val deadSnakes = snakesInDanger.flatMap { hits => hits.map(_.data.id) }.toSet
    val noFieldSnake = snakes.keys.toSet &~ grid.map(_._2 match { case x@Field(uid) => uid case _ => 0 }).toSet.filter(_ != 0) //若领地全被其它玩家圈走则死亡

    val newSnakes = updatedSnakes.filterNot(s => deadSnakes.contains(s.data.id) || killedSnaked.contains(s.data.id) || noFieldSnake.contains(s.data.id)).map { s =>
      mapKillCounter.get(s.data.id) match {
        case Some(k) => s.copy(data = s.data.copy(kill = k + s.data.kill))
        case None => s
      }
    }

    newSnakes.foreach(s => if (!s.isFiled) grid += s.data.header -> Body(s.data.id) else grid += s.data.header -> Field(s.data.id))
    snakes = newSnakes.map(s => (s.data.id, s.data)).toMap

    isFinish
  }

  def getGridData = {
    var bodyDetails: List[Bd] = Nil
    var fieldDetails: List[Fd] = Nil
    var bordDetails: List[Bord] = Nil
    grid.foreach {
      case (p, Body(id)) => bodyDetails ::= Bd(id, p.x.toInt, p.y.toInt)
      case (p, Field(id)) => fieldDetails ::= Fd(id, p.x.toInt, p.y.toInt)
      case (p, Border) => bordDetails ::= Bord(p.x.toInt, p.y.toInt)
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

  def returnBackField(snakeId: Long) ={
    grid --= grid.filter(_._2 match { case Body(bodyId) if bodyId == snakeId => true case _ => false }).keys
    bodyField.get(snakeId) match {
      case Some(points) => //圈地还原
        points.foreach(p => grid += p._1 -> Field(p._2))
      case _ =>
    }
    bodyField -= snakeId
  }

  def recallGrid(startFrame: Long, endFrame: Long) = {
    historyStateMap.get(startFrame) match {
      case Some(state) =>
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

