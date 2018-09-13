package com.neo.sk.carnie

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:48 PM
  */
package object paperClient {

  sealed trait Spot

  case class Body(id: Long, fid: Option[Long]) extends Spot

  case class Field(id: Long) extends Spot

  case object Border extends Spot

  case object Blank extends Spot

  case class Score(id: Long, n: String, k: Int, area: Int = 0)

  case class Bd(id: Long, fid: Option[Long], x: Float, y: Float)

  case class Fd(id: Long, x: Float, y: Float)

  case class Bord(x: Float, y: Float)

  case class Kill(killedId: Long, killerId: Long, killerName: String)

  case class BaseScore(kill: Int, area: Int, startTime: Long, endTime: Long)


  case class Point(x: Float, y: Float) {
    def +(other: Point) = Point(x + other.x, y + other.y)

    def -(other: Point) = Point(x - other.x, y - other.y)

    def *(n: Int) = Point(x * n, y * n)

    def *(n: Float) = Point(x * n, y * n)

    def /(n: Int) = Point(x / n, y / n)

    def %(other: Point) = Point(x % other.x, y % other.y)

  }

  case class SkDt(
                   id: Long,
                   name: String,
                   color: String,
                   startPoint: Point,
                   header: Point,
                   direction: Point = Point(0, 0),
                   kill: Int = 0
                 )

  case class UpdateSnakeInfo(
                              data: SkDt,
                              bodyInField : Option[Long] = None
                            )


  object Boundary {
    val w = 210
    val h = 105
  }

  object BorderSize {
    val w = 180
    val h = 90
  }

  object Window {
    val w = 60
    val h = 30
  }

  object littleMap {
    val w = 12
    val h = 7
  }

}
