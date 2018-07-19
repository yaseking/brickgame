package com.neo.sk.carnie
import scala.util.Random

/**
  * User: Taoz
  * Date: 6/28/2018
  * Time: 7:26 PM
  */
object TmpTest {

  val baseDirection = Map("left" -> Point(-1, 0), "right" -> Point(1, 0), "up" -> Point(0, -1), "down" -> Point(0, 1))

  import io.circe.generic.auto._
  import io.circe.parser._
  import io.circe.syntax._






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


  def main(args: Array[String]): Unit = {
    println(findInsidePoint(Point(60,55), Point(60,57),
      List(Point(60,69), Point(60,70), Point(60,71), Point(60,72), Point(60,73), Point(60,74), Point(60,75), Point(59,75), Point(58,75), Point(57,75), Point(56,75), Point(56,76), Point(56,77), Point(55,77), Point(54,77), Point(53,77), Point(52,77), Point(51,77), Point(50,77), Point(49,77), Point(48,77), Point(48,76), Point
      (48,75), Point(48,74), Point(48,73), Point(48,72), Point(48,71), Point(48,70), Point(48,69), Point(48,68), Point(48,67), Point(48,66), Point(48,65), Point(49,65), Point(50,65), Point(50,64), Point(50,63), Point(49,63), Point(49,62), Point(49,61), Point(49,60), Point(49,59), Point(50,59), Point(51,59), Point(51,58), Point(51,57), Point(51,56), Point(51,55), Point(51,54), Point(51,53), Point(52,53), Point(53,53), Point(54,53), Point(55,53), Point(56,53), Point(57,53), Point(58,53), Point(59,53),
        Point(59,54), Point(59,55), Point(59,56), Point(60,56), Point(61,56), Point(62,56), Point(62,57), Point(62,58), Point(62,59), Point(62,60), Point(62,61), Point(62,62), Point(62,63), Point(62,64), Point(61,64), Point(60,64), Point(60,68), Point(60,66), Point(60,67), Point(60,65)))
    )}
}




