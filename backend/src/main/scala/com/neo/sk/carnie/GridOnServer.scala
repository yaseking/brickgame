package com.neo.sk.carnie

import org.slf4j.LoggerFactory

/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 9:55 PM
  */
class GridOnServer(override val boundary: Point) extends Grid {


  private[this] val log = LoggerFactory.getLogger(this.getClass)

  override def debug(msg: String): Unit = log.debug(msg)

  override def info(msg: String): Unit = log.info(msg)


  private[this] var waitingJoin = Map.empty[Long, String]
//  private[this] var feededApples: List[Ap] = Nil


  var currentRank = List.empty[Score]
  private[this] var historyRankMap = Map.empty[Long, Score]
  var historyRankList = historyRankMap.values.toList.sortBy(_.k).reverse

  private[this] var historyRankThreshold = if (historyRankList.isEmpty) (-1, -1) else (historyRankList.map(_.area).min ,historyRankList.map(_.k).min)

  def addSnake(id: Long, name: String) = waitingJoin += (id -> name)


  private[this] def genWaitingSnake() = {
    waitingJoin.filterNot(kv => snakes.contains(kv._1)).foreach { case (id, name) =>
      val indexSize = 2
      val basePoint = randomEmptyPoint(indexSize)
      val bodyColor = randomColor()
      (0 to indexSize).foreach { x =>
        (0 to indexSize).foreach { y =>
          grid += basePoint.copy(x = basePoint.x + x, y = basePoint.y + y) -> Field(id)
        }
      }
      val startPoint = basePoint.copy(x = basePoint.x + indexSize + 1)

      val boundary = grid.filter(_._2 match { case Field(fid) if fid == id => true case _ => false }).keys.toList.filterNot(_ ==
        Point(basePoint.x + 1, basePoint.y + 1))

      snakes += id -> SkDt(id, name, bodyColor, startPoint, boundary, basePoint)

    }
    waitingJoin = Map.empty[Long, String]
  }

  implicit val scoreOrdering = new Ordering[Score] {
    override def compare(x: Score, y: Score): Int = {
      var r = y.area - x.area
      if (r == 0) {
        r = y.k - x.k
      }
//      if (r == 0) {
//        r = (x.id - y.id).toInt
//      }
      r
    }
  }

  private[this] def updateRanks() = {
    val areaMap = grid.filter { case (p, spot) =>
      spot match {
        case Field(id) if snakes.contains(id) => true
        case _ => false
      }
    }.map {
      case (p, f@Field(_)) => (p, f)
      case _ => (Point(-1, -1), Field(-1L))
    }.filter(_._2.id != -1L).values.groupBy(_.id).map(p => (p._1, p._2.size))
    currentRank = snakes.values.map(s => Score(s.id, s.name, s.kill, s.length, area = areaMap.getOrElse(s.id, 0))).toList.sorted
    println(areaMap.mkString("\n"))
//    println(currentRank)
    var historyChange = false
    currentRank.foreach { cScore =>
      historyRankMap.get(cScore.id) match {
        case Some(oldScore) =>
          if(cScore.area > oldScore.area) {
            historyRankMap += (cScore.id -> cScore)
            historyChange = true
          }
          else if(cScore.area == oldScore.area && cScore.k > oldScore.k) {
            historyRankMap += (cScore.id -> cScore)
            historyChange = true
          }
        case None =>
          if(cScore.area > historyRankThreshold._1) {
            historyRankMap += (cScore.id -> cScore)
            historyChange = true
          }
          else if(cScore.area == historyRankThreshold._1 && cScore.k > historyRankThreshold._2) {
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

  override def update(): Unit = {
    super.update()
    genWaitingSnake()
    updateRanks()
  }

//  def getFeededApple = feededApples

}
