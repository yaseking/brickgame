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

  val viewWidth = 1200
  val viewHeight = 600
  val rankWidth = 250
  val rankHeight = 300
  val group = new Group
  val backgroundCanvas = new Canvas(viewWidth, viewHeight)
  val viewCanvas = new Canvas(viewWidth, viewHeight)
  val rankCanvas = new Canvas(rankWidth, rankHeight)
//  rankCanvas.setStyle("z-index = 100")
//  viewCanvas.setStyle("z-index = 120")
  //十一月 01, 2018 10:02:09 上午 com.sun.javafx.css.parser.CSSParser declaration
  //WARNING: CSS Error parsing '*{z-index = 120}: Expected COLON at [1,10]
  //十一月 01, 2018 10:02:09 上午 com.sun.javafx.css.parser.CSSParser declaration
  //WARNING: CSS Error parsing '*{z-index = 100}: Expected COLON at [1,10]

  val scene = new Scene(group)
  group.getChildren.add(backgroundCanvas)
  group.getChildren.add(viewCanvas)
  group.getChildren.add(rankCanvas)

  val view = new GameViewCanvas(viewCanvas)
  val background = new BackgroundCanvas(backgroundCanvas)
  val rank = new RankCanvas(rankCanvas)

  viewCanvas.requestFocus()
  viewCanvas.setOnKeyPressed(event => gameSceneListener.onKeyPressed(event.getCode))

  background.drawCache()

  private val viewCtx = viewCanvas.getGraphicsContext2D

  def getScene: Scene = new Scene(group)

  def draw(uid: String, data: Data4TotalSync, offsetTime: Long, grid: Grid, championId: String): Unit = {
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

  def drawGameDie(killerOpt: Option[String], myScore: BaseScore = BaseScore(0,0,0,0), maxArea: Int = 0): Unit = {
    view.drawGameDie(killerOpt, myScore, maxArea)
  }


  def drawRank(myId: String, snakes: List[SkDt], currentRank: List[Score]): Unit = {
    rank.drawRank(myId, snakes, currentRank)
  }


  def setGameSceneListener(listener: GameSceneListener) {
    gameSceneListener = listener
  }

}
