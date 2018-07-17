package com.neo.sk.carnie

import scala.collection.mutable.ArrayBuffer

/**
  * Created by dry on 2018/7/16.
  **/
object Polygon {

  def Angle2D(x1: Int, y1: Int, x2: Int, y2: Int): Double = {

    var dtheta: Double = 0
    var theta1: Double = 0
    var theta2: Double = 0

    theta1 = Math.atan2(y1.toDouble, x1.toDouble)
    theta2 = Math.atan2(y2.toDouble, x2.toDouble)
    dtheta = theta2 - theta1
    while (dtheta > Math.PI)
      dtheta -= 2 * Math.PI
    while (dtheta < -Math.PI)
      dtheta += 2 * Math.PI
    dtheta

  }

  def InsidePolygon(polygon: List[Point], p: Point): Boolean = {
    var angle: Double = 0
    val l = polygon.length
    var p1 = Point(0, 0)
    var p2 = Point(0, 0)
    for (i <- 0 until l) {
      p1 = Point(polygon(i).x - p.x, polygon(i).y - p.y)
      p2 = Point(polygon((i + 1) % l).x - p.x, polygon((i + 1) % l).y - p.y)
      angle += Angle2D(p1.x, p1.y, p2.x, p2.y)
    }

    if (Math.abs(angle) < Math.PI) false
    else true

  }

  def setPoly(poly: List[Point], grid: Map[Point, Spot], snakeId: Long): Map[Point, Spot] = {
    var new_grid = grid
    for (x <- poly.map(_.x).min until poly.map(_.x).max)
      for (y <- poly.map(_.y).min until poly.map(_.y).max) {
        grid.get(Point(x, y)) match {
          case Some(Field(fid)) if fid == snakeId => //donothing
          case _ =>
            if (InsidePolygon(poly, Point(x, y))) {
              new_grid += Point(x, y) -> Field(snakeId)
            }
        }
      }

    new_grid.map {
      case (p, Body(bids)) if bids == snakeId => (p, Field(bids))
      case x => x
    }
  }

  def isCorner(p: Point, grid: Map[Point, Spot], snakeId: Long, otherBody: List[Point]): Point = {
    var blank = ArrayBuffer[Point]()
    val arr = Array(Point(-1, -1), Point(-1, 0), Point(-1, 1), Point(0, -1), Point(0, 1), Point(1, -1), Point(1, 0), Point(1, 1))
    for (a <- arr) {
      grid.get(a + p) match {
        case Some(Field(fid)) if fid == snakeId => //doNothing
        case _ if otherBody.contains(a+p) => //doNothing
        case _ => blank += a
      }
    }
    val count = blank.length
    if (count == 1 && (blank(0).x * blank(0).y != 0)) blank(0)
    else {
      if (blank.contains(Point(-1, 0)) && blank.contains(Point(-1, 1)) && blank.contains(Point(0, 1))) {
        Point(1, -1)
      }
      else if (blank.contains(Point(0, 1)) && blank.contains(Point(1, 1)) && blank.contains(Point(1, 0))) {
        Point(-1, -1)
      }
      else if (blank.contains(Point(-1, 0)) && blank.contains(Point(-1, -1)) && blank.contains(Point(0, -1))) {
        Point(1, 1)
      }
      else if (blank.contains(Point(1, 0)) && blank.contains(Point(1, -1)) && blank.contains(Point(0, -1))) {
        Point(-1, 1)
      }
      else
        Point(0, 0)
    }
  }

}
