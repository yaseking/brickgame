package org.seekloud.carnie.paperClient

import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory
import org.seekloud.carnie.Boot.roomManager
import org.seekloud.carnie.core.RoomActor


/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 9:55 PM
  */
class GridOnServer(override val boundary: Point) extends Grid {


  private[this] val log = LoggerFactory.getLogger(this.getClass)

  override def debug(msg: String): Unit = log.debug(msg)

  override def info(msg: String): Unit = log.info(msg)

  private[this] var waitingJoin = Map.empty[String, (String, String, Int, Byte)]

  var currentRank = List.empty[Score]

  var newInfo = List.empty[(String, SkDt, List[Point])]

  def addSnake(id: String, roomId: Int, name: String, img: Int, carnieId: Byte): Unit = {
    val bodyColor = randomColor()
    waitingJoin += (id -> (name, bodyColor, img, carnieId))
  }

  def waitingListState: Boolean = waitingJoin.nonEmpty

  private[this] def genWaitingSnake() = {
    val newInfo = waitingJoin.filterNot(kv => snakes.contains(kv._1)).map { case (id, (name, bodyColor, img, carnieId)) =>
      val indexSize = 5
      val basePoint = randomEmptyPoint(indexSize)
      val newFiled = (0 until indexSize).flatMap { x =>
        (0 until indexSize).map { y =>
          val point = Point(basePoint.x + x, basePoint.y + y)
          grid += Point(basePoint.x + x, basePoint.y + y) -> Field(id)
          point
        }.toList
      }.toList
      val startPoint = Point(basePoint.x + indexSize / 2, basePoint.y + indexSize / 2)
      val snakeInfo = SkDt(id, name, bodyColor, startPoint, startPoint, img = img, carnieId = carnieId) //img: Int
      snakes += id -> snakeInfo
      killHistory -= id
      (id, snakeInfo, newFiled)
    }.toList
    waitingJoin = Map.empty[String, (String, String, Int, Byte)]
    newInfo
  }

  private[this] def updateRanks(): Unit = {
    val areaMap = grid.filter { case (p, spot) =>
      spot match {
        case Field(id) if snakes.contains(id) => true
        case _ => false
      }
    }.map {
      case (p, f@Field(_)) => (p, f)
      case _ => (Point(-1, -1), Field((-1L).toString))
    }.filter(_._2.id != -1L).values.groupBy(_.id).map(p => (p._1, p._2.size))
    currentRank = snakes.values.map(s => Score(s.id, s.name, s.kill, areaMap.getOrElse(s.id, 0).toShort)).toList.sortBy(_.area).reverse

  }

  def randomColor(): String = {
    var color = randomHex()
    val exceptColor = snakes.map(_._2.color).toList ::: List("#F5F5F5", "#000000", "#000080", "#696969") ::: waitingJoin.map(_._2._2).toList
    val similarityDegree = 2000
    while (exceptColor.map(c => colorSimilarity(c.split("#").last, color)).count(_ < similarityDegree) > 0) {
      color = randomHex()
    }
    //    log.debug(s"color : $color exceptColor : $exceptColor")
    "#" + color
  }

  def randomHex(): String = {
    val h = getRandom(94).toHexString + getRandom(94).toHexString + getRandom(94).toHexString
    String.format("%6s", h).replaceAll("\\s", "0").toUpperCase()
  }

  def getRandom(start: Int): Int = {
    val end = 214
    val rnd = new scala.util.Random
    start + rnd.nextInt(end - start)
  }

  def colorSimilarity(color1: String, color2: String): Int = {
    var target = 0.0
    var index = 0
    if (color1.length == 6 && color2.length == 6) {
      (0 until color1.length / 2).foreach { _ =>
        target = target +
          Math.pow(hexToDec(color1.substring(index, index + 2)) - hexToDec(color2.substring(index, index + 2)), 2)
        index = index + 2
      }
    }
    target.toInt
  }

  def hexToDec(hex: String): Int = {
    val hexString: String = "0123456789ABCDEF"
    var target = 0
    var base = Math.pow(16, hex.length - 1).toInt
    for (i <- 0 until hex.length) {
      target = target + hexString.indexOf(hex(i)) * base
      base = base / 16
    }
    target
  }

  def updateInService(newSnake: Boolean, roomId: Int, mode: Int): List[(String, List[Point])] = {
    val update = super.update("b")
    val isFinish = update._1
    if (newSnake) newInfo = genWaitingSnake()
    val deadSnakes = update._2
    if (deadSnakes.nonEmpty) {
      val deadSnakesInfo = deadSnakes.map { id =>
        if (currentRank.exists(_.id == id)) {
          val info = currentRank.filter(_.id == id).head
          (id, info.k, info.area)
        } else (id, -1.toShort, -1.toShort)
      }
      roomManager ! RoomActor.UserDead(roomId, mode, deadSnakesInfo)
    }
    updateRanks()
    isFinish
  }

  def getDirectionEvent(frameCount: Int): List[Protocol.DirectionEvent] = {
    actionMap.getOrElse(frameCount, Map.empty).toList.map(a => Protocol.DirectionEvent(a._1, a._2))
  }

  def generateCarnieId(carnieIdGenerator: AtomicInteger, existsId: Iterable[Byte]): Byte = {
    var newId = (carnieIdGenerator.getAndIncrement() % Byte.MaxValue).toByte
    while (existsId.exists(_ == newId)) {
      newId = (carnieIdGenerator.getAndIncrement() % Byte.MaxValue).toByte
    }
    newId
  }

  def zipFieldWithCondensed(f: (Byte, scala.List[Point])): Protocol.FieldByColumnCondensed = {
    Protocol.FieldByColumnCondensed(f._1, f._2.groupBy(_.y).map { case (y, target) =>
      (y.toShort, Tool.findContinuous(target.map(_.x.toShort).sorted)) //read
    }.toList.groupBy(_._2).map { case (r, target) =>
      Protocol.ScanByColumn(Tool.findContinuous(target.map(_._1).sorted), r)
    }.toList)
  }

  def zipField(f: (String, Byte, scala.List[Point])): (Protocol.FieldByColumn, Protocol.FieldByColumnCondensed) = {
    val zipField = f._3.groupBy(_.y).map { case (y, target) =>
      (y.toShort, Tool.findContinuous(target.map(_.x.toShort).sorted)) //read
    }.toList.groupBy(_._2).map { case (r, target) =>
      Protocol.ScanByColumn(Tool.findContinuous(target.map(_._1).sorted), r)
    }.toList
    (Protocol.FieldByColumn(f._1, zipField), Protocol.FieldByColumnCondensed(f._2, zipField))
  }


}
