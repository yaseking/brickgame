package com.neo.sk.carnie

import java.awt.event.KeyEvent
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
  var killHistory = Map.empty[Long, (Long, String)] //killedId, (killerId, killerName)
  var bodyField = Map.empty[Long, Map[Point, Long]] //(body点，原归属者的id)

  List(0, BorderSize.w).foreach(x => (0 to BorderSize.h).foreach(y => grid += Point(x, y) -> Border))
  List(0, BorderSize.h).foreach(y => (0 to BorderSize.w).foreach(x => grid += Point(x, y) -> Border))

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
        case Field(id) if snakes.contains(id) => true
        case Border => true
        case _ => false
      }
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
          bodyField.get(x.id) match {
            case Some(points) => //圈地还原
              points.foreach(p => grid += p._1 -> Field(p._2))
            case None =>
          }
          bodyField -= x.id
          if (x.id != snake.id) killHistory += x.id -> (snake.id, snake.name)
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
                var tryFind = if (grid.get(snake.startPoint) match {
                  case Some(Field(fid)) if fid == snake.id => true
                  case _ => false
                }) true else false //起点还在的话
                while (searchPoint != snake.startPoint && tryFind) {
                  val blank = Polygon.isCorner(searchPoint, grid, snake.id, bodyField.flatMap(_._2.filter(_._2 == snake.id).keys).toList)
                  if (blank != Point(0, 0)) {
                    if (searchPoint != newHeader) {
                      temp = temp ::: List(searchPoint)
                      searchDirection = searchDirection + blank
                    } else {
                      searchDirection = Point(blank.x, 0)
                    }
                  }
                  searchPoint = searchPoint + searchDirection
                  if (searchPoint == newHeader) tryFind = false
                }
                if (tryFind) grid = Polygon.setPoly(temp, grid, snake.id)
                else {
                  val failBody = grid.filter(_._2 match { case Body(bodyId) if bodyId == snake.id => true case _ => false }).keys
                  grid --= failBody
                  bodyField.get(snake.id) match {
                    case Some(points) => //圈地还原
                      points.foreach(p => grid += p._1 -> Field(p._2))
                    case None =>
                  }
                }
                bodyField -= snake.id
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, turnPoint = Nil), true))

              case _ =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), true))
            }
          } else { //进入到别人的领域
            val tmp = bodyField.getOrElse(snake.id, Map.empty) + (newHeader -> id)
            bodyField += (snake.id -> tmp)
            grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
              case Some(Field(fid)) if fid == snake.id =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header)))
              case _ =>
                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
            }
          }

        case Some(Border) =>
          bodyField.get(snake.id) match {
            case Some(points) => //圈地还原
              points.foreach(p => grid += p._1 -> Field(p._2))
            case None =>
          }
          bodyField -= snake.id
          Left(None)

        case _ =>
          grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
            case Some(Field(fid)) if fid == snake.id =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header)))
            case _ =>
              Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
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
          killedSnaked ::= s.killedId.get //被杀者
        }
        updatedSnakes ::= s
      case Left(_) =>
    }


    //if two (or more) headers go to the same point,die at the same time
    val snakesInDanger = updatedSnakes.groupBy(_.data.header).filter(_._2.lengthCompare(1) > 0).values
    val deadSnakes = snakesInDanger.flatMap { hits => hits.map(_.data.id) }.toSet
    val noFieldSnake = snakes.keys.toSet &~ grid.map(_._2 match {case x@Field(uid) => uid case _ => 0}).toSet.filter(_ != 0) //若领地全被其它玩家圈走则死亡

    val newSnakes = updatedSnakes.filterNot(s => deadSnakes.contains(s.data.id) || killedSnaked.contains(s.data.id) || noFieldSnake.contains(s.data.id)).map { s =>
      mapKillCounter.get(s.data.id) match {
        case Some(k) => s.copy(data = s.data.copy(kill = k + s.data.kill))
        case None => s
      }
    }

    newSnakes.foreach(s => if (!s.isFiled) grid += s.data.header -> Body(s.data.id) else grid += s.data.header -> Field(s.data.id))
    snakes = newSnakes.map(s => (s.data.id, s.data)).toMap

  }

  def getGridData = {
    var bodyDetails: List[Bd] = Nil
    var fieldDetails: List[Fd] = Nil
    var bordDetails: List[Bord] = Nil
    grid.foreach {
      case (p, Body(id)) => bodyDetails ::= Bd(id, p.x, p.y)
      case (p, Field(id)) => fieldDetails ::= Fd(id, p.x, p.y)
      case (p, Border) => bordDetails ::= Bord(p.x, p.y)
    }
    Protocol.GridDataSync(
      frameCount,
      snakes.values.toList,
      bodyDetails,
      fieldDetails,
      bordDetails,
      killHistory.map(k => Kill(k._1, k._2._1, k._2._2)).toList
    )
  }

  def getKiller(myId: Long) = {
    killHistory.get(myId)
  }

  def cleanData() = {
    snakes = Map.empty[Long, SkDt]
    actionMap = Map.empty[Long, Map[Long, Int]]
    grid = grid.filter(_._2 match { case Border => true case _ => false })
    killHistory = Map.empty[Long, (Long, String)]
  }


}
