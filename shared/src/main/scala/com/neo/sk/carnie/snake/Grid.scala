package com.neo.sk.carnie.snake

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


  val defaultLength = 5
  val appleNum = 6
  val appleLife = 50
  val historyRankLength = 5

  var frameCount = 0l
  var grid = Map[Point, Spot]()
  var snakes = Map.empty[Long, SkDt]
  var actionMap = Map.empty[Long, Map[Long, Int]]

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
    debug(s"grid: ${grid.mkString(";")}")
    grid = grid.filter { case (p, spot) =>
      spot match {
        case Body(id) if snakes.contains(id) => true
//        case Apple(_, life) if life >= 0 => true
        //case Header(id, _) if snakes.contains(id) => true
        case Field(id)  if snakes.contains(id) => true
        case _ => false
      }
    }.map {
      //case (p, Header(id, life)) => (p, Body(id, life - 1))
      case (p, b@Body(_)) => (p, b)
      case (p, f@Field(_)) => (p, f)
//      case (p, a@Apple(_, life)) =>
//        appleCount += 1
//        (p, a.copy(life = life - 1))
      case x => x
    }

  }


  def randomEmptyPoint(): Point = {
    var p = Point(random.nextInt(boundary.x - 2), random.nextInt(boundary.y - 2))
    while (grid.contains(p) && grid.contains(p.copy(x = p.x + 2)) && grid.contains(p.copy(y = p.y + 2)) &&
      grid.contains(p.copy(x = p.x + 2, y = p.y + 2))) {
      p = Point(random.nextInt(boundary.x - 2), random.nextInt(boundary.y - 2))
    }
    p
  }

  def randomColor(): String = {
    var color = "#" + (new util.Random).nextInt(256).toHexString + (new util.Random).nextInt(256).toHexString +(new util.Random).nextInt(256).toHexString
    while (snakes.map(_._2.color).toList.contains(color) || color == "#000000" || color == "#000080") {
      color = "#" + (new util.Random).nextInt(256).toHexString + (new util.Random).nextInt(256).toHexString +(new util.Random).nextInt(256).toHexString
    }
    color
  }


  private[this] def updateSnakes() = {
    def updateASnake(snake: SkDt, actMap: Map[Long, Int]): Either[Option[Long], SkDt] = {
      val keyCode = actMap.get(snake.id)
      debug(s" +++ snake[${snake.id}] feel key: $keyCode at frame=$frameCount")
      val newDirection = {
        val keyDirection = keyCode match {
          case Some(KeyEvent.VK_LEFT) => Point(-1, 0)
          case Some(KeyEvent.VK_RIGHT) => Point(1, 0)
          case Some(KeyEvent.VK_UP) => Point(0, -1)
          case Some(KeyEvent.VK_DOWN) => Point(0, 1)
          case _ => snake.direction
        }
        if (keyDirection + snake.direction != Point(0, 0)) {
          keyDirection
        } else {
          snake.direction
        }
      }

      val newHeader = ((snake.header + newDirection) + boundary) % boundary

      grid.get(newHeader) match {
        case Some(x: Body) =>
          debug(s"snake[${snake.id}] hit wall.")
          Left(Some(x.id))
        case Some(Field(id)) =>
          if(id == snake.id){
            //todo 回到了自己的领域，完成一次封闭
            Right(snake.copy(header = newHeader, direction = newDirection))
          } else { //进入到被人的领域
            Right(snake.copy(header = newHeader, direction = newDirection))
          }
        case _ => //判断是否进入到了边界
          if(newHeader.x == 0 || newHeader.x == boundary.x){
            Left(None)
          } else if(newHeader.y == 0 || newHeader.y == boundary.y){
            Left(None)
          } else
            Right(snake.copy(header = newHeader, direction = newDirection))
      }
    }


    var mapKillCounter = Map.empty[Long, Int]
    var updatedSnakes = List.empty[SkDt]

    val acts = actionMap.getOrElse(frameCount, Map.empty[Long, Int])

    snakes.values.map(updateASnake(_, acts)).foreach {
      case Right(s) => updatedSnakes ::= s
      case Left(Some(killerId)) =>
        mapKillCounter += killerId -> (mapKillCounter.getOrElse(killerId, 0) + 1)
      case Left(None) =>
    }


    //if two (or more) headers go to the same point,
    val snakesInDanger = updatedSnakes.groupBy(_.header).filter(_._2.size > 1).values

    val deadSnakes =
      snakesInDanger.flatMap { hits =>
        val sorted = hits.toSeq.sortBy(_.length)
        val winner = sorted.head
        val deads = sorted.tail
        mapKillCounter += winner.id -> (mapKillCounter.getOrElse(winner.id, 0) + deads.length)
        deads
      }.map(_.id).toSet


    val newSnakes = updatedSnakes.filterNot(s => deadSnakes.contains(s.id)).map { s =>
      mapKillCounter.get(s.id) match {
        case Some(k) => s.copy(kill = s.kill + k)
        case None => s
      }
    }

    grid ++= newSnakes.map(s => s.header -> Body(s.id))
    snakes = newSnakes.map(s => (s.id, s)).toMap

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


}
