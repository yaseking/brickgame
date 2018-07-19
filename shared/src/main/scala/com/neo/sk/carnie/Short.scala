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

  def nextPreferDirection(lastDirection: Point): List[Point] ={ //以左优先
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

  def findShortestPath(initStart: Point, end: Point, fieldBoundary: List[Point], initLastDirection: Point): List[Point] = {
    val targetPath = mutable.Queue[Point]()
    var start = initStart
    var lastDirection = initLastDirection
    var flag = false //是否找不到
    while (start != end) {
      val direction = findDirection(start, nextPreferDirection(lastDirection), fieldBoundary)
      val nextPoint = start + direction
      if (targetPath.isEmpty) targetPath.enqueue(start)
      else {
        if (nextPoint != targetPath.head) targetPath.enqueue(start) else targetPath.dequeue()
      }
      start = start + direction
      lastDirection = direction
    }
    targetPath.enqueue(end)
    targetPath.toList
  }

//  def findShortestPath(start: Point, end: Point, fieldBoundary: List[Point], lastDirection: Point, targetPath: List[Point]): List[Point] = {
//    var res = targetPath
//    if(start != end){
//      val direction = findDirection(start, nextPreferDirection(lastDirection), fieldBoundary)
//      res = findShortestPath(start + direction, end, fieldBoundary, direction, start + direction :: targetPath)
//    }
//    res
//  }

  def findDirection(point: Point, direction: List[Point], fieldBoundary: List[Point]): Point = {
    var res = direction.head
    if(!fieldBoundary.contains(point + res) && direction.nonEmpty){
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

  def breadthFirst(startPointOpt: Option[Point], boundary: List[Point], snakeId: Long, grid: Map[Point, Spot]) = {
    //除了第一点的孩子是上下左右。其余的上的孩子是上，左的孩子是左+上，下的孩子是下，右的孩子是右+下
    var newGrid = grid
    val colorQueue = new mutable.Queue[Point]()
    var colorField = ArrayBuffer[Point]()
    startPointOpt match {
      case Some(startPoint) =>
        colorQueue.enqueue(startPoint)
        colorField += startPoint
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
        colorField.foreach{p =>
          newGrid.get(p) match{
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

//  def breadthFirst(startPointOpt: Option[Point], boundary: List[Point], snakeId: Long, grid: Map[Point, Spot]) = {
//    var newGrid = grid
//    startPointOpt match {
//      case Some(startPoint) =>
//        colorField += snakeId -> (startPoint :: colorField.getOrElse(snakeId, List.empty[Point]))
//        println(startPoint)
//        baseDirection.foreach { d =>
//          val nextPoint = startPoint + d._2
//          if (!boundary.contains(nextPoint) && !colorField(snakeId).contains(nextPoint))
//            goToColor(nextPoint, boundary, snakeId)
//        }
//        colorField(snakeId).foreach(p => newGrid += p -> Field(snakeId))
//        colorField -= snakeId
//
//      case None =>
//        println("point is None!!!!!!!!!!!!!")
//    }
//    boundary.foreach(b => newGrid += b -> Field(snakeId))
//    newGrid
//  }
//
//  def goToColor(point: Point, boundary: List[Point], snakeId: Long): Unit = {
//    println(point)
//    colorField += snakeId -> (point :: colorField.getOrElse(snakeId, List.empty[Point]))
//    baseDirection.foreach { d =>
//      val nextPoint = point + d._2
//      if (!boundary.contains(nextPoint) && !colorField(snakeId).contains(nextPoint)) {
//        colorField += snakeId -> (nextPoint :: colorField.getOrElse(snakeId, List.empty[Point]))
//        goToColor(nextPoint, boundary, snakeId)
//      }
//    }
//  }


}

