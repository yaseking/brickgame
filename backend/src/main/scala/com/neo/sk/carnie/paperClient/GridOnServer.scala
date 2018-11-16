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

  private val maxRecordNum = 100

  private val fileMaxRecordNum = 100000000

  var fileRecordNum = 0

  val currentTime = System.currentTimeMillis()

  val fileName = s"carnie_${currentTime}"

//  val initState = Snapshot(grid, snakes, Nil)

  var fileIndex = 0

//  var recorder: FrameOutputStream = getRecorder(fileName, fileIndex, GameInformation(currentTime), Some(initState))

  val middleBuffer = new MiddleBufferInJvm(10 * 4096)

  var eventFrames = List.empty[Option[(List[GameEvent], Option[Snapshot])]]

  var enclosureEveryFrame = List.empty[(Long, List[Point])]

  var stateEveryFrame :Option[Snapshot] = None

  var currentRank = List.empty[Score]

  private[this] var historyRankMap = Map.empty[String, Score]
  var historyRankList = historyRankMap.values.toList.sortBy(_.k).reverse

  private[this] var historyRankThreshold = if (historyRankList.isEmpty) (-1, -1) else (historyRankList.map(_.area).min ,historyRankList.map(_.k).min)

  def addSnake(id: String, roomId:Int, name: String) = {
    val bodyColor = randomColor()
    waitingJoin += (id -> (name, bodyColor))
  }

  def waitingListState = waitingJoin.nonEmpty

  private[this] def genWaitingSnake() = {
    waitingJoin.filterNot(kv => snakes.contains(kv._1)).foreach { case (id, (name, bodyColor)) =>
      val startTime = System.currentTimeMillis()
      val indexSize = 5
      val basePoint = randomEmptyPoint(indexSize)
      (0 until indexSize).foreach { x =>
        (0 until indexSize).foreach { y =>
          grid += Point(basePoint.x + x, basePoint.y + y) -> Field(id)
        }
      }
      val startPoint = Point(basePoint.x + indexSize / 2, basePoint.y + indexSize / 2)
      snakes += id -> SkDt(id, name, bodyColor, startPoint, startPoint, startTime = startTime, endTime = startTime)
      killHistory -= id
    }
    waitingJoin = Map.empty[String, (String, String)]
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

  def randomColor(): String = {
    var color = randomHex()
    val exceptColor = snakes.map(_._2.color).toList ::: List("#F5F5F5", "#000000", "#000080", "#696969") ::: waitingJoin.map(_._2._2).toList
    val similarityDegree = 2000
    while (exceptColor.map(c => colorSimilarity(c.split("#").last, color)).count(_<similarityDegree) > 0) {
      color = randomHex()
    }
    "#" + color
  }

  def randomHex() = {
    val h = random.nextInt(256).toHexString + random.nextInt(256).toHexString + random.nextInt(256).toHexString
    String.format("%6s", h).replaceAll("\\s", "0").toUpperCase()
  }

  def colorSimilarity(color1: String, color2: String) = {
    var target = 0.0
    var index = 0
    if(color1.length == 6 && color2.length == 6) {
      (0 until color1.length/2).foreach{ _ =>
        target = target +
          Math.pow(hexToDec(color1.substring(index, index + 2)) - hexToDec(color2.substring(index, index + 2)), 2)
        index = index + 2
      }
    }
    target.toInt
  }

  def hexToDec(hex: String): Int ={
    val hexString: String = "0123456789ABCDEF"
    var target = 0
    var base = Math.pow(16, hex.length - 1).toInt
    for(i <- 0 until hex.length){
      target = target + hexString.indexOf(hex(i)) * base
      base = base / 16
    }
    target
  }

  def updateInService(newSnake: Boolean): List[(String, List[Point])] = {
    val update = super.update("b")
    val isFinish = update._1
    if (newSnake) genWaitingSnake()
    updateRanks()
    val deadSnakes = update._2
    if (deadSnakes.nonEmpty) {
        roomManager ! UserDead(deadSnakes)
    }
    isFinish
  }

  def getDirectionEvent(frameCount: Long): List[Protocol.DirectionEvent] = {
    actionMap.getOrElse(frameCount, Map.empty).toList.map(a => DirectionEvent(a._1, a._2))
  }

//  override def checkEvents(enclosure: List[(String, List[Point])]): Unit = {
//    val encloseEventsEveryFrame = if(enclosure.isEmpty) Nil else List(EncloseEvent(enclosure))
//    val actionEventsEveryFrame = actionMap.getOrElse(frameCount, Map.empty).toList.map(a => DirectionEvent(a._1, a._2))
//    val eventsEveryFrame: List[GameEvent] = actionEventsEveryFrame ::: encloseEventsEveryFrame
//    val evts = (eventsEveryFrame, stateEveryFrame) match {
//      case (Nil, None) => None
//      case (events, state) => Some(events, state)
//      case _ => None
//    }
//    eventFrames :+= evts
//
//    if (eventFrames.lengthCompare(maxRecordNum) > 0) { //每一百帧写入文件
//      eventFrames.foreach {
//        case Some((events, Some(state))) =>
//          recorder.writeFrame(events.fillMiddleBuffer(middleBuffer).result(), Some(state.fillMiddleBuffer(middleBuffer).result()))
//        case Some((events, None)) => recorder.writeFrame(events.fillMiddleBuffer(middleBuffer).result())
//        case None => recorder.writeEmptyFrame()
//      }
//      eventFrames = List.empty[Option[(List[DirectionEvent], Option[Snapshot])]]
//      fileRecordNum += eventFrames.size
//    }
//
//    if (fileRecordNum > fileMaxRecordNum) { //文件写满
//      recorder.finish()
//      fileIndex += 1
//      val initState = if(stateEveryFrame.nonEmpty) stateEveryFrame else Some(Snapshot(grid, snakes, Nil))
//      recorder = getRecorder(fileName, fileIndex, GameInformation(System.currentTimeMillis()), initState)
//    }
//    stateEveryFrame = None
//
//  }

//  override def updateSnakes(origin: String): List[(String, List[Point])] = {
//    var finishFields = List.empty[(String, List[Point])]
//
//    def updateASnake(snake: SkDt, actMap: Map[String, Int]): Either[String, UpdateSnakeInfo] = {
//      val keyCode = actMap.get(snake.id)
//      val newDirection = {
//        val keyDirection = keyCode match {
//          case Some(KeyEvent.VK_LEFT) => Point(-1, 0)
//          case Some(KeyEvent.VK_RIGHT) => Point(1, 0)
//          case Some(KeyEvent.VK_UP) => Point(0, -1)
//          case Some(KeyEvent.VK_DOWN) => Point(0, 1)
//          case _ => snake.direction
//        }
//        if (keyDirection + snake.direction != Point(0, 0)) {
//          keyDirection
//        } else {
//          snake.direction
//        }
//      }
//
//      if (newDirection != Point(0, 0)) {
//        val newHeader = snake.header + newDirection
//
//        grid.get(newHeader) match {
//          case Some(x: Body) => //进行碰撞检测
//            debug(s"snake[${snake.id}] hit wall.")
//            if (x.id != snake.id) { //撞到了别人的身体
//              killHistory += x.id -> (snake.id, snake.name, frameCount)
//            }
//            mayBeDieSnake += x.id -> snake.id
//            grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
//              case Some(Field(fid)) if fid == snake.id =>
//                snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(newHeader.x.toInt, newHeader.y.toInt))))
//                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header), x.fid))
//
//              case Some(Body(bid, _)) if bid == snake.id && x.fid.getOrElse(-1L) == snake.id =>
//                enclosure(snake, origin, newHeader, newDirection)
//
//              case _ =>
//                if (snake.direction != newDirection)
//                  snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(snake.header.x.toInt, snake.header.y.toInt))))
//                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), x.fid))
//            }
//
//          case Some(Field(id)) =>
//            if (id == snake.id) {
//              grid(snake.header) match {
//                case Body(bid, _) if bid == snake.id => //回到了自己的领域
//                  enclosure(snake, origin, newHeader, newDirection)
//
//                case _ =>
//                  Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), Some(id)))
//              }
//            } else { //进入到别人的领域
//              grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
//                case Some(Field(fid)) if fid == snake.id =>
//                  snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(newHeader.x.toInt, newHeader.y.toInt))))
//                  Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header), Some(id)))
//                case _ =>
//                  if (snake.direction != newDirection)
//                    snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(snake.header.x.toInt, snake.header.y.toInt))))
//                  Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection), Some(id)))
//              }
//            }
//
//          case Some(Border) =>
//            Left(snake.id)
//
//          case _ =>
//            grid.get(snake.header) match { //当上一点是领地时 记录出行的起点
//              case Some(Field(fid)) if fid == snake.id =>
//                snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(newHeader.x.toInt, newHeader.y.toInt))))
//                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection, startPoint = snake.header)))
//
//              case _ =>
//                if (snake.direction != newDirection)
//                  snakeTurnPoints += ((snake.id, snakeTurnPoints.getOrElse(snake.id, Nil) ::: List(Point4Trans(snake.header.x.toInt, snake.header.y.toInt))))
//                Right(UpdateSnakeInfo(snake.copy(header = newHeader, direction = newDirection)))
//            }
//        }
//      }
//      else Right(UpdateSnakeInfo(snake, Some(snake.id)))
//
//    }
//
//    var mapKillCounter = Map.empty[String, Int]
//    var updatedSnakes = List.empty[UpdateSnakeInfo]
//    var killedSnaked = List.empty[String]
//
//    historyStateMap += frameCount -> (snakes, grid)
//
//    val acts = actionMap.getOrElse(frameCount, Map.empty[String, Int])
//
//    snakes.values.map(updateASnake(_, acts)).foreach {
//      case Right(s) =>
//        updatedSnakes ::= s
//
//      case Left(sid) =>
//        killedSnaked ::= sid
//    }
//
//    val intersection = mayBeSuccess.keySet.filter(p => mayBeDieSnake.keys.exists(_ == p))
//    if (intersection.nonEmpty) {
//      intersection.foreach { snakeId => // 在即将完成圈地的时候身体被撞击则不死但此次圈地作废
//        mayBeSuccess(snakeId).foreach { i =>
//          i._2 match {
//            case Body(_, fid) if fid.nonEmpty => grid += i._1 -> Field(fid.get)
//            case Field(fid) => grid += i._1 -> Field(fid)
//            case _ => grid -= i._1
//          }
//        }
//        mayBeDieSnake -= snakeId
//        killHistory -= snakeId
//      }
//    }
//
//    //if two (or more) headers go to the same point
//    val snakesInDanger = updatedSnakes.groupBy(_.data.header).filter(_._2.lengthCompare(1) > 0).flatMap { res =>
//      val sids = res._2.map(_.data.id)
//      grid.get(res._1) match {
//        case Some(Field(fid)) if sids.contains(fid) =>
//          sids.filterNot(_ == fid).foreach { killedId =>
//            mayBeDieSnake += killedId -> fid
//            killHistory += killedId -> (killedId, snakes.find(_._1 == fid).get._2.name, frameCount)
//          }
//          sids.filterNot(_ == fid)
//        case _ => sids
//      }
//    }.toList
//
//    mayBeDieSnake.foreach { s =>
//      mapKillCounter += s._2 -> (mapKillCounter.getOrElse(s._2, 0) + 1)
//      killedSnaked ::= s._1
//    }
//
//    finishFields = mayBeSuccess.map(i => (i._1, i._2.keys.toList)).toList
//
//    val noHeaderSnake = snakes.filter(s => finishFields.flatMap(_._2).contains(updatedSnakes.find(_.data.id == s._2.id).getOrElse(UpdateSnakeInfo(SkDt((-1).toString, "", "", Point(0, 0), Point(-1, -1)))).data.header)).keySet
//
//    mayBeDieSnake = Map.empty[String, String]
//    mayBeSuccess = Map.empty[String, Map[Point, Spot]]
//
//    val noFieldSnake = snakes.keySet &~ grid.map(_._2 match { case Field(uid) => uid case _ => "" }).toSet.filter(_ != "") //若领地全被其它玩家圈走则死亡
//
//    val finalDie = snakesInDanger ::: killedSnaked ::: noFieldSnake.toList ::: noHeaderSnake.toList
//
//    //    println(s"snakeInDanger:$snakesInDanger\nkilledSnaked:$killedSnaked\nnoFieldSnake:$noFieldSnake\nnoHeaderSnake:$noHeaderSnake")
//
//    val fullSize = (BorderSize.w - 2) * (BorderSize.h - 2)
//    finalDie.foreach { sid =>
//      roomManager ! UserDead(sid, snakes(sid).name)
//      val score = grid.filter(_._2 match { case Field(fid) if fid == sid => true case _ => false }).toList.length.toFloat*100 / fullSize
//      val killing = if (snakes.contains(sid)) snakes(sid).kill else 0
//      val nickname = if (snakes.contains(sid)) snakes(sid).name else "Unknown"
//      val startTime = startTimeMap(sid)
//      val endTime = System.currentTimeMillis()
//      val msg: Future[String] = tokenActor ? AskForToken
//      msg.map { token =>
//        EsheepClient.inputBatRecord(sid.toString, nickname, killing, 1, score.formatted("%.2f").toFloat, "", startTime, endTime, token)
//      }
//      returnBackField(sid)
//      grid ++= grid.filter(_._2 match { case Body(_, fid) if fid.nonEmpty && fid.get == sid => true case _ => false }).map { g =>
//        Point(g._1.x, g._1.y) -> Body(g._2.asInstanceOf[Body].id, None)
//      }
//      snakeTurnPoints -= sid
//    }
//
//    val newSnakes = updatedSnakes.filterNot(s => finalDie.contains(s.data.id)).map { s =>
//      mapKillCounter.get(s.data.id) match {
//        case Some(k) => s.copy(data = s.data.copy(kill = k + s.data.kill))
//        case None => s
//      }
//    }
//
//    newSnakes.foreach { s =>
//      if (s.bodyInField.nonEmpty && s.bodyInField.get == s.data.id) grid += s.data.header -> Field(s.data.id)
//      else grid += s.data.header -> Body(s.data.id, s.bodyInField)
//    }
//
//    snakes = newSnakes.map(s => (s.data.id, s.data)).toMap
//
//    finishFields
//  }

}
