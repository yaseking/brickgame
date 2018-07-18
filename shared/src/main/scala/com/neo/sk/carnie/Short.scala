package com.neo.sk.carnie

import scala.collection.mutable
import scala.util.Random

/**
  * Created by dry on 2018/7/18.
  **/
object Short {

  val baseDirection = Map("left" -> Point(-1, 0), "right" -> Point(1, 0), "up" -> Point(0, -1), "down" -> Point(0, 1))
  val random = new Random(System.nanoTime())
  var colorField = Map.empty[Long, List[Point]]


  def findFieldBoundary(fieldIds: Iterable[Point]) = {
    var fieldBoundary = List.empty[Point]
    fieldIds.foreach{ p =>
      if(baseDirection.map(d => fieldIds.exists(_ == p + d._2)).exists(_ == false)){
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

  def nextPreferDirection(lastDirection: Point): List[Point] ={
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

  def findShortestPath(start: Point, end: Point, fieldBoundary: List[Point], lastDirection: Point, targetPath: List[Point]): List[Point] = {
    var res = targetPath
    if(start != end){
      val direction = findDirection(start, nextPreferDirection(lastDirection), fieldBoundary)
      res = findShortestPath(start + direction, end, fieldBoundary, direction, start + direction :: targetPath)
    }
    res
  }

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
          Some(findInsidePoint(Point(findPoint.x, findPoint.y + 1), Point(findPoint.x, findPoint.y - 1), snakeBoundary))
        } else if (!originSnakeBoundary.contains(findPoint + baseDirection("left")) && !originSnakeBoundary.contains(findPoint + baseDirection("right")) &&
          originSnakeBoundary.contains(findPoint + baseDirection("up")) && originSnakeBoundary.contains(findPoint + baseDirection("down"))) { //竖线上的点
          Some(findInsidePoint(Point(findPoint.x + 1, findPoint.y), Point(findPoint.x - 1, findPoint.y), snakeBoundary))
        } else { //转折点-重新找点
          findRandomPoint(snakeBoundary.filterNot(_ == findPoint), originSnakeBoundary)
        }
      }
    } else {
      None
    }
  }

  def findInsidePoint(point1: Point, point2: Point, boundary: List[Point]): Point = {
    if (boundary.count(p => p.x == point1.x && p.y > point1.y) % 2 == 1 &&
      boundary.count(p => p.x == point1.x && p.y < point1.y) % 2 == 1 &&
      boundary.count(p => p.y == point1.y && p.x > point1.x) % 2 == 1 &&
      boundary.count(p => p.y == point1.y && p.x < point1.x) % 2 == 1) { //射线上相交个数均为奇数的点为内部点
      point1
    } else {
      point2
    }
  }

  def breadthFirst(startPointOpt: Option[Point], boundary: List[Point], snakeId: Long, grid: Map[Point, Spot]) = {
    //除了第一点的孩子是上下左右。其余的上的孩子是上，左的孩子是左+上，下的孩子是下，右的孩子是右+下
    var newGrid = grid
    val colorQueue = new mutable.Queue[(String, Point)]()
    startPointOpt match {
      case Some(startPoint) =>
        newGrid += startPoint -> Field(snakeId)
        baseDirection.foreach(d => if (!boundary.contains(startPoint + d._2)) colorQueue.enqueue((d._1, startPoint + d._2)))

        while (colorQueue.nonEmpty) {
          val currentPoint = colorQueue.dequeue()
          newGrid += currentPoint._2 -> Field(snakeId)
          currentPoint._1 match {
            case "left" =>
              if (!boundary.contains(currentPoint._2 + baseDirection("left"))) colorQueue.enqueue(("left", currentPoint._2 + baseDirection("left")))
              if (!boundary.contains(currentPoint._2 + baseDirection("up"))) colorQueue.enqueue(("up", currentPoint._2 + baseDirection("up")))
              if (!boundary.contains(currentPoint._2 + baseDirection("down"))) colorQueue.enqueue(("down", currentPoint._2 + baseDirection("down")))

            case "right" =>
              if (!boundary.contains(currentPoint._2 + baseDirection("right"))) colorQueue.enqueue(("right", currentPoint._2 + baseDirection("right")))
              if (!boundary.contains(currentPoint._2 + baseDirection("down"))) colorQueue.enqueue(("down", currentPoint._2 + baseDirection("down")))
              if (!boundary.contains(currentPoint._2 + baseDirection("up"))) colorQueue.enqueue(("up", currentPoint._2 + baseDirection("up")))

            case "up" =>
              if (!boundary.contains(currentPoint._2 + baseDirection("up"))) colorQueue.enqueue(("up", currentPoint._2 + baseDirection("up")))

            case "down" =>
              if (!boundary.contains(currentPoint._2 + baseDirection("down"))) colorQueue.enqueue(("down", currentPoint._2 + baseDirection("down")))
          }
        }
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

//val snakeField = grid.filter(_._2 match { case Field(fid) if fid == snake.id => true case _ => false }).keys
//val snakeBody = grid.filter(_._2 match { case Body(bodyId) if bodyId == snake.id => true case _ => false }).keys.toList
//val snakeFieldBoundary = Short.findFieldBoundary(snakeField)
//println("snakeFieldBoundary" + snakeFieldBoundary.length)
//println("snakeFieldBoundary" + snakeFieldBoundary.sortBy(_.x))
//val path = Short.findShortestPath(snake.startPoint, newHeader, snakeFieldBoundary,
//Short.startPointOnBoundary(snake.startPoint, snakeBody), Nil) ::: List(snake.startPoint)
//val closed = path ::: snakeBody
//println("startPoint" + snake.startPoint)
//println("endPoint" + newHeader)
//println("path" + path)
//println("closed" + closed)
//val randomPoint = Short.findRandomPoint(closed, closed)
//println("randomPoint" + randomPoint)
//grid = Short.breadthFirst(randomPoint, closed, snake.id, grid)