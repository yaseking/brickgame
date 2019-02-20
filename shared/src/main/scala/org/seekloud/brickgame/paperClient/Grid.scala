package org.seekloud.brickgame.paperClient

import java.awt.event.KeyEvent

import org.seekloud.brickgame.paperClient
import org.seekloud.brickgame.paperClient.Protocol._

import scala.util.Random
import scala.collection.mutable

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 5:34 PM
  */
trait Grid {

//  val boundary: Point

  def debug(msg: String): Unit

  def info(msg: String): Unit

  val random = new Random(System.nanoTime())

  val maxDelayed = 11 //最大接收10帧以内的延时
  val historyRankLength = 5
  var frameCount = 0
  var gameDuration = 0 //游戏时间
  var grid: Map[Point, Spot] = Map[Point, Spot]()
  var players = Map.empty[Int, PlayerDt] //(id, PlayerDt)
  var snakes = Map.empty[String, SkDt]
  var actionMap = Map.empty[Int, Map[Int, Int]] //Map[frameCount,Map[id, keyCode]]
  var mayBeDieSnake = Map.empty[String, String] //可能死亡的蛇 killedId,killerId
  var mayBeSuccess = Map.empty[String, Map[Point, Spot]] //圈地成功后的被圈点 userId,points
  var historyStateMap = Map.empty[Int, Map[Int, PlayerDt]]

//  val defaultHeight = 3
//  (0 until 20).foreach {x =>
//    (0 until 3).foreach {y =>
//      grid += Point(x, y) -> Brick
//    }
//  }

  def addAction(id: Int, keyCode: Byte): Unit = {
    addActionWithFrame(id, keyCode, frameCount)
  }

  def addActionWithFrame(id: Int, keyCode: Byte, frame: Int): Unit = {
    val map = actionMap.getOrElse(frame, Map.empty)
    val direction = if(keyCode == 37) -1 else if(keyCode == 39) 1 else 0
    val tmp = map + (id -> direction)
    println(s"addedAction: $tmp")
    actionMap += (frame -> tmp)
  }

  def initAction(id: Int): Unit = {
    val player = players.get(id)
    if(player.nonEmpty) {
      val newVelocityX = 0.5f
      val newVelocityY = -1
      players += id -> player.get.copy(velocityX = newVelocityX, velocityY = newVelocityY)
    }
  }

  def checkActionFrame(id: Int, frontFrame: Int): Int = {
    val backendFrame = Math.max(frontFrame, frameCount)
    val existFrame = actionMap.map { a => (a._1, a._2.filter(_._1 == id)) }.filter(_._2.nonEmpty).keys
    try {
      Math.max(existFrame.max + 1, backendFrame)
    } catch {
      case e: Exception =>
        backendFrame
    }
  }

//  def deleteActionWithFrame(id: String, frame: Int): Unit = {
//    val map = actionMap.getOrElse(frame, Map.empty)
//    val tmp = map - id
//    actionMap += (frame -> tmp)
//  }
//
//  def nextDirection(id: String): Option[Point] = {
//    val map = actionMap.getOrElse(frameCount, Map.empty)
//    map.get(id) match {
//      case Some(KeyEvent.VK_LEFT) => Some(Point(-1, 0))
//      case Some(KeyEvent.VK_RIGHT) => Some(Point(1, 0))
//      case Some(KeyEvent.VK_UP) => Some(Point(0, -1))
//      case Some(KeyEvent.VK_DOWN) => Some(Point(0, 1))
//      case _ => None
//    }
//  }

  def reBornPlank(id: Int): Map[Point, Spot] = {
    var field = players(id).field
    val location = players(id).location
    (location until location+plankLen).foreach{x =>
      field -= Point(x, 30)
    }
    (plankOri until plankOri+plankLen).foreach{x =>
      field += Point(x, 30) -> Plank
    }
    field
  }

  def updateBalls: List[Int] = { //该为向后台返回死亡名单

    var deadPlayers:List[Int] = List.empty[Int]

    def updateAPlayer(p: PlayerDt, acts: Map[Int, Int]): Unit = {
      val direction = acts.getOrElse(p.id, 0)
      var newLocation = p.location
      var newBallLocation = Point(p.velocityX + p.ballLocation.x, p.velocityY + p.ballLocation.y)
      val field = p.field
      var newField = p.field
      var newScore = p.score
      var newVelocityX = p.velocityX
      var newVelocityY = p.velocityY
      field.get(newBallLocation.toInt) match {
        case Some(Brick) =>
          newField -= newBallLocation.toInt
          newScore += 1
          newVelocityY = - p.velocityY
          newBallLocation = Point(p.velocityX + p.ballLocation.x, p.ballLocation.y + newVelocityY)
          newField.get(newBallLocation.toInt) match { //该方法可以另用一个
            case Some(Brick) =>
              newField -= newBallLocation.toInt
              newScore += 1
              newVelocityX = -p.velocityX
              newBallLocation = Point(newVelocityX + p.ballLocation.x, p.ballLocation.y + newVelocityY)

            case Some(DeadLine) =>
              //不做处理，正常死亡

            case Some(_) =>
              newVelocityX = -p.velocityX
              newBallLocation = Point(newVelocityX + p.ballLocation.x, p.ballLocation.y + newVelocityY)

            case _ =>
          }

        case Some(Plank) =>
          newVelocityY = - p.velocityY
          newBallLocation = Point(p.velocityX + p.ballLocation.x, p.ballLocation.y + newVelocityY)
          newField.get(newBallLocation.toInt) match {
            case Some(Brick) =>
              newField -= newBallLocation.toInt
              newScore += 1
              newVelocityX = -p.velocityX
              newBallLocation = Point(newVelocityX + p.ballLocation.x, p.ballLocation.y + newVelocityY)

            case Some(DeadLine) =>
            //不做处理，正常死亡

            case Some(_) =>
              newVelocityX = -p.velocityX
              newBallLocation = Point(newVelocityX + p.ballLocation.x, p.ballLocation.y + newVelocityY)

            case _ =>
          }

        case Some(TopBorder) =>
          newVelocityY = - p.velocityY
          newBallLocation = Point(p.velocityX + p.ballLocation.x, p.ballLocation.y + newVelocityY)
          newField.get(newBallLocation.toInt) match {
            case Some(Brick) =>
              newField -= newBallLocation.toInt
              newScore += 1
              newVelocityX = -p.velocityX
              newBallLocation = Point(newVelocityX + p.ballLocation.x, p.ballLocation.y + newVelocityY)

            case Some(DeadLine) =>
            //不做处理，正常死亡

            case Some(_) =>
              newVelocityX = -p.velocityX
              newBallLocation = Point(newVelocityX + p.ballLocation.x, p.ballLocation.y + newVelocityY)

            case _ =>
          }

        case Some(SideBorder) =>
          newVelocityX = - p.velocityX
          newBallLocation = Point(p.ballLocation.x + newVelocityX, p.ballLocation.y + p.velocityY)
          newField.get(newBallLocation.toInt) match {
            case Some(Brick) =>
              newField -= newBallLocation.toInt
              newScore += 1
              newVelocityY = -p.velocityY
              newBallLocation = Point(newVelocityX + p.ballLocation.x, p.ballLocation.y + newVelocityY)

            case Some(DeadLine) =>
            //不做处理，正常死亡

            case Some(_) =>
              newVelocityY = -p.velocityY
              newBallLocation = Point(newVelocityX + p.ballLocation.x, p.ballLocation.y + newVelocityY)

            case _ =>
          }

        case Some(DeadLine) =>
          deadPlayers =  p.id :: deadPlayers
          //清除死亡次的蛇

        case _ =>
      }

      //Plank信息修改，field信息需要加上board和plank，dealLine的信息
      if(direction == 1 && p.location+plankLen < OriginField.w+1) {
        newLocation += 1
        newField -= Point(p.location, 30)
        newField += Point(newLocation+plankLen-1, 30) -> Plank
        if(p.velocityX == 0 && p.velocityY ==0) {
          newBallLocation = Point(p.ballLocation.x+1, p.ballLocation.y)
        }
      } else if(direction == -1 && p.location > 1) {
        newLocation -= 1
        newField -= Point(p.location+plankLen-1, 30)
        newField += Point(newLocation, 30) -> Plank
        if(p.velocityX == 0 && p.velocityY ==0) {
          newBallLocation = Point(p.ballLocation.x-1, p.ballLocation.y)
        }
      }

      players += p.id -> p.copy(location = newLocation, velocityX = newVelocityX, velocityY = newVelocityY, ballLocation = newBallLocation, field = newField, score = newScore)

    }

    historyStateMap += frameCount -> players

    val acts = actionMap.getOrElse(frameCount, Map.empty[Int, Int])

    players.values.foreach(updateAPlayer(_, acts))

//    players --= deadPlayers

    deadPlayers
  }

  def update: List[Int] = {
    val deadList = updateBalls
    val limitFrameCount = frameCount - (maxDelayed + 1)
    actionMap = actionMap.filter(_._1 > limitFrameCount)
    historyStateMap = historyStateMap.filter(_._1 > limitFrameCount)
    frameCount += 1
    deadList
  }

  def getPointBelong(id: String, point: Point): Boolean = {
    grid.get(point) match {
      case Some(Field(fid)) if fid == id => true
      case _ => false
    }
  }

}

