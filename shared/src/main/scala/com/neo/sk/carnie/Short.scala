package com.neo.sk.carnie

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
  * Created by dry on 2018/7/18.
  **/
object Short {

  val baseDirection = Map("left" -> Point(-1, 0), "right" -> Point(1, 0), "up" -> Point(0, -1), "down" -> Point(0, 1))
  val random = new Random(System.nanoTime())

  def findFieldBoundary(fieldIds: Iterable[Point]) = {
    var fieldBoundary = List.empty[Point]
    fieldIds.foreach { p =>
      if (!fieldIds.exists(_ == p + Point(-1, -1)) || !fieldIds.exists(_ == p + Point(-1, 1)) || !fieldIds.exists(_ == p + Point(1, -1)) || !fieldIds.exists(_ == p + Point(1, 1))) {
        fieldBoundary = p :: fieldBoundary
      }
    }
    fieldBoundary
  }

  def startPointOnBoundary(startPoint: Point, body: List[Point]) = {
    if (body.contains(startPoint + baseDirection("up"))) { //起点为那个方向的边界
      baseDirection("down")
    } else if (body.contains(startPoint + baseDirection("down"))) {
      baseDirection("up")
    } else if (body.contains(startPoint + baseDirection("left"))) {
      baseDirection("right")
    } else {
      baseDirection("left")
    }
  }

  def nextLeftPreferDirection(lastDirection: Point): List[Point] = { //以左优先
    baseDirection.filter(_._2 == lastDirection).head._1 match {
      case "down" =>
        List(baseDirection("right"), baseDirection("down"), baseDirection("left"), baseDirection("up"))

      case "up" =>
        List(baseDirection("left"), baseDirection("up"), baseDirection("right"), baseDirection("down"))

      case "left" =>
        List(baseDirection("down"), baseDirection("left"), baseDirection("up"), baseDirection("right"))

      case "right" =>
        List(baseDirection("up"), baseDirection("right"), baseDirection("down"), baseDirection("left"))
    }
  }

  def nextRightPreferDirection(lastDirection: Point): List[Point] = { //以右优先
    baseDirection.filter(_._2 == lastDirection).head._1 match {
      case "down" =>
        List(baseDirection("left"), baseDirection("down"), baseDirection("right"), baseDirection("up"))

      case "up" =>
        List(baseDirection("right"), baseDirection("up"), baseDirection("left"), baseDirection("down"))

      case "left" =>
        List(baseDirection("up"), baseDirection("left"), baseDirection("down"), baseDirection("right"))

      case "right" =>
        List(baseDirection("down"), baseDirection("right"), baseDirection("up"), baseDirection("left"))
    }
  }


  def findShortestPath(initStart: Point, end: Point, fieldBoundary: List[Point], initLastDirection: Point, clockwise: Point) = {
    val targetPath = mutable.Stack[Point]()
    var start = initStart
    var lastDirection = initLastDirection
    var flag = true //记录是否找得到闭合回路
    var flagPoint = (Point(0, 0), Point(0, 0)) //(point,direction)
    var count = 0
    val isClockwise = if (clockwise == baseDirection("right") || clockwise == baseDirection("down")) true else false //是否是顺时针行走的
    while (start != end && flag) {
      count += 1
      val direction = findDirection(start, if (isClockwise) nextLeftPreferDirection(lastDirection) else nextRightPreferDirection(lastDirection), fieldBoundary)
      val nextPoint = start + direction
      if (count == 2) flagPoint = (start, direction) //记录第二点的位置和方向，再次同方向到达此点时找不到闭合回路
      if (targetPath.isEmpty) targetPath.push(start)
      else {
        if (start == flagPoint._1 && direction == flagPoint._2 && count != 2) flag = false
        if (nextPoint != targetPath.top) {
          targetPath.push(start)
        } else targetPath.pop()
      }
      start = nextPoint
      lastDirection = direction
    }
    targetPath.push(end)
    (targetPath.reverse.toList, flag)
  }

  def findDirection(point: Point, direction: List[Point], fieldBoundary: List[Point]): Point = {
    var res = direction.head
    if (!fieldBoundary.contains(point + res) && direction.nonEmpty) {
      res = findDirection(point, direction.tail, fieldBoundary)
    }
    res
  }

  def findRandomPoint(snakeBoundary: List[Point], originSnakeBoundary: List[Point]): Option[Point] = {
    if (snakeBoundary.nonEmpty) {
      val findPoint = snakeBoundary(random.nextInt(snakeBoundary.length))
      if (findPoint.x == 0 || findPoint.y == 0 || findPoint.x == Boundary.w || findPoint.y == Boundary.h) { //剔除边界点
        findRandomPoint(snakeBoundary.filterNot(_ == findPoint), originSnakeBoundary)
      } else {
        if (originSnakeBoundary.contains(findPoint + baseDirection("left")) && originSnakeBoundary.contains(findPoint + baseDirection("right")) &&
          !originSnakeBoundary.contains(findPoint + baseDirection("up")) && !originSnakeBoundary.contains(findPoint + baseDirection("down"))) { //横线上的点
          findInsidePoint(Point(findPoint.x, findPoint.y + 1), Point(findPoint.x, findPoint.y - 1), originSnakeBoundary) match {
            case Right(p) => Some(p)
            case Left(_) => findRandomPoint(snakeBoundary.filterNot(_ == findPoint), originSnakeBoundary)
          }
        } else if (!originSnakeBoundary.contains(findPoint + baseDirection("left")) && !originSnakeBoundary.contains(findPoint + baseDirection("right")) &&
          originSnakeBoundary.contains(findPoint + baseDirection("up")) && originSnakeBoundary.contains(findPoint + baseDirection("down"))) { //竖线上的点
          findInsidePoint(Point(findPoint.x + 1, findPoint.y), Point(findPoint.x - 1, findPoint.y), originSnakeBoundary) match {
            case Right(p) => Some(p)
            case Left(_) => findRandomPoint(snakeBoundary.filterNot(_ == findPoint), originSnakeBoundary)
          }
        } else { //转折点-重新找点
          findRandomPoint(snakeBoundary.filterNot(_ == findPoint), originSnakeBoundary)
        }
      }
    } else {
      None
    }
  }

  def findInsidePoint(point1: Point, point2: Point, boundary: List[Point]) = {
    if (boundary.count(p => p.x == point1.x && p.y > point1.y) % 2 == 1 &&
      boundary.count(p => p.x == point1.x && p.y < point1.y) % 2 == 1 &&
      boundary.count(p => p.y == point1.y && p.x > point1.x) % 2 == 1 &&
      boundary.count(p => p.y == point1.y && p.x < point1.x) % 2 == 1) { //射线上相交个数均为奇数的点为内部点
      Right(point1)
    } else if (boundary.count(p => p.x == point2.x && p.y > point2.y) % 2 == 1 && boundary.count(p => p.x == point2.x && p.y < point2.y) % 2 == 1 &&
      boundary.count(p => p.y == point2.y && p.x > point2.x) % 2 == 1 && boundary.count(p => p.y == point2.y && p.x < point2.x) % 2 == 1) {
      Right(point2)
    } else { //存在相交到了连续的点
      Left("find again.")
    }
  }


  def breadthFirst(startPointOpt: Option[Point], boundary: List[Point], snakeId: Long, grid: Map[Point, Spot], turnPoint: List[Point]) = {
    var newGrid = grid
    val colorQueue = new mutable.Queue[Point]()
    var colorField = ArrayBuffer[Point]()
    startPointOpt match {
      case Some(startPoint) =>
        colorQueue.enqueue(startPoint)
        colorField += startPoint
        getBodyTurnPoint(turnPoint, boundary).foreach(i => colorQueue.enqueue(i))
        while (colorQueue.nonEmpty) {
          val currentPoint = colorQueue.dequeue()
          List(Point(-1, 0), Point(0, -1), Point(0, 1), Point(1, 0)).foreach { d =>
            val newPoint = currentPoint + d
            if (!boundary.contains(newPoint) && !colorField.contains(newPoint)) {
              colorField += newPoint
              colorQueue.enqueue(newPoint)
            }
          }
        }
        colorField.foreach { p =>
          newGrid.get(p) match {
            case Some(Field(fid)) if fid == snakeId => //donothing
            case Some(Body(_)) =>
            case _ => newGrid += p -> Field(snakeId)
          }
        }
        colorField.clear()

      case None =>
    }
    boundary.foreach(b => newGrid += b -> Field(snakeId))
    newGrid
  }

  def getBodyTurnPoint(turnPoints: List[Point], boundary: List[Point]) = {
    var res = List.empty[Point]
    turnPoints.foreach { t =>
      val exist = baseDirection.map(d => (d._1, boundary.contains(d._2 + t)))
      if (exist.count(_._2) == 3 && isInsidePoint(baseDirection(exist.filter(!_._2).head._1) + t, boundary))
        res = baseDirection(exist.filter(!_._2).head._1) + t :: res
    }
    res
  }

  def isInsidePoint(point: Point, boundary: List[Point]) = {
    if (boundary.count(p => p.x == point.x && p.y > point.y) % 2 == 1 &&
      boundary.count(p => p.x == point.x && p.y < point.y) % 2 == 1 &&
      boundary.count(p => p.y == point.y && p.x > point.x) % 2 == 1 &&
      boundary.count(p => p.y == point.y && p.x < point.x) % 2 == 1) {
      true
    } else false
  }


}

