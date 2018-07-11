package com.neo.sk.carnie

import java.awt.event.KeyEvent

import scala.collection.mutable
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


  val defaultLength = 5
  val appleNum = 6
  val appleLife = 50
  val historyRankLength = 5

  var frameCount = 0l
  var grid = Map[Point, Spot]()
  var snakes = Map.empty[Long, SkDt]
  var actionMap = Map.empty[Long, Map[Long, Int]]
  var snakeStart = Map.empty[Long, Point]
  val baseDirection = Map("left" -> Point(-1, 0), "right" -> Point(1, 0), "up" -> Point(0, -1), "down" -> Point(0, 1))

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


  def update() = {
    //println(s"-------- grid update frameCount= $frameCount ---------")
    updateSnakes()
    updateSpots()
    actionMap -= frameCount
    frameCount += 1
  }

  private[this] def updateSpots() = {
//    debug(s"grid: ${grid.mkString(";")}")
    grid = grid.filter { case (p, spot) =>
      spot match {
        case Body(id) if snakes.contains(id) => true
        //case Header(id, _) if snakes.contains(id) => true
        case Field(id)  if snakes.contains(id) => true
        case _ => false
      }
    }.map {
      //case (p, Header(id, life)) => (p, Body(id, life - 1))
      case (p, b@Body(_)) => (p, b)
      case (p, f@Field(_)) => (p, f)
      case x => x
    }

  }


  def randomEmptyPoint(size: Int): Point = {
    var p = Point(random.nextInt(boundary.x - (size+1)), random.nextInt(boundary.y - (size+1)))
    while ((0 to size).flatMap { x =>
      (0 to size).map { y =>
        grid.contains(p.copy(x = p.x + x, y = p.y + y))
      }}.contains(true)) {
      p = Point(random.nextInt(boundary.x - (size+1)), random.nextInt(boundary.y - (size+1)))
    }
    p
  }

  def randomColor(): String = {
    var color = "#" + randomHex
    while (snakes.map(_._2.color).toList.contains(color) || color == "#000000" || color == "#000080") {
      color = "#" + randomHex
    }
    color
  }

  def randomHex() = {
    val h = (new util.Random).nextInt(256).toHexString + (new util.Random).nextInt(256).toHexString + (new util.Random).nextInt(256).toHexString
    String.format("%6s", h).replaceAll("\\s", "0")
  }


  private[this] def updateSnakes() = {
    def updateASnake(snake: SkDt, actMap: Map[Long, Int]): Either[Option[Long], UpdateSnakeInfo] = {
      val keyCode = actMap.get(snake.id)
//      debug(s" +++ snake[${snake.id} -- color is ${snake.color} ] feel key: $keyCode at frame=$frameCount")
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

      val newHeader = ((snake.header + newDirection) + boundary) % boundary

      grid.get(newHeader) match {
        case Some(x: Body) => //进行碰撞检测
          debug(s"snake[${snake.id}] hit wall.")
          Left(Some(x.id))

        case Some(Field(id)) =>
          if(id == snake.id && (grid(snake.header) match{case Body(bid) if bid == snake.id  => true  case _ => false})){
            //todo 回到了自己的领域，根据起点和终点最近的连线与body路径围成一个闭合的图形，进行圈地并且自己的领地不会被重置为body
            snakeStart.get(snake.id) match {
              case Some(startPoint) =>
                val bodys = grid.filter(_._2 match{case Body(bid) if bid == snake.id  => true  case _ => false}).keys.toList
                val boundary = snake.boundary
                debug("game boundary" + boundary)
                debug("start-" + startPoint)
                debug("end-" + newHeader)
                debug("body-" + bodys)
                debug("boundary-" + snake.boundary)
                val newCalFieldBoundary  = findShortestPath(startPoint, newHeader, boundary)._1 ++ bodys
                val newTotalFieldBoundary = findShortestPath(startPoint, newHeader, boundary)._2 ++ bodys
                debug("findShortestPath1" + findShortestPath(startPoint, newHeader, boundary)._1)
                debug("findShortestPath2" + findShortestPath(startPoint, newHeader, boundary)._2)
                debug("newTotalFieldBoundary" + newTotalFieldBoundary)
                debug("newCalFieldBoundary" + newCalFieldBoundary)
                val findPoint = findRandomPoint(newCalFieldBoundary)
                debug("point is" + findPoint)
                breadthFirst(findPoint, newCalFieldBoundary, snake.id)
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, boundary = newTotalFieldBoundary), true))

              case None =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), true))
            }
          } else { //进入到别人的领域
            grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
              case Some(Field(fid)) if fid == snake.id => snakeStart += id -> snake.header
              case _ =>
            }
            Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
          }
        case _ => //判断是否进入到了边界
          if(newHeader.x == 0 || newHeader.x == boundary.x){
            Left(None)
          } else if(newHeader.y == 0 || newHeader.y == boundary.y){
            Left(None)
          } else{
            grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
              case Some(Field(fid)) if fid == snake.id => snakeStart += fid -> snake.header
              case _ =>
            }
            Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
          }
      }
    }


    var mapKillCounter = Map.empty[Long, Int]
    var updatedSnakes = List.empty[UpdateSnakeInfo]

    val acts = actionMap.getOrElse(frameCount, Map.empty[Long, Int])

    snakes.values.map(updateASnake(_, acts)).foreach {
      case Right(s) => updatedSnakes ::= s
      case Left(Some(killerId)) =>
        mapKillCounter += killerId -> (mapKillCounter.getOrElse(killerId, 0) + 1)
      case Left(None) =>
    }


    //if two (or more) headers go to the same point,
    val snakesInDanger = updatedSnakes.groupBy(_.data.header).filter(_._2.size > 1).values

    val deadSnakes =
      snakesInDanger.flatMap { hits =>
        val sorted = hits.map(_.data).sortBy(_.length)
        val winner = sorted.head
        val deads = sorted.tail
        mapKillCounter += winner.id -> (mapKillCounter.getOrElse(winner.id, 0) + deads.length)
        deads
      }.map(_.id).toSet


    val newSnakes = updatedSnakes.filterNot(s => deadSnakes.contains(s.data.id)).map { s =>
      mapKillCounter.get(s.data.id) match {
        case Some(k) => s.copy(data = s.data.copy(kill = k + s.data.kill))
        case None => s
      }
    }

    newSnakes.foreach(s => if(!s.isFiled) grid += s.data.header -> Body(s.data.id))
    snakes = newSnakes.map(s => (s.data.id, s.data)).toMap

  }


  def updateAndGetGridData() = {
    update()
    getGridData
  }

  def getGridData = {
    var bodyDetails: List[Bd] = Nil
    var fieldDetails: List[Fd] = Nil
    grid.foreach {
      case (p, Body(id)) => bodyDetails ::= Bd(id, p.x, p.y)
      case (p, Field(id)) => fieldDetails ::= Fd(id, p.x, p.y)
      case (p, Header(id)) => bodyDetails ::= Bd(id, p.x, p.y)
    }
    Protocol.GridDataSync(
      frameCount,
      snakes.values.toList,
      bodyDetails,
      fieldDetails
    )
  }

  def findShortestPath(start:Point, end: Point, fieldBoundary: List[Point]) = {
    var initDirection = List.empty[Point]
    baseDirection.values.foreach { p =>
      if (fieldBoundary.contains(start + p)) initDirection = p :: initDirection
    }
    if (initDirection.lengthCompare(2) == 0) {
      val route1 = getShortest(start + initDirection.head, end, fieldBoundary, List(start + initDirection.head), initDirection.head)
      val route2 = getShortest(start + initDirection.last, end, fieldBoundary, List(start + initDirection.last), initDirection.last)
      if(route1.lengthCompare(route2.length) > 0) (route2, route1) else (route1, route2)
    } else {
      (Nil, Nil)
    }
  }

  def getShortest(start: Point, end: Point, fieldBoundary: List[Point], targetPath: List[Point], lastDirection: Point): List[Point] = {
    var res = targetPath
    val resetDirection = if (lastDirection.x != 0) Point(-lastDirection.x, lastDirection.y) else Point(lastDirection.x, -lastDirection.y)
    if (start - end != Point(0, 0)) {
      var direction = Point(-1, -1)
      baseDirection.values.filterNot(_ == resetDirection).foreach { d => if (fieldBoundary.contains(start + d)) direction = d }
      if (direction != Point(-1, -1)) {
        res = getShortest(start + direction, end, fieldBoundary, start + direction :: targetPath, direction)
      } else {
        return Nil
      }
    }
    res
  }

  def findRandomPoint(boundary: List[Point]): Point = {
    var findPoint = boundary(random.nextInt(boundary.length))
    if(findPoint.x == 0 || findPoint.y ==0 || findPoint.x == Boundary.w || findPoint.y == Boundary.h){ //剔除边界点
      findPoint = findRandomPoint(boundary)
    } else {
      if (boundary.contains(findPoint + baseDirection("left")) && boundary.contains(findPoint + baseDirection("right")) &&
        !boundary.contains(findPoint + baseDirection("up")) && !boundary.contains(findPoint + baseDirection("down"))) { //横线上的点
        findPoint = findInsidePoint(Point(findPoint.x, findPoint.y + 1),Point(findPoint.x, findPoint.y - 1), boundary)
      } else if (!boundary.contains(findPoint + baseDirection("left")) && !boundary.contains(findPoint + baseDirection("right")) &&
        boundary.contains(findPoint + baseDirection("up")) && boundary.contains(findPoint + baseDirection("down"))) { //竖线上的点
        findPoint = findInsidePoint(Point(findPoint.x + 1, findPoint.y),Point(findPoint.x - 1, findPoint.y), boundary)
      } else { //转折点-重新找点
        findPoint = findRandomPoint(boundary)
      }
    }
    findPoint
  }

  def findInsidePoint(point1: Point, point2: Point, boundary: List[Point]): Point = {
    if (boundary.count(p => p.x == point1.x && p.y > point1.y) % 2 == 1 &&
      boundary.count(p => p.x == point1.x && p.y < point1.y) % 2 == 1 &&
      boundary.count(p => p.y == point1.y && p.x > point1.x) % 2 == 1 &&
      boundary.count(p => p.y == point1.y && p.x < point1.x) % 2 == 1){ //射线上相交个数均为奇数的点为内部点
      point1
    } else {
      point2
    }
  }

  def breadthFirst(startPoint: Point, boundary: List[Point], snakeId: Long) = {
    //除了第一点的孩子是上下左右。其余的上的孩子是上，左的孩子是左+上，下的孩子是下，右的孩子是右+下
    val colorQueue = new mutable.Queue[(String, Point)]()
    grid += startPoint -> Field(snakeId)
    baseDirection.foreach(d => if(!boundary.contains(startPoint + d._2)) colorQueue.enqueue((d._1, startPoint + d._2)))

    while(colorQueue.nonEmpty){
      val currentPoint = colorQueue.dequeue()
      grid += currentPoint._2 -> Field(snakeId)
      currentPoint._1 match {
        case "left" =>
          if(!boundary.contains(currentPoint._2 + baseDirection("left"))) colorQueue.enqueue(("left", currentPoint._2 + baseDirection("left")))
          if(!boundary.contains(currentPoint._2 + baseDirection("up"))) colorQueue.enqueue(("up", currentPoint._2 + baseDirection("up")))
        case "right" =>
          if(!boundary.contains(currentPoint._2 + baseDirection("right"))) colorQueue.enqueue(("right", currentPoint._2 + baseDirection("right")))
          if(!boundary.contains(currentPoint._2 + baseDirection("down"))) colorQueue.enqueue(("down", currentPoint._2 + baseDirection("down")))
        case "up" =>
          if(!boundary.contains(currentPoint._2 + baseDirection("up"))) colorQueue.enqueue(("up", currentPoint._2 + baseDirection("up")))
        case "down" =>
          if(!boundary.contains(currentPoint._2 + baseDirection("down"))) colorQueue.enqueue(("down", currentPoint._2 + baseDirection("down")))
      }
    }

    boundary.foreach(b => grid += b -> Field(snakeId))
  }
}
