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

  def findShortestPath(start:Point, end: Point, fieldBoundary: List[Point]) = {
    var initDirection = List.empty[Point]
    baseDirection.values.foreach { p =>
      if (fieldBoundary.contains(start + p)) initDirection = p :: initDirection
    }
    println(initDirection.length)
    if (initDirection.lengthCompare(2) == 0) {
      val route1 = getShortest(start + initDirection.head, end, fieldBoundary, List(start + initDirection.head), initDirection.head)
//      val route2 = getShortest(start + initDirection.last, end, fieldBoundary, List(start + initDirection.last), initDirection.last)
//      if(route1.lengthCompare(route2.length) > 0) (route2, route1) else (route1, route2)
    } else {
      (Nil, Nil)
    }
  }

  def getShortest(start: Point, end: Point, fieldBoundary: List[Point], targetPath: List[Point], lastDirection: Point): List[Point] = {
    var res = targetPath
    val resetDirection = if (lastDirection.x != 0) Point(-lastDirection.x, lastDirection.y) else Point(lastDirection.x, -lastDirection.y)
    if (start - end != Point(0, 0)) {
      println(start + "00" + end)
      var direction = Point(-1, -1)
      baseDirection.values.filterNot(_ == resetDirection).foreach { d => if (fieldBoundary.contains(start + d)) direction = d }
      if (direction != Point(-1, -1)) {
        println(direction)
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
    println("color blue" + startPoint)
    baseDirection.foreach(d => if(!boundary.contains(startPoint + d._2)) colorQueue.enqueue((d._1, startPoint + d._2)))

    while(colorQueue.nonEmpty){
      println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
      val currentPoint = colorQueue.dequeue()
      println("color blue" + currentPoint._2)
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
  }


  def main(args: Array[String]): Unit = {
//    val a = findShortestPath(Point(92,31), Point(90,31),
//      List(Point(91,33), Point(92,32), Point(90,31), Point(92,33), Point(90,33), Point(90,32), Point(91,32), Point(92,31)))
//
//
//    println(a)
//    val t = List(Point(69,7), Point(68,7), Point(68,6), Point(68,5), Point(79,5), Point(79,6), Point(71,4), Point(78,7), Point(69,4), Point(76,7), Point(73,7), Point(77,4), Point(69,6), Point(72,7), Point(75,4), Point(73,4), Point(79,7), Point(76,4), Point(74,7), Point(74,4), Point(70,4), Point(79,4), Point(71,7), Point(78,4), Point(77,7), Point(75,7), Point(72,4))
//    println(t.length)
//val b = List(Point(24,17), Point(25,15), Point(23,17), Point(22,15), Point(20,15), Point(20,17), Point(25,17), Point(27,15), Point(26,17), Point(21,17), Point(23,15), Point(22,17), Point(21,15), Point(27,17), Point(26,15), Point(18,15), Point(24,15), Point(27,16))
//
//        println(b.sortBy(_.x))


    val b = List(Point(90,11), Point(90,10), Point(92,11), Point(91,11), Point(92,9), Point(93,11), Point(91,9), Point(94,9), Point(94,11), Point(93,9), Point(94,10))

        val p = findRandomPoint(b)
//    println(p)
//    breadthFirst(p, t, 0l)
  }
}





