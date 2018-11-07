package com.neo.sk.carnie.scene

import com.neo.sk.carnie.common.Constant.ColorsSetting
import com.neo.sk.carnie.paperClient.Protocol.Data4TotalSync
import com.neo.sk.carnie.paperClient._
import javafx.scene.canvas.Canvas
import javafx.scene.{Group, Scene}
import javafx.scene.input.KeyCode
import javafx.scene.text.{Font, FontPosture, FontWeight, Text}

/**
  * Created by dry on 2018/10/29.
  **/

object GameScene{
  trait GameSceneListener {
    def onKeyPressed(e: KeyCode): Unit
  }
}

class GameScene {

  import GameScene._

  var gameSceneListener: GameSceneListener = _

  val viewWidth = 1800
  val viewHeight = 900
  val rankWidth = 1800
  val rankHeight = 300
  val group = new Group()
  val backgroundCanvas = new Canvas()
  val viewCanvas = new Canvas()
  val rankCanvas = new Canvas()
//  rankCanvas.setStyle("z-index = 100")
//  viewCanvas.setStyle("z-index = 120")
  backgroundCanvas.setHeight(viewHeight)
  backgroundCanvas.setWidth(viewWidth)

  viewCanvas.setHeight(viewHeight)
  viewCanvas.setWidth(viewWidth)

  rankCanvas.setHeight(rankHeight)
  rankCanvas.setWidth(rankWidth)

  group.getChildren.add(viewCanvas)
  group.getChildren.add(backgroundCanvas)
  group.getChildren.add(rankCanvas)

  val background = new BackgroundCanvas(backgroundCanvas)
  val view = new GameViewCanvas(viewCanvas,background)
  val rank = new RankCanvas(rankCanvas)



  private val viewCtx = viewCanvas.getGraphicsContext2D

  val getScene: Scene = new Scene(group)

  def draw(uid: String, data: Data4TotalSync, offsetTime: Long, grid: Grid, championId: String): Unit = {
    //    background.drawCache(view.offXY(uid, data, offsetTime, grid)._1 , view.offXY(uid, data, offsetTime, grid)._2)
    view.drawGrid(uid, data, offsetTime, grid, championId)
    view.drawSmallMap(data.snakes.filter(_.id == uid).map(_.header).head, data.snakes.filterNot(_.id == uid))
  }

  def drawGameWait(): Unit = {
    viewCtx.save()
    viewCtx.setFill(ColorsSetting.dieInfoBackgroundColor)
    viewCtx.fillRect(0, 0, viewWidth, viewHeight)
    viewCtx.setFill(ColorsSetting.dieInfoFontColor)
    viewCtx.setFont(Font.font(30))
    viewCtx.fillText("Please wait.", 150, 180)
    viewCtx.restore()
  }

  def drawGameOff(firstCome: Boolean): Unit = {
    viewCtx.save()
    viewCtx.setFill(ColorsSetting.dieInfoBackgroundColor)
    viewCtx.fillRect(0, 0, viewWidth, viewHeight)
    viewCtx.setFill(ColorsSetting.dieInfoFontColor)
    if (firstCome) {
      viewCtx.setFont(Font.font(30))
      viewCtx.fillText("Welcome.", 150, 180)
    } else {
      viewCtx.setFont(Font.font(30))
      viewCtx.fillText("Ops, connection lost.", 150, 180)
    }
    viewCtx.restore()
  }

  def drawGameWin(myId: String, winner: String, data: Data4TotalSync): Unit = {
    view.drawGameWin(myId: String, winner: String, data: Data4TotalSync)
  }

  def drawGameDie(killerOpt: Option[String], currentRank: List[Score], myId: String,startTime: Long): Unit = {
    rank.drawClearRank()
    view.drawGameDie(killerOpt, currentRank, myId,startTime)
  }


  def drawRank(myId: String, snakes: List[SkDt], currentRank: List[Score]): Unit = {
    rank.drawRank(myId, snakes, currentRank)
  }

  def drawUserDieInfo(killedName: String, killerName: String): Unit = {
    view.drawUserDieInfo(killedName,killerName)
  }

  def drawGameDieStartTime(): Unit = {
    view.cleanMyScore
  }

  def setGameSceneListener(listener: GameSceneListener) {
    gameSceneListener = listener
  }

}
