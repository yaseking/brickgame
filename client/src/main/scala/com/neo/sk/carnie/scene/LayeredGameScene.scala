package com.neo.sk.carnie.scene

/**
  * Created by dry on 2018/12/5.
  */

import com.neo.sk.carnie.common.Constant.{ColorsSetting,_}
import com.neo.sk.carnie.paperClient.Protocol.{Data4TotalSync, WinData}
import com.neo.sk.carnie.paperClient._
import javafx.scene.canvas.Canvas
import javafx.scene.{Group, Scene}
import javafx.scene.input.KeyCode
import javafx.scene.media.AudioClip
import javafx.scene.text.{Font, FontPosture, FontWeight, Text}
import javafx.stage.Screen


class LayeredGameScene (img: Int, frameRate: Int) {

  import GameScene._


  val screen= Screen.getPrimary.getVisualBounds
  println(s"----width--${screen.getMaxX.toInt}")
  println(s"----height--${screen.getMaxY.toInt}")
  protected val viewWidth = layeredCanvasWidth
  protected val viewHeight = layeredCanvasHeight
  private val rankWidth = viewWidth
  private val rankHeight = viewHeight/2
  val group = new Group()

  val positionCanvas = new Canvas()
  val viewCanvas = new Canvas()
  val rankCanvas = new Canvas()
  val BorderCanvas = new Canvas()
  val selfViewCanvas = new Canvas()
  val selfCanvas = new Canvas()

  positionCanvas.setHeight(viewHeight)
  positionCanvas.setWidth(viewWidth)
  positionCanvas.setLayoutY(50)
  positionCanvas.setLayoutX(50)

  viewCanvas.setHeight(viewHeight)
  viewCanvas.setWidth(viewWidth)
//  viewCanvas.setLayoutY(50)
//  viewCanvas.setLayoutX(500)


  rankCanvas.setHeight(rankHeight)
  rankCanvas.setWidth(rankWidth)



  BorderCanvas.setHeight(viewHeight)
  BorderCanvas.setWidth(viewWidth)
  BorderCanvas.setLayoutY(50)
  BorderCanvas.setLayoutX(600)

  selfViewCanvas.setHeight(200)
  selfViewCanvas.setWidth(viewWidth)
  selfViewCanvas.setLayoutY(50)
  selfViewCanvas.setLayoutX(1200)

  selfCanvas.setHeight(viewHeight)
  selfCanvas.setWidth(viewWidth)
  selfCanvas.setLayoutY(400)
  selfCanvas.setLayoutX(50)

  group.getChildren.add(viewCanvas)
  group.getChildren.add(rankCanvas)
  group.getChildren.add(positionCanvas)
  group.getChildren.add(BorderCanvas)
  group.getChildren.add(selfViewCanvas)
  group.getChildren.add(selfCanvas)

  val view = new GameViewCanvas(viewCanvas, rankCanvas, img)
  val rank = new RankCanvas(rankCanvas)
  val layered = new LayeredCanvas(viewCanvas,rankCanvas,positionCanvas,BorderCanvas,selfViewCanvas,selfCanvas,img)


  private val viewCtx = viewCanvas.getGraphicsContext2D
  private val selfViewCtx = selfViewCanvas.getGraphicsContext2D

  val getScene: Scene = new Scene(group)

  def resetScreen(viewWidth: Int,viewHeight: Int,rankWidth: Int,rankHeight: Int): Unit = {
    //    val viewWidth = 1200//1800
    //    val viewHeight = 750//900
    //    val rankWidth = 1200//1800
    //    val rankHeight = 250//300

    rank.resetRankView(rankWidth, rankHeight)
    view.resetScreen(viewWidth, viewHeight, rankWidth, rankHeight)

    //    viewCanvas.setWidth(viewWidth)
    //    viewCanvas.setHeight(viewHeight)
    //
    //    rankCanvas.setWidth(rankWidth)
    //    rankCanvas.setHeight(rankHeight)
  }

  def draw(uid: String, data: Data4TotalSync, offsetTime: Long, grid: Grid, championId: String): Unit = {
//    view.drawGrid(uid, data, offsetTime, grid, championId, frameRate)
//    view.drawSmallMap(data.snakes.filter(_.id == uid).map(_.header).head, data.snakes.filterNot(_.id == uid))

    layered.drawPosition(data.snakes.filter(_.id == uid).map(_.header).head,data.snakes.find(_.id == championId).map(_.header),uid == championId)
    layered.drawBorder()
    layered.drawSelfView(uid, data, offsetTime, grid, championId, frameRate)
    layered.drawSelf(uid, data, offsetTime, grid, championId, frameRate)
  }

  def drawGameWait(): Unit = {
    selfViewCtx.save()
    selfViewCtx.setFill(ColorsSetting.dieInfoBackgroundColor)
    selfViewCtx.fillRect(0, 0, viewWidth, viewHeight)
    selfViewCtx.setFill(ColorsSetting.dieInfoFontColor)
    selfViewCtx.setFont(Font.font(30))
    selfViewCtx.fillText("Please wait.", 150, 180)
    selfViewCtx.restore()
  }

  def drawGameOff(firstCome: Boolean): Unit = {
    rank.drawClearRank()
    selfViewCtx.save()
    selfViewCtx.setFill(ColorsSetting.dieInfoBackgroundColor)
    selfViewCtx.fillRect(0, 0, viewWidth, viewHeight)
    selfViewCtx.setFill(ColorsSetting.dieInfoFontColor)
    if (firstCome) {
      selfViewCtx.setFont(Font.font(30))
      selfViewCtx.fillText("Welcome.", 150, 180)
    } else {
      selfViewCtx.setFont(Font.font(30))
      selfViewCtx.fillText("Ops, connection lost.", 150, 180)
    }
    selfViewCtx.restore()
  }

  def drawGameWin(myId: String, winner: String, data: Data4TotalSync,winningData:WinData): Unit = {
    rank.drawClearRank()
    layered.drawGameWin(myId: String, winner: String, data: Data4TotalSync,winningData:WinData)
  }

  def drawGameDie(killerOpt: Option[String], myScore: BaseScore, maxArea: Int): Unit = {
    rank.drawClearRank()
    layered.drawGameDie(killerOpt, myScore,maxArea)
  }


  def drawRank(myId: String, snakes: List[SkDt], currentRank: List[Score]): Unit = {
    rank.drawRank(myId, snakes, currentRank)
  }

  def drawBarrage(killedName: String, killerName: String): Unit = {
    layered.drawUserDieInfo(killedName,killerName)
  }

}
