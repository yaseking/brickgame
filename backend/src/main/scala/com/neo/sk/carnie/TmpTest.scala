package com.neo.sk.carnie

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
  * User: Taoz
  * Date: 6/28/2018
  * Time: 7:26 PM
  */
object TmpTest {

  val baseDirection = Map("left" -> Point(-1, 0), "right" -> Point(1, 0), "up" -> Point(0, -1), "down" -> Point(0, 1))
  val random = new Random(System.nanoTime())
  var colorField = Map.empty[Long, List[Point]]

  import io.circe.generic.auto._
  import io.circe.parser._
  import io.circe.syntax._


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

  def ffindS(start: Point, end: Point, fieldBoundary: List[Point], lastDirection: Point, targetPath: List[Point]): List[Point] = {
    var res = targetPath
    if(start != end){
      val direction = findDirection(start, nextPreferDirection(lastDirection), fieldBoundary)
      res = ffindS(start + direction, end, fieldBoundary, direction, start + direction :: targetPath)
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

  def findVertex(shape: List[Point]) = {
    var vertex = List.empty[Point]
    shape.foreach { p =>
      val countX = List(baseDirection("up"), baseDirection("down")).map{ d => shape.contains(p + d)}.count(i => i)
      val countY = List(baseDirection("left"), baseDirection("right")).map{ d => shape.contains(p + d)}.count(i => i)
      if(countX == 1 && countY ==1) vertex = p :: vertex
    }
    vertex
  }

  def findShortestPath(start: Point, end: Point, fieldBoundary: List[Point], turnPoint: List[(Point, Point)]) = {
    val startDirection = findClockwiseDirection(start, fieldBoundary)
    if(startDirection != Point(-1, -1)){
      val route1 = getShortest(start + startDirection, end, fieldBoundary, List(start + startDirection, start), startDirection, turnPoint)
      if(route1.nonEmpty){
        val route2 = (fieldBoundary.toSet &~ route1.toSet).toList ::: List(start, end)
        if(route1.lengthCompare(route2.length) > 0) (route2, route1) else (route1, route2)
      } else {
        (Nil, Nil)
      }
    } else{
      (Nil, Nil)
    }
  }

  def getShortest(start: Point, end: Point, fieldBoundary: List[Point], targetPath: List[Point], lastDirection: Point, turnPoint: List[(Point, Point)]): List[Point] = {
    var res = targetPath
    val nextDirection = if (turnPoint.exists(_._1 == start)) turnPoint.filter(_._1 == start).head._2 else lastDirection
    if (start - end != Point(0, 0)) {
      if (fieldBoundary.contains(start + nextDirection) && !targetPath.contains(start + nextDirection)) {
        res = getShortest(start + nextDirection, end, fieldBoundary, start + nextDirection :: targetPath, nextDirection, turnPoint)
      } else {
        val tryAgain = clockwise(start, lastDirection, fieldBoundary)
        if (fieldBoundary.contains(start + tryAgain)  && !targetPath.contains(start + nextDirection)) {
          res = getShortest(start + tryAgain, end, fieldBoundary, start + tryAgain :: targetPath, nextDirection, turnPoint)
        } else
          return Nil
      }
    }
    res
  }

  def clockwise(nowPoint: Point, lastDirection: Point, boundarys: List[Point]) = {
    var direction = Point(-1, -1)
    val directions = baseDirection.map(_._2).filterNot(_ == lastDirection).filter(i => boundarys.contains(nowPoint + i))
    if (directions.size == 1) {
      direction = directions.head
    } else {
      directions.foreach { i =>
        if (boundarys.contains(nowPoint + i) && !boundarys.contains(nowPoint + i - lastDirection))
          direction = i
      }
    }
    direction
  }

  def findClockwiseDirection(startPoint:Point, boundarys: List[Point]) = {
    var target = Point(-1 ,-1)
    baseDirection.filter(d => boundarys.contains(startPoint + d._2)).foreach{ i=>
      i._1 match {
        case "up" =>
          if(!boundarys.contains(startPoint + i._2 + baseDirection("left")))
            target = i._2

        case "down" =>
          if(!boundarys.contains(startPoint + i._2 + baseDirection("right")))
            target = i._2

        case "left" =>
          if(!boundarys.contains(startPoint + i._2 + baseDirection("down")))
            target = i._2

        case "right" =>
          if(!boundarys.contains(startPoint + i._2 + baseDirection("up")))
            target = i._2
      }
    }
    target
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

  def breadthFirst(startPointOpt: Option[Point], boundary: List[Point], snakeId: Long) = {
    startPointOpt match {
      case Some(startPoint) =>
        colorField += snakeId -> (startPoint :: colorField.getOrElse(snakeId, List.empty[Point]))
        println(startPoint)
        baseDirection.foreach { d =>
          val nextPoint = startPoint + d._2
          if (!boundary.contains(nextPoint) && !colorField(snakeId).contains(nextPoint))
            goToColor(nextPoint, boundary, snakeId)
        }
      case None =>
        println("point is None!!!!!!!!!!!!!")
    }
    //    boundary.foreach(b => grid += b -> Field(snakeId))
  }

  def goToColor(point: Point, boundary: List[Point], snakeId: Long): Unit = {
    println(point)
    colorField += snakeId -> (point :: colorField.getOrElse(snakeId, List.empty[Point]))
    baseDirection.foreach { d =>
      val nextPoint = point + d._2
      if (!boundary.contains(nextPoint) && !colorField(snakeId).contains(nextPoint)) {
        colorField += snakeId -> (nextPoint :: colorField.getOrElse(snakeId, List.empty[Point]))
        goToColor(nextPoint, boundary, snakeId)
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val b = List(Point(77, 54), Point(71, 55), Point(77, 55), Point(73, 55), Point(76, 54), Point(71, 54), Point(75, 55), Point(73, 56), Point(76, 55), Point(72, 56), Point(71, 56), Point(74, 55), Point(72, 54), Point(75, 54), Point(75, 53), Point(74, 51), Point(72, 52), Point(73, 51), Point(75, 52), Point(75, 51), Point(72, 51), Point(72, 53))
    println(b.sortBy(_.x))
    val turn = List((Point(70,52),Point(1,0)), (Point(70,55),Point(0,-1)), (Point(75,55),Point(-1,0)), (Point(75,51),Point(0,1)), (Point(72,51),Point(1,0)), (Point(77,55),Point(-1,0)), (Point(77,54),Point(0,1)), (Point(74,55),Point(1,0)), (Point(73,55),Point(-1,0)))
    val s = Point(71, 55)
    val e = Point(75, 52)
    //
    val a = findShortestPath(s, e, b, turn)
    println(a)

    //    val t = List(Point(69,7), Point(68,7), Point(68,6), Point(68,5), Point(79,5), Point(79,6), Point(71,4), Point(78,7), Point(69,4), Point(76,7), Point(73,7), Point(77,4), Point(69,6), Point(72,7), Point(75,4), Point(73,4), Point(79,7), Point(76,4), Point(74,7), Point(74,4), Point(70,4), Point(79,4), Point(71,7), Point(78,4), Point(77,7), Point(75,7), Point(72,4))
    //    println(t.length)
    //val b = List(Point(24,17), Point(25,15), Point(23,17), Point(22,15), Point(20,15), Point(20,17), Point(25,17), Point(27,15), Point(26,17), Point(21,17), Point(23,15), Point(22,17), Point(21,15), Point(27,17), Point(26,15), Point(18,15), Point(24,15), Point(27,16))
    //
    //        println(b.sortBy(_.x))

    //    val c = List(Point(35,28), Point(36,28), Point(36,27), Point(36,26), Point(36,25), Point(36,24), Point(36,23), Point(36,22), Point(36,21), Point(38,25), Point(35,33), Point(36,33), Point(35,31), Point(38,27), Point(38,24), Point(43,32), Point(35,30), Point(38,21), Point(41,21), Point(38,26), Point(35,32), Point(42,30), Point(40,33), Point(40,24), Point(43,33), Point(39,33), Point(39,24), Point(40,21), Point(35,29), Point(38,29), Point(42,33), Point(41,33), Point(41,24), Point(41,22), Point(37,21), Point(39,21), Point(40,30), Point(41,30), Point(37,33), Point(38,30), Point(38,33), Point(41,23), Point(38,28), Point(39,30), Point(43,31), Point(43,30))
    //    val p = findRandomPoint(c, c)
    //    println("random" + p)
    //    breadthFirst(p, c, 0l)
  }
}



