package com.neo.sk.carnie

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:48 PM
  */
package object paperClient {

  sealed trait Spot

  case class Body(id: String, fid: Option[String]) extends Spot

  case class Field(id: String) extends Spot

  case object Border extends Spot

  case object Blank extends Spot

  case class Score(id: String, n: String, k: Int, area: Int = 0)

  case class Bd(id: String, fid: Option[Long], x: Float, y: Float)

  case class Fd(id: String, x: Float, y: Float)

  case class Bord(x: Float, y: Float)

  case class Kill(killedId: String, killerId: String, killerName: String,frameCount:Long)

  case class KilledSkDt(
                      id:String,
                      nickname:String,
                      killing:Int,
                      score: Float,
                      startTime:Long,
                      endTime:Long
                      )

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
                   id: String,
                   name: String,
                   color: String,//may change to rgb(Int, Int, Int)
                   startPoint: Point,
                   header: Point,
                   direction: Point = Point(0, 0),
                   kill: Int = 0,
                   startTime: Long,
                   endTime: Long
                 )

  case class UpdateSnakeInfo(
                              data: SkDt,
                              bodyInField : Option[String] = None
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
