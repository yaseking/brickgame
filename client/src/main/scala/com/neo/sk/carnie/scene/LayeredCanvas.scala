package com.neo.sk.carnie.scene

//import java.awt.Graphics
//import java.io.File

import com.neo.sk.carnie.paperClient._
import com.neo.sk.carnie.paperClient.Protocol.{Data4TotalSync, FieldByColumn, WinData}
import javafx.scene.canvas.Canvas
import javafx.scene.image.Image
import javafx.scene.paint.Color
import javafx.scene.text.{Font, FontPosture, FontWeight, Text}
import com.neo.sk.carnie.common.Constant
import com.neo.sk.carnie.common.Constant.ColorsSetting
import javafx.geometry.VPos
//import javafx.scene.SnapshotParameters
//import javafx.scene.media.{AudioClip, AudioEqualizer, Media, MediaPlayer}
import org.slf4j.LoggerFactory

/**
  * Created by dry on 2018/10/29.
  **/
class LayeredCanvas(viewCanvas: Canvas,rankCanvas: Canvas,positionCanvas: Canvas,BorderCanvas: Canvas,
  selfViewCanvas: Canvas,selfCanvas: Canvas, img: Int) {//,background: BackgroundCanvas
  private val realWindow = Point(selfViewCanvas.getWidth.toFloat, selfViewCanvas.getHeight.toFloat)
  private val window = Point(Window.w, Window.h)
  private val border = Point(BorderSize.w, BorderSize.h)
  private var windowBoundary = Point(viewCanvas.getWidth.toFloat, viewCanvas.getHeight.toFloat)
  private var positionWindowBoundary = Point(positionCanvas.getWidth.toFloat, positionCanvas.getHeight.toFloat)
  
  private var positionCanvasUnit = positionWindowBoundary.x / (border.x + 10)
  private var positionCanvasUnitY = positionWindowBoundary.y / border.y

  private val viewCtx = viewCanvas.getGraphicsContext2D
  private val rankCtx = rankCanvas.getGraphicsContext2D
  private val positionCtx = positionCanvas.getGraphicsContext2D
  private val BorderCtx = BorderCanvas.getGraphicsContext2D
  private val selfViewCtx = selfViewCanvas.getGraphicsContext2D
  private val selfCtx = selfCanvas.getGraphicsContext2D
  private val canvasSize = (border.x - 2) * (border.y - 2)
  private val imgMap: Map[Int, Image] =
    Map(
      0 -> new Image("img/luffy.png"),
      1 -> new Image("img/fatTiger.png"),
      2 -> new Image("img/Bob.png"),
      3 -> new Image("img/yang.png"),
      4 -> new Image("img/smile.png"),
      5 -> new Image("img/pig.png")
    )
  private val championHeaderImg = new Image("champion.png")
  private val myHeaderImg = imgMap(img)
  private val crownImg = new Image("crown.png")
  private var canvasUnit = positionWindowBoundary.x / window.x
  private var canvasUnitY =   positionWindowBoundary.y / window.y
  private var scale = 1.0
  private val smallMap = Point(littleMap.w, littleMap.h)
  private val textLineHeight = 15
  private[this] val log = LoggerFactory.getLogger(this.getClass)

  def debug(str: String):Unit = log.debug(s"$str")

  def resetScreen(viewWidth:Int, viewHeight:Int, rankWidth:Int, rankHeight:Int):Unit = {
    viewCanvas.setWidth(viewWidth)
    viewCanvas.setHeight(viewHeight)
    rankCanvas.setWidth(rankWidth)
    rankCanvas.setHeight(rankHeight)
    windowBoundary = Point(viewCanvas.getWidth.toFloat, viewCanvas.getHeight.toFloat)
//    positionWindowBoundary = Point(positionCanvas.getWidth.toFloat, positionCanvas.getHeight.toFloat)
    canvasUnit = (windowBoundary.x / window.x).toInt
    canvasUnitY = (windowBoundary.y / window.y).toInt
//    positionCanvasUnit = (positionWindowBoundary.x / border.x).toInt
//    positionCanvasUnitY = (positionWindowBoundary.y / border.y).toInt
  }

  var a=0

  def drawPosition(myHeader: Point,championHeader: Option[Point],isMe: Boolean):Unit = {
//    val offx = myHeader.x.toDouble / border.x * window.x
//    val offy = myHeader.y.toDouble / border.y * window.y
    val offx = myHeader.x.toDouble * positionCanvasUnit
    val offy = myHeader.y.toDouble * positionCanvasUnit
    positionCtx.setFill(ColorsSetting.backgroundColor)
    val w = positionWindowBoundary.x //400
    val h = positionWindowBoundary.y //300
    positionCtx.clearRect(0,0,w,h)
//    a += 1
//    if(a % 200 ==0) println(w,h,positionCanvasUnit)
    positionCtx.save()
    positionCtx.setGlobalAlpha(0.5)
    positionCtx.fillRect(0, 0, w , h )
    positionCtx.restore()

    positionCtx.setFill(Color.rgb(105,105,105))

    positionCtx.fillRect(positionCanvasUnit, positionCanvasUnit, positionCanvasUnit * border.x, positionCanvasUnit)
    positionCtx.fillRect(positionCanvasUnit, positionCanvasUnit, positionCanvasUnit, positionCanvasUnit * border.y)
    positionCtx.fillRect(positionCanvasUnit, border.y * positionCanvasUnit, positionCanvasUnit * (border.x + 1), positionCanvasUnit)
    positionCtx.fillRect(border.x  * positionCanvasUnit, positionCanvasUnit, positionCanvasUnit, positionCanvasUnit * (border.y + 1))

    positionCtx.restore()
    positionCtx.fillRect( offx - window.x / 2 * positionCanvasUnit , offy  - window.y / 2 * positionCanvasUnit, window.x * positionCanvasUnit, window.y * positionCanvasUnit)
    if(isMe){
      positionCtx.drawImage(championHeaderImg, offx - 5, offy - 5, 10, 10)
    }
    else{
      positionCtx.drawImage(myHeaderImg, offx - 5, offy - 5, 10, 10)
      if(championHeader.isDefined){
        positionCtx.drawImage(championHeaderImg, championHeader.get.x*positionCanvasUnit - 5, championHeader.get.y*positionCanvasUnit - 5, 10, 10)
      }
    }
  }

  def drawBorder(): Unit = {
    BorderCtx.setFill(ColorsSetting.backgroundColor)
    val w = positionWindowBoundary.x //400
    val h = positionWindowBoundary.y //300
    BorderCtx.clearRect(0,0,w,h)
    BorderCtx.save()
    BorderCtx.setGlobalAlpha(0.5)
    BorderCtx.fillRect(0, 0, w , h )
    BorderCtx.restore()

    BorderCtx.setFill(Color.rgb(105,105,105))

    BorderCtx.fillRect(positionCanvasUnit, positionCanvasUnit, positionCanvasUnit * border.x, positionCanvasUnit)
    BorderCtx.fillRect(positionCanvasUnit, positionCanvasUnit, positionCanvasUnit, positionCanvasUnit * border.y)
    BorderCtx.fillRect(positionCanvasUnit, border.y * positionCanvasUnit, positionCanvasUnit * (border.x + 1), positionCanvasUnit)
    BorderCtx.fillRect(border.x  * positionCanvasUnit, positionCanvasUnit, positionCanvasUnit, positionCanvasUnit * (border.y + 1))

    BorderCtx.restore()
  }


  def drawGameWin(myId: String, winner: String, data: Data4TotalSync,winningData:WinData): Unit = {
    val winnerId = data.snakes.find(_.name == winner).map(_.id).get
    val snakes = data.snakes
    val snakesFields = data.fieldDetails
    scale = 0.33
    val width = windowBoundary.x - BorderSize.w * canvasUnit * scale
    val height = windowBoundary.y - BorderSize.h * canvasUnit * scale
    selfViewCtx.clearRect(0, 0, windowBoundary.x, windowBoundary.y)
    selfViewCtx.save()
    selfViewCtx.scale(scale, scale)
    selfViewCtx.setFill(ColorsSetting.borderColor)
    selfViewCtx.fillRect(1.5 * width - canvasUnit, 1.5 * height - canvasUnit, canvasUnit * BorderSize.w, canvasUnit)
    selfViewCtx.fillRect(1.5 * width - canvasUnit, 1.5 * height - canvasUnit, canvasUnit, canvasUnit * BorderSize.h)
    selfViewCtx.fillRect(1.5 * width - canvasUnit, BorderSize.h * canvasUnit + 1.5 * height - canvasUnit, canvasUnit * (BorderSize.w + 1), canvasUnit)
    selfViewCtx.fillRect(BorderSize.w * canvasUnit + 1.5 * width - canvasUnit, 1.5 * height - canvasUnit, canvasUnit, canvasUnit * (BorderSize.h + 1))
    snakesFields.foreach { field =>
      if (field.uid == myId || field.uid == winnerId) {
        val color = snakes.find(_.id == field.uid).map(s => Constant.hex2Rgb(s.color)).get
        selfViewCtx.setFill(color)
        field.scanField.foreach { point =>
          point.x.foreach { x =>
            selfViewCtx.fillRect(x._1 * canvasUnit + 1.5 * width - canvasUnit, point.y * canvasUnit + 1.5 * height - canvasUnit, canvasUnit * (x._2 - x._1 + 1), canvasUnit * 1.05)
          }
        }
      }
    }
    selfViewCtx.restore()
    selfViewCtx.save()
    selfViewCtx.scale(1, 1)
    selfViewCtx.setGlobalAlpha(1)
    selfViewCtx.setFont(Font.font("Microsoft YaHei", FontPosture.findByName("bold"), 25))
    selfViewCtx.setFill(ColorsSetting.defaultColor)

    val txt1 = s"The Winner is $winner"
    val txt2 = s"Press space to reStart"
    val length = new Text(txt1).getLayoutBounds.getWidth
    selfViewCtx.fillText(txt1, (windowBoundary.x - length) / 2 , windowBoundary.y / 5)
    selfViewCtx.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20))
    //    ctx.setfont = "bold 24px Helvetica"
    selfViewCtx.setFill(ColorsSetting.fontColor2)
    val txt4 = s"WINNER SCORE:" + f"${winningData.winnerScore / canvasSize * 100}%.2f" + "%"
    val length1 = new Text(txt4).getLayoutBounds.getWidth
    if(winningData.yourScore.isDefined) {
      val txt3 = s"YOUR SCORE:" + f"${winningData.yourScore.get / canvasSize * 100}%.2f" + "%"
      selfViewCtx.fillText(txt3, (windowBoundary.x - length1) / 2 , windowBoundary.y / 4)
    }
    selfViewCtx.fillText(txt4, (windowBoundary.x - length1) / 2 , windowBoundary.y / 4 + 40)
    selfViewCtx.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20)) //FontPosture.findByName("bold")
    selfViewCtx.fillText(txt2, windowBoundary.x - 300, windowBoundary.y - 100)
    selfViewCtx.drawImage(crownImg, (windowBoundary.x - length) / 2 + length - 50, windowBoundary.y / 5 - 75, 50, 50)
    selfViewCtx.restore()
  }

  import javafx.scene.text.Text
  def drawUserDieInfo(killedName: String, killerName: String): Unit = {
    selfViewCtx.save()
    //    ctx.globalAlpha = 0.6

    selfViewCtx.restore()
    selfViewCtx.save()
    selfViewCtx.setFont(Font.font(30))
    selfViewCtx.setFill(ColorsSetting.gameNameColor)
    val txt = s"$killedName is killed by $killerName"
    val text = new Text(txt)
    text.setFont(Font.font(30))
    text.setFill(ColorsSetting.gameNameColor)
    val length = text.getLayoutBounds.getWidth
    val offx = length / 2
    //    ctx.drawImage(bloodImg, windowBoundary.x / 2 - offx, 115, 300, 50)
    selfViewCtx.fillText(s"$killedName is killed by $killerName", windowBoundary.x / 2 - offx, 150)
    selfViewCtx.restore()
  }

  def drawGameDie(killerOpt: Option[String],  myScore :BaseScore, maxArea: Int): Unit = {
    //    rankCtx.clearRect(0, 0, windowBoundary.x, windowBoundary.y)
    //    val endTime = System.currentTimeMillis()
    //    if (myScore.area > maxArea) maxArea = myScore.area
    selfViewCtx.setFill(ColorsSetting.dieInfoBackgroundColor)
    selfViewCtx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    selfViewCtx.setFill(ColorsSetting.gameNameColor)
    selfViewCtx.setFont(Font.font(20))
    scale = 1
    selfViewCtx.scale(1, 1)

    val text = killerOpt match {
      case Some(killer) => s"Ops, You Are Killed By $killer! Press Space Key To Revenge!"
      case None => "Ops, Press Space Key To Restart!"
    }
    val txt =new Text(text)
    val length = txt.getLayoutBounds.getWidth
    val offx = length / 2
    val x = (windowBoundary.x / 2).toInt - 145
    val y = (windowBoundary.y / 2).toInt - 180

    val gameTime = (myScore.endTime - myScore.startTime) / 1000
    val bestScore = maxArea / canvasSize * 100
    val time = {
      val tempM = gameTime / 60
      val s1 = gameTime % 60
      val s = if (s1 < 0) "00" else if (s1 < 10) "0" + s1 else s1.toString
      val m = if (tempM < 0) "00" else if (tempM < 10) "0" + tempM else tempM.toString
      m + ":" + s
    }
    selfViewCtx.fillText(text, windowBoundary.x / 2 - offx - 50 , y) //(500,180)
    selfViewCtx.save()
    selfViewCtx.setFill(ColorsSetting.dieInfoFontColor)
    selfViewCtx.setFont(Font.font(20))
    selfViewCtx.fillText("YOUR SCORE:", x, y + 70)
    selfViewCtx.fillText(f"${myScore.area / canvasSize * 100}%.2f" + "%", x + 230, y + 70)
    selfViewCtx.fillText("BEST SCORE:", x, y + 110)
    selfViewCtx.fillText(f"$bestScore%.2f" + "%", x + 230, y + 110)
    selfViewCtx.fillText(s"PLAYERS KILLED:", x, y + 150)
    selfViewCtx.fillText(s"${myScore.kill}", x + 230, y + 150)
    selfViewCtx.fillText(s"TIME PLAYED:", x, y + 190)
    selfViewCtx.fillText(s"$time", x + 230, y + 190)
    selfViewCtx.restore()
  }

  def drawDieBarrage(killedName: String, killerName: String): Unit = {
    selfViewCtx.save()
    selfViewCtx.setGlobalAlpha(0.6)
    //    ctx.drawImage(bloodImg, 670, 115, 300, 50)
    selfViewCtx.restore()
    selfViewCtx.save()
    selfViewCtx.setFill(Color.rgb(255, 88, 9))
    selfViewCtx.setFont(Font.font(25))
    val length = 30
    val offx = (270 - length) / 2
    selfViewCtx.fillText(s"$killedName is killed by $killerName", 670 + offx, 150)
    selfViewCtx.restore()
  }


  def drawCache(offx: Float, offy: Float): Unit = { //离屏缓存的更新--缓存边界
    //    ctx.clearRect(0,0,canvas.getWidth,canvas.getHeight)
    selfViewCtx.setFill(Color.rgb(105,105,105))

    //画边界
    selfViewCtx.fillRect(canvasUnit * offx, canvasUnit * offy, canvasUnit * BorderSize.w, canvasUnit)
    selfViewCtx.fillRect(canvasUnit * offx, canvasUnit * offy, canvasUnit, canvasUnit * BorderSize.h)
    selfViewCtx.fillRect(canvasUnit * offx, (BorderSize.h + offy) * canvasUnit, canvasUnit * (BorderSize.w + 1), canvasUnit)
    selfViewCtx.fillRect((BorderSize.w + offx) * canvasUnit, canvasUnit * offy, canvasUnit, canvasUnit * (BorderSize.h + 1))
  }

  def drawSelfView(uid: String, data: Data4TotalSync, offsetTime: Long, grid: Grid, championId: String, frameRate: Int): Unit = { //头所在的点是屏幕的正中心
    val snakes = data.snakes

    val lastHeader = snakes.find(_.id == uid) match {
      case Some(s) =>
        val nextDirection = grid.nextDirection(s.id).getOrElse(s.direction)
        val direction = if (s.direction + nextDirection != Point(0, 0)) nextDirection else s.direction
        s.header + direction * offsetTime.toFloat / frameRate

      case None =>
        Point(border.x / 2, border.y / 2)
    }

    val offx = window.x / 2 - lastHeader.x //新的框的x偏移量
    val offy = window.y / 2 - lastHeader.y //新的框的y偏移量

    val newWindowBorder = Point(window.x / scale.toFloat, window.y / scale.toFloat)
    val (minPoint, maxPoint) = (lastHeader - newWindowBorder, lastHeader + newWindowBorder)

    selfViewCtx.clearRect(0, 0, windowBoundary.x, windowBoundary.y)
    selfViewCtx.setFill(ColorsSetting.backgroundColor)
    selfViewCtx.fillRect(0,0,windowBoundary.x,windowBoundary.y)
    val snakeWithOff = data.snakes.map(i => i.copy(header = Point(i.header.x + offx, y = i.header.y + offy)))
    val fieldInWindow = data.fieldDetails.map { f => FieldByColumn(f.uid, f.scanField.filter(p => p.y < maxPoint.y && p.y > minPoint.y)) }

    scale = 1 - grid.getMyFieldCount(uid, maxPoint, minPoint) * 0.00008
    selfViewCtx.save()

//    setScale(scale, windowBoundary.x / 2, windowBoundary.y / 2)
    drawCache(offx , offy)
    selfViewCtx.setGlobalAlpha(0.6)
    data.bodyDetails.foreach { bds =>
      val color = snakes.find(_.id == bds.uid).map(s => Constant.hex2Rgb(s.color)).getOrElse(ColorsSetting.defaultColor)
      selfViewCtx.setFill(color)
      val turnPoints = bds.turn.turnPoint
      (0 until turnPoints.length - 1).foreach { i => //拐点渲染
        val start = turnPoints(i)
        val end = turnPoints(i + 1)
        if (start.x == end.x) { //同x
          if (start.y > end.y) {
            selfViewCtx.fillRect((start.x + offx) * canvasUnit, (end.y + 1 + offy) * canvasUnit, canvasUnit, (start.y - end.y) * canvasUnit)
          } else {
            selfViewCtx.fillRect((start.x + offx) * canvasUnit, (start.y + offy) * canvasUnit, canvasUnit, (end.y - start.y) * canvasUnit)
          }
        } else { // 同y

          if (start.x > end.x) {
            selfViewCtx.fillRect((end.x + 1 + offx) * canvasUnit, (end.y + offy) * canvasUnit, (start.x - end.x) * canvasUnit, canvasUnit)
          } else {
            selfViewCtx.fillRect((start.x + offx) * canvasUnit, (start.y + offy) * canvasUnit, (end.x - start.x) * canvasUnit, canvasUnit)
          }
        }
      }
      if (turnPoints.nonEmpty) {
        selfViewCtx.fillRect((turnPoints.last.x + offx) * canvasUnit, (turnPoints.last.y + offy) * canvasUnit, canvasUnit, canvasUnit)
      }
    }

    selfViewCtx.setGlobalAlpha(1)
    fieldInWindow.foreach { field => //按行渲染
      val color = snakes.find(_.id == field.uid).map(s => Constant.hex2Rgb(s.color)).getOrElse(ColorsSetting.defaultColor)
      selfViewCtx.setFill(color)
      field.scanField.foreach { point =>
        point.x.foreach { x =>
          selfViewCtx.fillRect((x._1 + offx) * canvasUnit, (point.y + offy) * canvasUnit, canvasUnit * (x._2 - x._1 + 1), canvasUnit * 1.05)
        }
      }
    }


    snakeWithOff.foreach { s =>
      selfViewCtx.setFill(Constant.hex2Rgb(s.color))

      val nextDirection = grid.nextDirection(s.id).getOrElse(s.direction)
      val direction = if (s.direction + nextDirection != Point(0, 0)) nextDirection else s.direction
      val off = direction * offsetTime.toFloat / frameRate
      selfViewCtx.fillRect((s.header.x + off.x) * canvasUnit, (s.header.y + off.y) * canvasUnit, canvasUnit, canvasUnit)

      if (s.id == championId)
        selfViewCtx.drawImage(championHeaderImg, (s.header.x + off.x) * canvasUnit, (s.header.y + off.y - 1) * canvasUnit, canvasUnit, canvasUnit)
      val otherHeaderImg = imgMap(s.img)
      val img = if (s.id == uid) myHeaderImg else otherHeaderImg
      selfViewCtx.drawImage(img, (s.header.x + off.x) * canvasUnit, (s.header.y + off.y) * canvasUnit, canvasUnit, canvasUnit)

      selfViewCtx.setFont(Font.font(16))
      selfViewCtx.setFill(Color.rgb(0, 0, 0))
      val t = new Text(s"${s.name}")
      selfViewCtx.fillText(s.name, (s.header.x + off.x) * canvasUnit + canvasUnit / 2 - t.getLayoutBounds.getWidth / 2, (s.header.y + off.y - 1) * canvasUnit - 3)
    }

    selfViewCtx.restore()

//    rankCtx.clearRect(20, textLineHeight * 5, 650, textLineHeight * 2)//* 5, * 2
//    PerformanceTool.renderFps(rankCtx, 20, 5 * textLineHeight)

  }

  def drawSelf(uid: String, data: Data4TotalSync, offsetTime: Long, grid: Grid, championId: String, frameRate: Int): Unit = { //头所在的点是屏幕的正中心
    val snakes = data.snakes

    val lastHeader = snakes.find(_.id == uid) match {
      case Some(s) =>
        val nextDirection = grid.nextDirection(s.id).getOrElse(s.direction)
        val direction = if (s.direction + nextDirection != Point(0, 0)) nextDirection else s.direction
        s.header + direction * offsetTime.toFloat / frameRate

      case None =>
        Point(border.x / 2, border.y / 2)
    }

    val offx = window.x / 2 - lastHeader.x //新的框的x偏移量
    val offy = window.y / 2 - lastHeader.y //新的框的y偏移量

    val newWindowBorder = Point(window.x / scale.toFloat, window.y / scale.toFloat)
    val (minPoint, maxPoint) = (lastHeader - newWindowBorder, lastHeader + newWindowBorder)

    selfCtx.clearRect(0, 0, windowBoundary.x, windowBoundary.y)
    selfCtx.setFill(ColorsSetting.backgroundColor)
    selfCtx.fillRect(0,0,windowBoundary.x,windowBoundary.y)
    val snakeWithOff = data.snakes.map(i => i.copy(header = Point(i.header.x + offx, y = i.header.y + offy)))
    val fieldInWindow = data.fieldDetails.map { f => FieldByColumn(f.uid, f.scanField.filter(p => p.y < maxPoint.y && p.y > minPoint.y)) }

    scale = 1 - grid.getMyFieldCount(uid, maxPoint, minPoint) * 0.00008
    selfCtx.save()

//    setScale(scale, windowBoundary.x / 2, windowBoundary.y / 2)
    selfCtx.setFill(Color.rgb(105,105,105))
//
//  //画边界
    selfCtx.fillRect(canvasUnit * offx, canvasUnit * offy, canvasUnit * BorderSize.w, canvasUnit)
    selfCtx.fillRect(canvasUnit * offx, canvasUnit * offy, canvasUnit, canvasUnit * BorderSize.h)
    selfCtx.fillRect(canvasUnit * offx, (BorderSize.h + offy) * canvasUnit, canvasUnit * (BorderSize.w + 1), canvasUnit)
    selfCtx.fillRect((BorderSize.w + offx) * canvasUnit, canvasUnit * offy, canvasUnit, canvasUnit * (BorderSize.h + 1))
    selfCtx.setGlobalAlpha(0.6)


    val bd = data.bodyDetails.find(_.uid == uid)

    if(bd.isDefined){
      val bds = bd.get
      val color = snakes.find(_.id == uid).map(s => Constant.hex2Rgb(s.color)).getOrElse(ColorsSetting.defaultColor)
      selfCtx.setFill(color)
      val turnPoints = bds.turn.turnPoint
//      val turnPoints = data.bodyDetails.filter(_.uid == uid).head.turn.turnPoint
      (0 until turnPoints.length - 1).foreach { i => //拐点渲染
        val start = turnPoints(i)
        val end = turnPoints(i + 1)
        if (start.x == end.x) { //同x
          if (start.y > end.y) {
            selfCtx.fillRect((start.x + offx) * canvasUnit, (end.y + 1 + offy) * canvasUnit, canvasUnit, (start.y - end.y) * canvasUnit)
          } else {
            selfCtx.fillRect((start.x + offx) * canvasUnit, (start.y + offy) * canvasUnit, canvasUnit, (end.y - start.y) * canvasUnit)
          }
        } else { // 同y

          if (start.x > end.x) {
            selfCtx.fillRect((end.x + 1 + offx) * canvasUnit, (end.y + offy) * canvasUnit, (start.x - end.x) * canvasUnit, canvasUnit)
          } else {
            selfCtx.fillRect((start.x + offx) * canvasUnit, (start.y + offy) * canvasUnit, (end.x - start.x) * canvasUnit, canvasUnit)
          }
        }
      }
      if (turnPoints.nonEmpty) {
        selfCtx.fillRect((turnPoints.last.x + offx) * canvasUnit, (turnPoints.last.y + offy) * canvasUnit, canvasUnit, canvasUnit)
      }
    }

    selfCtx.setGlobalAlpha(1)
    fieldInWindow.filter(_.uid == uid).foreach { field => //按行渲染
      val color = snakes.find(_.id == field.uid).map(s => Constant.hex2Rgb(s.color)).getOrElse(ColorsSetting.defaultColor)
      selfCtx.setFill(color)

      field.scanField.foreach { point =>
        point.x.foreach { x =>
          selfCtx.fillRect((x._1 + offx) * canvasUnit, (point.y + offy) * canvasUnit, canvasUnit * (x._2 - x._1 + 1), canvasUnit * 1.05)
        }
      }
    }

    snakeWithOff.filter(_.id == uid).foreach { s =>
      selfCtx.setFill(Constant.hex2Rgb(s.color))

      val nextDirection = grid.nextDirection(s.id).getOrElse(s.direction)
      val direction = if (s.direction + nextDirection != Point(0, 0)) nextDirection else s.direction
      val off = direction * offsetTime.toFloat / frameRate
      selfCtx.fillRect((s.header.x + off.x) * canvasUnit, (s.header.y + off.y) * canvasUnit, canvasUnit, canvasUnit)

      if (s.id == championId)
        selfCtx.drawImage(championHeaderImg, (s.header.x + off.x) * canvasUnit, (s.header.y + off.y - 1) * canvasUnit, canvasUnit, canvasUnit)
      val otherHeaderImg = imgMap(s.img)
      val img = if (s.id == uid) myHeaderImg else otherHeaderImg
      selfCtx.drawImage(img, (s.header.x + off.x) * canvasUnit, (s.header.y + off.y) * canvasUnit, canvasUnit, canvasUnit)

      selfCtx.setFont(Font.font(16))
      selfCtx.setFill(Color.rgb(0, 0, 0))
      val t = new Text(s"${s.name}")
      selfCtx.fillText(s.name, (s.header.x + off.x) * canvasUnit + canvasUnit / 2 - t.getLayoutBounds.getWidth / 2, (s.header.y + off.y - 1) * canvasUnit - 3)
    }

    selfCtx.restore()


  }

  private val realWindowWidth = rankCanvas.getWidth
  private val realWindowHeight = rankCanvas.getHeight
  private val rankWindowBoundary = Point(realWindowWidth.toFloat, realWindowHeight.toFloat)
  private val goldImg = new Image("gold.png")
  private val silverImg = new Image("silver.png")
  private val bronzeImg = new Image("bronze.png")

  private val fillWidth = 33
  private var lastRankNum = 0 //清屏用
  def drawRank(uid: String, snakes: List[SkDt], currentRank: List[Score]): Unit = {

    val leftBegin = 20
    val rightBegin = 20
//    val maxArea = if(0.4 - currentRank.length * 0.5 > 0.15) 0.4 - currentRank.length * 0.5 else 0.15
    val maxArea = 0.10
    val areaUnit = 250 / maxArea

    drawClearRank()//绘制前清除canvas

    lastRankNum = currentRank.length

    rankCtx.setGlobalAlpha(1.0)
    rankCtx.setTextBaseline(VPos.TOP)


    val currentRankBaseLine = 2
    var index = 0
    rankCtx.setFont(Font.font(14))

    drawTextLine(s" --- Current Rank --- ", rightBegin.toInt, index, currentRankBaseLine)
    if (currentRank.lengthCompare(3) >= 0) {
      rankCtx.drawImage(goldImg, rightBegin - 5 - textLineHeight, textLineHeight * 2, textLineHeight, textLineHeight)
      rankCtx.drawImage(silverImg, rightBegin - 5 - textLineHeight, textLineHeight * 3, textLineHeight, textLineHeight)
      rankCtx.drawImage(bronzeImg, rightBegin - 5 - textLineHeight, textLineHeight * 4, textLineHeight, textLineHeight)
    }
    else if (currentRank.lengthCompare(2) == 0) {
      rankCtx.drawImage(goldImg, rightBegin - 5 - textLineHeight, textLineHeight * 2, textLineHeight, textLineHeight)
      rankCtx.drawImage(silverImg, rightBegin - 5 - textLineHeight, textLineHeight * 3, textLineHeight, textLineHeight)
    }
    else {
      rankCtx.drawImage(goldImg, rightBegin - 5 - textLineHeight, textLineHeight * 2, textLineHeight, textLineHeight)
    }
    currentRank.foreach { score =>
      val color = snakes.find(_.id == score.id).map(s => Constant.hex2Rgb(s.color)).getOrElse(ColorsSetting.defaultColor)
      rankCtx.setGlobalAlpha(0.6)
      rankCtx.setFill(color)
      rankCtx.save()
      rankCtx.fillRect(rightBegin.toInt + 100, (index + currentRankBaseLine) * textLineHeight,
        5 + areaUnit * (score.area.toDouble / canvasSize), textLineHeight)
      rankCtx.restore()

      rankCtx.setGlobalAlpha(1)
      rankCtx.setFill(Color.rgb(0,0,0))
      index += 1
      drawTextLine(s"[$index]: ${score.n.+("   ").take(3)}", rightBegin.toInt, index, currentRankBaseLine)
      drawTextLine(f"${score.area.toDouble / canvasSize * 100}%.2f" + s"%", rightBegin.toInt + 60, index, currentRankBaseLine)
//      drawTextLine(s"kill=${score.k}", rightBegin.toInt + 160, index, currentRankBaseLine)
    }
  }

  def drawClearRank(): Unit = {
    val width = rankCanvas.getWidth
    val height = rankCanvas.getHeight
    rankCtx.clearRect(0, 0, width, height)
  }

  def drawTextLine(str: String, x: Int, lineNum: Int, lineBegin: Int = 0): Unit = {
    rankCtx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight)
  }

  def setScale(scale: Double, x: Double, y: Double): Unit = {
    selfViewCtx.translate(x, y)
    selfViewCtx.scale(scale, scale)
    selfViewCtx.translate(-x, -y)
  }




}
