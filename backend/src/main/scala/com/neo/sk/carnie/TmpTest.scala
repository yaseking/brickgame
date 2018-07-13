package com.neo.sk.carnie

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




  def findShortestPath(start: Point, end: Point, fieldBoundary: List[Point], turnPoint: List[(Point, Point)]) = {
    var initDirection = List.empty[Point]
    baseDirection.values.foreach { p =>
      if (fieldBoundary.contains(start + p)) initDirection = p :: initDirection
    }
    if (initDirection.lengthCompare(2) == 0) {
      val route1 = getShortest(start + initDirection.head, end, fieldBoundary, List(start + initDirection.head, start), initDirection.head, turnPoint)
      val route2 = getShortest(start + initDirection.last, end, fieldBoundary, List(start + initDirection.last, start), initDirection.last, turnPoint)
      if (route1.lengthCompare(route2.length) > 0) (route2, route1) else (route1, route2)
    } else {
      (Nil, Nil)
    }
  }

  def shortest(start: Point, end: Point, fieldBoundary: List[Point], targetPath: List[Point], lastDirection: Point, turnPoint: List[(Point, Point)]): List[Point] = {
    var res = targetPath
    var nextDirection = if(turnPoint.map(_._1).contains(start)) turnPoint.filter(_._1 == start).head._2 else lastDirection
    if (start - end != Point(0, 0)) {
      if(fieldBoundary.contains(start + nextDirection)){
        val nextPoint = start + nextDirection
        res = getShortest(nextPoint, end, fieldBoundary.filterNot(_ == nextPoint), nextPoint :: targetPath, nextDirection, turnPoint)
      }else{
        nextDirection = if(nextDirection.x == 0) Point(nextDirection.y, nextDirection.x) else Point(-nextDirection.y, nextDirection.x)
        val nextPoint = start + nextDirection
        if(fieldBoundary.contains(start + nextDirection)){
          res = getShortest(nextPoint, end, fieldBoundary.filterNot(_ == nextPoint), nextPoint :: targetPath, nextDirection, turnPoint)
        } else {
          return Nil
        }
      }
    }else {
      return Nil
    }
    res
  }

  def getShortest(start: Point, end: Point, fieldBoundary: List[Point], targetPath: List[Point], lastDirection: Point, turnPoint: List[(Point, Point)]): List[Point] = {
    var res = targetPath
    val resetDirection = if (lastDirection.x != 0) Point(-lastDirection.x, lastDirection.y) else Point(lastDirection.x, -lastDirection.y)
    val nextDirection = if(turnPoint.map(_._1).contains(start))  List(turnPoint.filter(_._1 == start).head._2) else baseDirection.values.filterNot(_ == resetDirection)
    if (start - end != Point(0, 0)) {
      var direction = Point(-1, -1)
      nextDirection.foreach { d => if (fieldBoundary.contains(start + d)) direction = d }
      if (direction != Point(-1, -1)) {
        res = getShortest(start + direction, end, fieldBoundary, start + direction :: targetPath, direction, turnPoint)
      } else {
        return Nil
      }
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
      boundary.count(p => p.y == point1.y && p.x < point1.x) % 2 == 1){ //射线上相交个数均为奇数的点为内部点
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
          if (!boundary.contains(nextPoint) && !colorField(snakeId).contains(nextPoint)) {
            goToColor(nextPoint, boundary, snakeId)
          }
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
//    val b= List(Point(72,45), Point(72,44), Point(72,43), Point(73,43), Point(73,42), Point(73,41), Point(73,40), Point(73,39), Point(73,38), Point(74,38), Point(75,38), Point(76,38), Point(77,38), Point(78,38), Point(79,38), Point(80,38), Point(81,38), Point(82,38), Point(83,38), Point(83,39), Point(83,40), Point(83,41), Point(83,42), Point(83,43), Point(84,43), Point(85,43), Point(86,43), Point(87,43), Point(88,43), Point(89,43), Point(89,44), Point(89,45), Point(88,45), Point(87,45), Point(86,45), Point(85,45), Point(84,45), Point(83,45), Point(82,45), Point(81,45), Point(80,45), Point(79,45), Point(78,45), Point(77,45), Point(76,45), Point(75,45), Point(74,45), Point(73,45), Point(71,49), Point(73,50), Point(72,52), Point(73,47), Point(71,47), Point(73,52), Point(71,46), Point(71,51), Point(73,48), Point(73,51), Point(71,50), Point(73,46), Point(73,49), Point(71,48), Point(71,45), Point(71,52))
//    val s=Point(83,41)
//    val e = Point(89,43)
//
//    val a = findShortestPath(s, e, b)
//    println(a)
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



