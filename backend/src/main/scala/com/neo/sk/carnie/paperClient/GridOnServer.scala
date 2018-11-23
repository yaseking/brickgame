package com.neo.sk.carnie.paperClient

import org.slf4j.LoggerFactory
import com.neo.sk.carnie.paperClient.Protocol._
import org.seekloud.byteobject.MiddleBufferInJvm
import com.neo.sk.carnie.Boot.roomManager
import com.neo.sk.carnie.core.RoomActor.UserDead


/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 9:55 PM
  */
class GridOnServer(override val boundary: Point) extends Grid {


  private[this] val log = LoggerFactory.getLogger(this.getClass)

  override def debug(msg: String): Unit = log.debug(msg)

  override def info(msg: String): Unit = log.info(msg)

  private[this] var waitingJoin = Map.empty[String, (String, String)]

  var currentRank = List.empty[Score]

  var newInfo = List.empty[(String, SkDt, List[Point])]

  private[this] var historyRankMap = Map.empty[String, Score]
  var historyRankList: List[Score] = historyRankMap.values.toList.sortBy(_.k).reverse

  private[this] var historyRankThreshold = if (historyRankList.isEmpty) (-1, -1) else (historyRankList.map(_.area).min, historyRankList.map(_.k).min)

  def addSnake(id: String, roomId: Int, name: String) = {
    val bodyColor = randomColor()
    waitingJoin += (id -> (name, bodyColor))
  }

  def waitingListState: Boolean = waitingJoin.nonEmpty

  private[this] def genWaitingSnake() = {
    val newInfo = waitingJoin.filterNot(kv => snakes.contains(kv._1)).map { case (id, (name, bodyColor)) =>
      val startTime = System.currentTimeMillis()
      val indexSize = 10 //5
    val basePoint = randomEmptyPoint(indexSize)
      val newFiled = (0 until indexSize).flatMap { x =>
        (0 until indexSize).map { y =>
          val point = Point(basePoint.x + x, basePoint.y + y)
          grid += Point(basePoint.x + x, basePoint.y + y) -> Field(id)
          point
        }.toList
      }.toList
      val startPoint = Point(basePoint.x + indexSize / 2, basePoint.y + indexSize / 2)
      val snakeInfo = SkDt(id, name, bodyColor, startPoint, startPoint, startTime = startTime, endTime = startTime)
      snakes += id -> snakeInfo
      killHistory -= id
      (id, snakeInfo, newFiled)
    }.toList
    waitingJoin = Map.empty[String, (String, String)]
    newInfo

  }

  private[this] def updateRanks() = {
    val areaMap = grid.filter { case (p, spot) =>
      spot match {
        case Field(id) if snakes.contains(id) => true
        case _ => false
      }
    }.map {
      case (p, f@Field(_)) => (p, f)
      case _ => (Point(-1, -1), Field((-1L).toString))
    }.filter(_._2.id != -1L).values.groupBy(_.id).map(p => (p._1, p._2.size))
    currentRank = snakes.values.map(s => Score(s.id, s.name, s.kill, areaMap.getOrElse(s.id, 0))).toList.sortBy(_.area).reverse
    var historyChange = false
    currentRank.foreach { cScore =>
      historyRankMap.get(cScore.id) match {
        case Some(oldScore) =>
          if (cScore.area > oldScore.area) {
            historyRankMap += (cScore.id -> cScore)
            historyChange = true
          }
          else if (cScore.area == oldScore.area && cScore.k > oldScore.k) {
            historyRankMap += (cScore.id -> cScore)
            historyChange = true
          }
        case None =>
          if (cScore.area > historyRankThreshold._1) {
            historyRankMap += (cScore.id -> cScore)
            historyChange = true
          }
          else if (cScore.area == historyRankThreshold._1 && cScore.k > historyRankThreshold._2) {
            historyRankMap += (cScore.id -> cScore)
            historyChange = true
          }
        case _ => //do nothing.
      }
    }

    if (historyChange) {
      historyRankList = historyRankMap.values.toList.sorted.take(historyRankLength)
      historyRankThreshold = (historyRankList.lastOption.map(_.area).getOrElse(-1), historyRankList.lastOption.map(_.k).getOrElse(-1))
      historyRankMap = historyRankList.map(s => s.id -> s).toMap
    }

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

  implicit val scoreOrdering = new Ordering[Score] {
    override def compare(x: Score, y: Score): Int = {
      var r = y.area - x.area
      if (r == 0) {
        r = y.k - x.k
      }
      r
    }
  }

  def randomHex() = {
    val h = getRandom(94).toHexString + getRandom(94).toHexString + getRandom(94).toHexString
    String.format("%6s", h).replaceAll("\\s", "0").toUpperCase()
  }

  def getRandom(start: Int) = {
    val end = 256
    val rnd = new scala.util.Random
    start + rnd.nextInt(end - start)
  }

  def colorSimilarity(color1: String, color2: String) = {
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

  def updateInService(newSnake: Boolean): List[(String, List[Point])] = {
    val update = super.update("b")
    val isFinish = update._1
    if (newSnake) newInfo = genWaitingSnake()
    val deadSnakes = update._2
    if (deadSnakes.nonEmpty) {
      val deadSnakesInfo = deadSnakes.map { id =>
        if (currentRank.exists(_.id == id)) {
          val info = currentRank.filter(_.id == id).head
          (id, info.k, info.area)
        } else (id, -1, -1)
      }
      roomManager ! UserDead(deadSnakesInfo)
    }
    updateRanks()
    isFinish
  }

  def getDirectionEvent(frameCount: Long): List[Protocol.DirectionEvent] = {
    actionMap.getOrElse(frameCount, Map.empty).toList.map(a => DirectionEvent(a._1, a._2))
  }

}
