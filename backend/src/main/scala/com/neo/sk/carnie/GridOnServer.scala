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

  private[this] var historyRankThreshold = if (historyRankList.isEmpty) -1 else historyRankList.map(_.k).min

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

      snakes += id -> SkDt(id, name, bodyColor, startPoint, Nil, basePoint)

    }
    waitingJoin = Map.empty[Long, String]
  }

  implicit val scoreOrdering = new Ordering[Score] {
    override def compare(x: Score, y: Score): Int = {
      var r = y.k - x.k
      if (r == 0) {
        r = y.l - x.l
      }
      if (r == 0) {
        r = (x.id - y.id).toInt
      }
      r
    }
  }

  private[this] def updateRanks() = {
    currentRank = snakes.values.map(s => Score(s.id, s.name, s.kill, s.length)).toList.sorted
    var historyChange = false
    currentRank.foreach { cScore =>
      historyRankMap.get(cScore.id) match {
        case Some(oldScore) if cScore.k > oldScore.k =>
          historyRankMap += (cScore.id -> cScore)
          historyChange = true
        case None if cScore.k > historyRankThreshold =>
          historyRankMap += (cScore.id -> cScore)
          historyChange = true
        case _ => //do nothing.
      }
    }

    if (historyChange) {
      historyRankList = historyRankMap.values.toList.sorted.take(historyRankLength)
      historyRankThreshold = historyRankList.lastOption.map(_.k).getOrElse(-1)
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
