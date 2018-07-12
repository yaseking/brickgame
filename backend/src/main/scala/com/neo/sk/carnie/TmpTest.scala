package com.neo.sk.carnie

import scala.collection.mutable
import scala.util.Random

/**
  * User: Taoz
  * Date: 6/28/2018
  * Time: 7:26 PM
  */
object TmpTest {

  val baseDirection = Map("left" -> Point(-1, 0), "right" -> Point(1, 0), "up" -> Point(0, -1), "down" -> Point(0, 1))
  val random = new Random(System.nanoTime())

  import io.circe.generic.auto._
  import io.circe.parser._
  import io.circe.syntax._

  def findVertex(shape: List[Point]) = {
    var vertex = List.empty[Point]
    shape.foreach { p =>
      if (List(baseDirection("up"), baseDirection("down")).exists { d =>
        shape.contains(p + d) && shape.contains(p + baseDirection("left")) && !shape.contains(p + baseDirection("right")) ||
          shape.contains(p + d) && shape.contains(p + baseDirection("right")) && !shape.contains(p + baseDirection("left"))
      }) {
        vertex = p :: vertex
      }
    }
    vertex
  }

  def findShortestPath(start: Point, end: Point, fieldBoundary: List[Point]) = {
    var initDirection = List.empty[Point]
    baseDirection.values.foreach { p =>
      if (fieldBoundary.contains(start + p)) initDirection = p :: initDirection
    }
    val vertex = findVertex(fieldBoundary)
    println("vertex" +  vertex)
    if (initDirection.lengthCompare(2) == 0) {
      val route1 = getShortest(start + initDirection.head, end, fieldBoundary, List(start + initDirection.head, start), initDirection.head, vertex)
      val route2 = getShortest(start + initDirection.last, end, fieldBoundary, List(start + initDirection.last, start), initDirection.last, vertex)
      if (route1.lengthCompare(route2.length) > 0) (route2, route1) else (route1, route2)
    } else {
      (Nil, Nil)
    }
  }

  def getShortest(start: Point, end: Point, fieldBoundary: List[Point], targetPath: List[Point], lastDirection: Point, vertex: List[Point]): List[Point] = {
    var res = targetPath
    val resetDirection = if (lastDirection.x != 0) Point(-lastDirection.x, lastDirection.y) else Point(lastDirection.x, -lastDirection.y)
    val nextDirection = if(vertex.contains(start))  baseDirection.values.filterNot(List(lastDirection, resetDirection).contains(_)) else List(lastDirection)
    if (start - end != Point(0, 0)) {
      var direction = Point(-1, -1)
      nextDirection.foreach { d => if (fieldBoundary.contains(start + d)) direction = d }
      if (direction != Point(-1, -1)) {
        res = getShortest(start + direction, end, fieldBoundary, start + direction :: targetPath, direction, vertex)
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
        val colorQueue = new mutable.Queue[Point]()
        var alreadyColor = List.empty[Point]
        colorQueue.enqueue(startPoint)

        while (colorQueue.nonEmpty) {
          val nowColor = colorQueue.dequeue()
          alreadyColor = nowColor :: alreadyColor
          println(nowColor)
          baseDirection.foreach { d =>
            val nextPoint = startPoint + d._2
            if (!boundary.contains(nextPoint) && !alreadyColor.contains(nextPoint)){
              colorQueue.enqueue(nextPoint)
            }
          }
        }

      case None =>
    }

//    boundary.foreach(b => println(b))
  }


  def main(args: Array[String]): Unit = {
//    val a = findShortestPath(Point(48,36), Point(48,35),
//      List(Point(50,36), Point(50,37), Point(49,37), Point(48,37), Point(48,36), Point(48,35), Point(49,35), Point(50,35), Point(53,35), Point(52,36), Point(54,35), Point(52,35), Point(55,36), Point(51,36), Point(51,35), Point(55,35), Point(54,36), Point(53,36)))
////
//    println(a)
//    val t = List(Point(69,7), Point(68,7), Point(68,6), Point(68,5), Point(79,5), Point(79,6), Point(71,4), Point(78,7), Point(69,4), Point(76,7), Point(73,7), Point(77,4), Point(69,6), Point(72,7), Point(75,4), Point(73,4), Point(79,7), Point(76,4), Point(74,7), Point(74,4), Point(70,4), Point(79,4), Point(71,7), Point(78,4), Point(77,7), Point(75,7), Point(72,4))
//    println(t.length)
//val b = List(Point(24,17), Point(25,15), Point(23,17), Point(22,15), Point(20,15), Point(20,17), Point(25,17), Point(27,15), Point(26,17), Point(21,17), Point(23,15), Point(22,17), Point(21,15), Point(27,17), Point(26,15), Point(18,15), Point(24,15), Point(27,16))
//
//        println(b.sortBy(_.x))



    val c = List(Point(88,47), Point(88,46), Point(89,47), Point(89,46), Point(90,46), Point(92,46), Point(92,47), Point(91,46), Point(91,47), Point(90,47))

    val p = findRandomPoint(c, c)
    println(p)
//    breadthFirst(p, c, 0l)
  }
}



