package com.neo.sk

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:48 PM
  */
package object carnie {

  sealed trait Spot

  case class Body(id: Long) extends Spot

  case class Field(id: Long) extends Spot

  case object Border extends Spot

  case class Score(id: Long, n: String, k: Int, l: Int, t: Option[Long] = None, area: Int = 0)

  case class Bd(id: Long, x: Int, y: Int)

  case class Fd(id: Long, x: Int, y: Int)

  case class Bord(x: Int, y: Int)


  case class Point(x: Int, y: Int) {
    def +(other: Point) = Point(x + other.x, y + other.y)

    def -(other: Point) = Point(x - other.x, y - other.y)

    def *(n: Int) = Point(x * n, y * n)

    def %(other: Point) = Point(x % other.x, y % other.y)
  }

  case class SkDt(
                   id: Long,
                   name: String,
                   color: String,
                   startPoint: Point,
                   turnPoint: List[Point],
                   header: Point,
                   direction: Point = Point(1, 0),
                   length: Int = 4,
                   kill: Int = 0
                 )

  case class UpdateSnakeInfo(
                              data: SkDt,
                              isFiled: Boolean = false,
                              killedId: Option[Long] = None
                            )


  object Boundary {
    val w = 210
    val h = 75
  }

  object BorderSize{
    val w = 180
    val h = 60
  }

  object Window{
    val w = 60
    val h = 30
  }

  object LittleMap{
    val w=20
    val h=10
  }

}
