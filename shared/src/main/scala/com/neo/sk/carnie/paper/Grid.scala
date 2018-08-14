package com.neo.sk.carnie.paper

import java.awt.event.KeyEvent
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
  var mayBeSuccess = Map.empty[Long, Set[Point]] //圈地成功后的被圈点
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
    updateSnakes()
    updateSpots()
    actionMap -= frameCount - maxDelayed
    historyStateMap -= frameCount - (maxDelayed + 1)
    frameCount += 1
  }

  private[this] def updateSpots() = {
    //    debug(s"grid: ${grid.mkString(";")}")
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
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header)))
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
                    val snakeField = grid.filter(_._2 match { case Field(fid) if fid == snake.id => true case _ => false }).keys
                    val snakeBody = grid.filter(_._2 match { case Body(bodyId) if bodyId == snake.id => true case _ => false }).keys.toList
                    //                  println("begin" + System.currentTimeMillis())
                    val findShortPath = Short.findShortestPath(snake.startPoint, newHeader, snakeField.toList, Short.startPointOnBoundary(snake.startPoint, snakeBody), snake.clockwise)
                    //                  println("findShortPath" + System.currentTimeMillis())
                    if (findShortPath._2) {
                      val closed = findShortPath._1 ::: snakeBody
                      val randomPoint = Short.findRandomPoint(closed, closed)
                      //                    println("randomPoint" + System.currentTimeMillis())
                      val newGrid = Short.breadthFirst(randomPoint, closed, snake.id, grid, snake.turnPoint)
                      //                println("start--" + snake.startPoint)
                      //                println("end--" + newHeader)
                      //                    println("done" + System.currentTimeMillis())
                      mayBeSuccess += (snake.id -> newGrid.keys.filterNot(p=> grid.keys.exists(_ == p)).toSet)
                      grid = newGrid
                    } else returnBackField(snake.id)
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
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header)))
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
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header)))
            case _ =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
          }
      }
      value match {
        case Right(v) =>
          var newData = v
          if(v.data.clockwise + newDirection == Point(0,0) || v.data.clockwise == Point(0,0)){ //判断是顺时针还是逆时针行走
            newData = newData.copy(newData.data.copy(clockwise = newDirection))
          }
          if (turnPoint.nonEmpty && !newData.isFiled){ //记录纸袋的拐点
            newData = newData.copy(newData.data.copy(turnPoint = newData.data.turnPoint ::: List(turnPoint.get)))
          }
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

    val interset = mayBeSuccess.keys.filter(p => mayBeDieSnake.keys.exists(_ == p))
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
    mayBeSuccess = Map.empty[Long, Set[Point]]


    //if two (or more) headers go to the same point,die at the same time
    val snakesInDanger = updatedSnakes.groupBy(_.data.header).filter(_._2.lengthCompare(1) > 0).values
    val deadSnakes = snakesInDanger.flatMap { hits => hits.map(_.data.id) }.toSet
    val noFieldSnake = snakes.keys.toSet &~ grid.map(_._2 match { case x@Field(uid) => uid case _ => 0 }).toSet.filter(_ != 0) //若领地全被其它玩家圈走则死亡
    noFieldSnake.foreach {s => returnBackField(s) }

    val newSnakes = updatedSnakes.filterNot(s => deadSnakes.contains(s.data.id) || killedSnaked.contains(s.data.id) || noFieldSnake.contains(s.data.id)).map { s =>
      mapKillCounter.get(s.data.id) match {
        case Some(k) => s.copy(data = s.data.copy(kill = k + s.data.kill))
        case None => s
      }
    }

    newSnakes.foreach(s => if (!s.isFiled) grid += s.data.header -> Body(s.data.id) else grid += s.data.header -> Field(s.data.id))
    snakes = newSnakes.map(s => (s.data.id, s.data)).toMap

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

    }
  }


}

