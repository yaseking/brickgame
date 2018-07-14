package com.neo.sk.carnie

import java.awt.event.KeyEvent

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random


/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 5:34 PM
  */
trait Grid {

  val boundary: Point

  def debug(msg: String): Unit

  def info(msg: String): Unit

  val random = new Random(System.nanoTime())

  val historyRankLength = 5
  var frameCount = 0l
  var grid = Map[Point, Spot]()
  var snakes = Map.empty[Long, SkDt]
  var actionMap = Map.empty[Long, Map[Long, Int]]
  val baseDirection = Map("left" -> Point(-1, 0), "right" -> Point(1, 0), "up" -> Point(0, -1), "down" -> Point(0, 1))
  var colorField = Map.empty[Long, List[Point]]

  def removeSnake(id: Long): Option[SkDt] = {
    val r = snakes.get(id)
    if (r.isDefined) {
      snakes -= id
    }
    r
  }


  def addAction(id: Long, keyCode: Int) = {
    addActionWithFrame(id, keyCode, frameCount)
  }

  def addActionWithFrame(id: Long, keyCode: Int, frame: Long) = {
    val map = actionMap.getOrElse(frame, Map.empty)
    val tmp = map + (id -> keyCode)
    actionMap += (frame -> tmp)
  }


  def update() = {
    //println(s"-------- grid update frameCount= $frameCount ---------")
    updateSnakes()
    updateSpots()
    actionMap -= frameCount
    frameCount += 1
  }

  private[this] def updateSpots() = {
    //    debug(s"grid: ${grid.mkString(";")}")
    grid = grid.filter { case (p, spot) =>
      spot match {
        case Body(id) if snakes.contains(id) => true
        //case Header(id, _) if snakes.contains(id) => true
        case Field(id) if snakes.contains(id) => true
        case _ => false
      }
    }.map {
      //case (p, Header(id, life)) => (p, Body(id, life - 1))
      case (p, b@Body(_)) => (p, b)
      case (p, f@Field(_)) => (p, f)
      case x => x
    }

  }


  def randomEmptyPoint(size: Int): Point = {
    var p = Point(random.nextInt(boundary.x - size), random.nextInt(boundary.y - size))
    while ((0 until size).flatMap { x =>
      (0 until size).map { y =>
        grid.contains(p.copy(x = p.x + x, y = p.y + y))
      }
    }.contains(true)) {
      p = Point(random.nextInt(boundary.x - size), random.nextInt(boundary.y - size))
    }
    p
  }

  def randomColor(): String = {
    var color = "#" + randomHex()
    while (snakes.map(_._2.color).toList.contains(color) || color == "#000000" || color == "#000080") {
      color = "#" + randomHex()
    }
    color
  }

  def randomHex() = {
    val h = (new util.Random).nextInt(256).toHexString + (new util.Random).nextInt(256).toHexString + (new util.Random).nextInt(256).toHexString
    String.format("%6s", h).replaceAll("\\s", "0")
  }

  private[this] def updateSnakes() = {
    def updateASnake(snake: SkDt, actMap: Map[Long, Int]): Either[Option[Long], UpdateSnakeInfo] = {
      val keyCode = actMap.get(snake.id)
      //      debug(s" +++ snake[${snake.id} -- color is ${snake.color} ] feel key: $keyCode at frame=$frameCount")
      val (newDirection, turnPoint) = {
        val keyDirection = keyCode match {
          case Some(KeyEvent.VK_LEFT) => Point(-1, 0)
          case Some(KeyEvent.VK_RIGHT) => Point(1, 0)
          case Some(KeyEvent.VK_UP) => Point(0, -1)
          case Some(KeyEvent.VK_DOWN) => Point(0, 1)
          case _ => snake.direction
        }
        if (keyDirection + snake.direction != Point(0, 0)) {
          if (keyDirection != snake.direction) (keyDirection, Some(snake.header))
          else (keyDirection, None)
        } else {
          (snake.direction, None)
        }
      }

      val newHeader = snake.header + newDirection

      val value = grid.get(newHeader) match {
        case Some(x: Body) => //进行碰撞检测
          debug(s"snake[${snake.id}] hit wall.")
          grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
            case Some(Field(fid)) if fid == snake.id =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header), killedId = Some(x.id)))
            case _ =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), killedId = Some(x.id)))
          }

        case Some(Field(id)) =>
          if (id == snake.id) {
            grid(snake.header) match {
              case Body(bid) if bid == snake.id => //回到了自己的领域
                var temp = List.empty[Point]
                var searchDirection = (newDirection + Point(1, 1)) % Point(2, 2)
                var searchPoint = newHeader
                temp = List(snake.startPoint) ::: snake.turnPoint ::: List(newHeader)
                while (searchPoint != snake.startPoint) {
                  val blank = isCorner(searchPoint, grid, snake.id, newHeader)
                  if (blank != Point(0, 0)) {
                    if (searchPoint != newHeader) {
                      temp = temp ::: List(searchPoint)
                      searchDirection = searchDirection + blank
                    } else {
                      searchDirection = Point(blank.x, 0)
                    }
                  }
                  searchPoint = searchPoint + searchDirection
                }
                grid = setPoly(temp, grid, boundary, snake.id)
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, turnPoint = Nil), true))

              case _ =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), true))
            }
          } else { //进入到别人的领域
            grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
              case Some(Field(fid)) if fid == snake.id =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header)))
              case _ =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
            }
          }

        case _ => //判断是否进入到了边界
          if (newHeader.x == 0 || newHeader.x == boundary.x) {
            Left(None)
          } else if (newHeader.y == 0 || newHeader.y == boundary.y) {
            Left(None)
          } else {
            grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
              case Some(Field(fid)) if fid == snake.id =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header)))
              case _ =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
            }

          }
      }
      value match {
        case Right(v) if turnPoint.nonEmpty && !v.isFiled =>
          Right(v.copy(v.data.copy(turnPoint = v.data.turnPoint ::: List(turnPoint.get))))

        case _ => value
      }
    }


    var mapKillCounter = Map.empty[Long, Int]
    var updatedSnakes = List.empty[UpdateSnakeInfo]
    var killedSnaked = List.empty[Long]

    val acts = actionMap.getOrElse(frameCount, Map.empty[Long, Int])

    snakes.values.map(updateASnake(_, acts)).foreach {
      case Right(s) =>
        if (s.killedId.nonEmpty) {
          mapKillCounter += s.data.id -> (mapKillCounter.getOrElse(s.data.id, 0) + 1)
          killedSnaked ::= s.killedId.get
        }
        updatedSnakes ::= s
      case Left(_) =>
    }


    //if two (or more) headers go to the same point,die at the same time
    val snakesInDanger = updatedSnakes.groupBy(_.data.header).filter(_._2.lengthCompare(1) > 0).values
    val deadSnakes = snakesInDanger.flatMap { hits => hits.map(_.data.id) }.toSet

    val newSnakes = updatedSnakes.filterNot(s => deadSnakes.contains(s.data.id) || killedSnaked.contains(s.data.id)).map { s =>
      mapKillCounter.get(s.data.id) match {
        case Some(k) => s.copy(data = s.data.copy(kill = k + s.data.kill))
        case None => s
      }
    }

    newSnakes.foreach(s => if (!s.isFiled) grid += s.data.header -> Body(s.data.id) else grid += s.data.header -> Field(s.data.id))
    snakes = newSnakes.map(s => (s.data.id, s.data)).toMap

  }


  def updateAndGetGridData() = {
    update()
    getGridData
  }

  def getGridData = {
    var bodyDetails: List[Bd] = Nil
    var fieldDetails: List[Fd] = Nil
    grid.foreach {
      case (p, Body(id)) => bodyDetails ::= Bd(id, p.x, p.y)
      case (p, Field(id)) => fieldDetails ::= Fd(id, p.x, p.y)
      case (p, Header(id)) => bodyDetails ::= Bd(id, p.x, p.y)
    }
    Protocol.GridDataSync(
      frameCount,
      snakes.values.toList,
      bodyDetails,
      fieldDetails
    )
  }

  def Angle2D(x1: Int, y1: Int, x2: Int, y2: Int) : Double = {

    var dtheta:Double = 0
    var theta1:Double = 0
    var theta2:Double = 0

    theta1 = Math.atan2(y1.toDouble,x1.toDouble)
    theta2 = Math.atan2(y2.toDouble,x2.toDouble)
    dtheta = theta2 - theta1
    while (dtheta > Math.PI)
      dtheta -= 2 * Math.PI
    while (dtheta < -Math.PI)
      dtheta += 2 * Math.PI
    dtheta

  }

  def InsidePolygon(polygon:List[Point], p: Point) :Boolean = {

    var i = 0
    var angle: Double = 0
    val l = polygon.length
    var p1 = Point(0,0)
    var p2 = Point(0,0)
    for (i <-0 until l) {
      p1= Point(polygon(i).x - p.x, polygon(i).y - p.y)
      p2 = Point(polygon((i+1)%l).x - p.x, polygon((i+1)%l).y - p.y)
      angle += Angle2D(p1.x,p1.y,p2.x,p2.y)
    }

    if (Math.abs(angle) < Math.PI) false
    else true

  }

  def setPoly(poly: List[Point], grid: Map[Point, Spot], boundary: Point, snakeId: Long): Map[Point, Spot] = {
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


  //  def Angle2D(x1: Int, y1: Int, x2: Int, y2: Int) : Double = {
//
//    var dtheta:Double = 0
//    var theta1:Double = 0
//    var theta2:Double = 0
//
//    theta1 = Math.atan2(y1.toDouble,x1.toDouble)
//    theta2 = Math.atan2(y2.toDouble,x2.toDouble)
//    dtheta = theta2 - theta1
//    while (dtheta > Math.PI)
//      dtheta -= 2 * Math.PI
//    while (dtheta < -Math.PI)
//      dtheta += 2 * Math.PI
//    dtheta
//
//  }
//
//  def InsidePolygon(polygon:List[Point], p: Point) :Boolean = {
//
//    var angle: Double = 0
//    val l = polygon.length
//    var p1 = Point(0, 0)
//    var p2 = Point(0, 0)
//    polygon.indices.foreach { i =>
//      p1 = Point(polygon(i).x - p.x, polygon(i).y - p.y)
//      p2 = Point(polygon((i + 1) % l).x - p.x, polygon((i + 1) % l).y - p.y)
//      angle += Angle2D(p1.x, p1.y, p2.x, p2.y)
//    }
//
//    if (Math.abs(angle) < Math.PI) false
//    else true
//  }
//
//  def setPoly(poly: List[Point], grid: Map[Point, Spot], snakeId: Long): Map[Point, Spot] = {
//    var new_grid = Map[Point, Spot]()
//    new_grid = new_grid ++ grid
//    var x_max = 0
//    var y_max = 0
//    var x_min = boundary.x
//    var y_min = boundary.y
//    for(p <- poly){
//      if(p.x > x_max) x_max = p.x
//      if(p.x < x_min) x_min = p.x
//      if(p.y > y_max) y_max = p.y
//      if(p.y < y_min) y_min = p.y
//    }
//    for(x <- x_min until x_max)
//      for(y <- y_min until y_max){
//        grid.get(Point(x, y)) match {
//          case Some(Field(fid)) if fid == snakeId => //donothing
//          case _ =>
//            if(InsidePolygon(poly, Point(x,y))){
//              new_grid += Point(x,y) -> Field(snakeId)
//            }
//        }
//      }
//
//    new_grid
//  }

  def isCorner(p: Point, grid: Map[Point, Spot], snakeId: Long, newHeader: Point): Point = {
    var blank = ArrayBuffer[Point]()
    val arr = Array(Point(-1, -1), Point(-1, 0), Point(-1, 1), Point(0, -1), Point(0, 1), Point(1, -1), Point(1, 0), Point(1, 1))
    for (a <- arr) {
      grid.get(a + p) match {
        case Some(Field(fid)) if fid == snakeId => //doNothing
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
