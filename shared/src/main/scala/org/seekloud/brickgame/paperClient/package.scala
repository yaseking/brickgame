package org.seekloud.brickgame


/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:48 PM
  */
package object paperClient {

  sealed trait Spot

  case class Field(id: String) extends Spot

  case object TopBorder extends Spot

  case object SideBorder extends Spot

  case object Plank extends Spot

  case object Brick extends Spot

  case object DeadLine extends Spot

  case class Score(id: String, n: String, k: Short, area: Short = 0)

  case class Bd(id: String, fid: Option[Long], x: Float, y: Float)

  case class Fd(id: String, x: Float, y: Float)

  case class Bord(x: Float, y: Float)

  case class KilledSkDt(
                         id: String,
                         nickname: String,
                         killing: Int,
                         score: Float,
                         startTime: Long,
                         endTime: Long
                       )

  case class BaseScore(kill: Short, area: Short, playTime: Short)


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
                   color: String,
                   startPoint: Point,
                   header: Point,
                   direction: Point = Point(0, 0),
                   kill: Short = 0,
                   img: Int = 0,
                   carnieId: Byte
                 )


  case class PlayerDt(
                   id: Int,
                   name: String,
                   location: Int,
                   velocityX: Float,
                   velocityY: Float,
                   ballLocation: Point = Point(0 ,0),
                   field: Map[Point, Spot],
                   direction: Int = 0
                 )

  case class SkDt4Sync(
                        id: String,
                        startPoint: Point,
                        header: Point,
                        direction: Point
                      )

  case class UpdateSnakeInfo(
                              data: SkDt,
                              bodyInField: Option[String] = None
                            )

  object OriginField {
    val w = 20
    val h = 3
  }

  object Boundary {
    val w = 24
    val h = 45
  }

//  object Boundary {
//    val w = 200
//    val h = 100
//  }

  object BorderSize {
    val w = 170
    val h = 85
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
