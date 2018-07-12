package com.neo.sk

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:48 PM
  */
package object carnie {

  sealed trait Spot

  case class Body(id: Long) extends Spot

  case class Header(id: Long) extends Spot

  //  case class Apple(score: Int, life: Int) extends Spot
  case class Field(id: Long) extends Spot

  case class Score(id: Long, n: String, k: Int, l: Int, t: Option[Long] = None)

  case class Bd(id: Long, x: Int, y: Int)

  case class Fd(id: Long, x: Int, y: Int)


  case class Point(x: Int, y: Int) {
    def +(other: Point) = Point(x + other.x, y + other.y)

    def -(other: Point) = Point(x - other.x, y - other.y)

    def *(n: Int) = Point(x * n, y * n)

    def %(other: Point) = Point(x % other.x, y % other.y)
  }


  class Snake(x: Int, y: Int, len: Int = 5, d: Point = Point(1, 0)) {
    var length = len
    var direction = d
    var header = Point(x, y)
  }

  case class SkDt(
                   id: Long,
                   name: String,
                   color: String,
                   startPoint: Point,
                   boundary: List[Point],
                   header: Point = Point(20, 20),
                   direction: Point = Point(1, 0),
                   length: Int = 4,
                   kill: Int = 0
                 )

  case class UpdateSnakeInfo(
                              data: SkDt,
                              isFiled: Boolean = false
                            )


  object Boundary {
    val w = 120
    val h = 60
  }


}
