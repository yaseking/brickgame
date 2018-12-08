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
//import javafx.scene.SnapshotParameters
//import javafx.scene.media.{AudioClip, AudioEqualizer, Media, MediaPlayer}
import org.slf4j.LoggerFactory

/**
  * Created by dry on 2018/10/29.
  **/
class LayeredCanvas(viewCanvas: Canvas,rankCanvas: Canvas,positionCanvas: Canvas,BorderCanvas: Canvas,
  selfViewCanvas: Canvas,selfCanvas: Canvas, img: Int) {//,background: BackgroundCanvas
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
  private var canvasUnit = (positionWindowBoundary.x / window.x)
  private var canvasUnitY = (positionWindowBoundary.y / window.y)
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
    a += 1
    if(a % 200 ==0) println(w,h,positionCanvasUnit)
    positionCtx.save()
    positionCtx.setGlobalAlpha(0.5)
    positionCtx.fillRect(0, 0, w , h )
    positionCtx.restore()

    positionCtx.setFill(Color.rgb(105,105,105))

//    positionCtx.fillRect(positionCanvasUnit, positionCanvasUnit, w, positionCanvasUnit)
//    positionCtx.fillRect(positionCanvasUnit, positionCanvasUnit, positionCanvasUnit, border.y / border.x * w)
//    positionCtx.fillRect(positionCanvasUnit, border.y / border.x * w, positionCanvasUnit + w, positionCanvasUnit)
//    positionCtx.fillRect(w, positionCanvasUnit, positionCanvasUnit, border.y / border.x * w)

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


//    val newWindowBorder = Point(window.x / 2, window.y / 2)
//    val minPoint = myHeader - newWindowBorder
//    positionCtx.fillRect(minPoint.x * positionCanvasUnit, minPoint.y * positionCanvasUnit, positionCanvasUnit * window.x, positionCanvasUnit * window.y)
//    positionCtx.fillRect(minPoint.x * positionCanvasUnit, minPoint.y * positionCanvasUnit, positionCanvasUnit, positionCanvasUnit * window.y)
//    positionCtx.fillRect(minPoint.x * positionCanvasUnit, (BorderSize.h + minPoint.y) * positionCanvasUnit, positionCanvasUnit * (window.x + 1), positionCanvasUnit)
//    positionCtx.fillRect((BorderSize.w + minPoint.x) * positionCanvasUnit, minPoint.y * positionCanvasUnit, positionCanvasUnit, positionCanvasUnit * (window.y + 1))
//    val snakes = data.snakes
//
//    val lastHeader = snakes.find(_.id == uid) match {
//      case Some(s) =>
//        val nextDirection = grid.nextDirection(s.id).getOrElse(s.direction)
//        val direction = if (s.direction + nextDirection != Point(0, 0)) nextDirection else s.direction
//        s.header + direction * offsetTime.toFloat / frameRate
//
//      case None =>
//        Point(border.x / 2, border.y / 2)
//    }
//
//    val offx = window.x / 2 - lastHeader.x //新的框的x偏移量
//    val offy = window.y / 2 - lastHeader.y //新的框的y偏移量
    //画边界
//    positionCtx.fillRect(positionCanvasUnit * offx, positionCanvasUnit * offy, positionCanvasUnit * BorderSize.w, positionCanvasUnit)
//    positionCtx.fillRect(positionCanvasUnit * offx, positionCanvasUnit * offy, positionCanvasUnit, positionCanvasUnit * BorderSize.h)
//    positionCtx.fillRect(positionCanvasUnit * offx, (BorderSize.h + offy) * positionCanvasUnit, positionCanvasUnit * (BorderSize.w + 1), positionCanvasUnit)
//    positionCtx.fillRect((BorderSize.w + offx) * positionCanvasUnit, positionCanvasUnit * offy, positionCanvasUnit, positionCanvasUnit * (BorderSize.h + 1))



//    positionCtx.clearRect(0, 0, windowBoundary.x, windowBoundary.y)
//    positionCtx.setFill(ColorsSetting.backgroundColor)
//    positionCtx.fillRect(0,0,windowBoundary.x,windowBoundary.y)
//
//    val fieldInWindow = data.fieldDetails.map { f => FieldByColumn(f.uid, f.scanField.filter(p => p.y < maxPoint.y && p.y > minPoint.y)) }
//    fieldInWindow.foreach { field => //按行渲染
//      val color = snakes.find(_.id == field.uid).map(s => Constant.hex2Rgb(s.color)).getOrElse(ColorsSetting.defaultColor)
//      viewCtx.setFill(color)
//      field.scanField.foreach { point =>
//        point.x.foreach { x =>
//          viewCtx.fillRect((x._1 + offx) * canvasUnit, (point.y + offy) * canvasUnit, canvasUnit * (x._2 - x._1 + 1), canvasUnit * 1.05)
//        }
//      }
//    }
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

  def drawGameOff(firstCome: Boolean): Unit = {
    viewCtx.save()
    viewCtx.setFill(ColorsSetting.dieInfoBackgroundColor)
    viewCtx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
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

  def drawGameWin(myId: String, winner: String, data: Data4TotalSync,winningData:WinData): Unit = {
    val winnerId = data.snakes.find(_.name == winner).map(_.id).get
    val snakes = data.snakes
    val snakesFields = data.fieldDetails
    scale = 0.33
    val width = windowBoundary.x - BorderSize.w * canvasUnit * scale
    val height = windowBoundary.y - BorderSize.h * canvasUnit * scale
    viewCtx.clearRect(0, 0, windowBoundary.x, windowBoundary.y)
    viewCtx.save()
    viewCtx.scale(scale, scale)
    viewCtx.setFill(ColorsSetting.borderColor)
    viewCtx.fillRect(1.5 * width - canvasUnit, 1.5 * height - canvasUnit, canvasUnit * BorderSize.w, canvasUnit)
    viewCtx.fillRect(1.5 * width - canvasUnit, 1.5 * height - canvasUnit, canvasUnit, canvasUnit * BorderSize.h)
    viewCtx.fillRect(1.5 * width - canvasUnit, BorderSize.h * canvasUnit + 1.5 * height - canvasUnit, canvasUnit * (BorderSize.w + 1), canvasUnit)
    viewCtx.fillRect(BorderSize.w * canvasUnit + 1.5 * width - canvasUnit, 1.5 * height - canvasUnit, canvasUnit, canvasUnit * (BorderSize.h + 1))
    snakesFields.foreach { field =>
      if (field.uid == myId || field.uid == winnerId) {
        val color = snakes.find(_.id == field.uid).map(s => Constant.hex2Rgb(s.color)).get
        viewCtx.setFill(color)
        field.scanField.foreach { point =>
          point.x.foreach { x =>
            viewCtx.fillRect(x._1 * canvasUnit + 1.5 * width - canvasUnit, point.y * canvasUnit + 1.5 * height - canvasUnit, canvasUnit * (x._2 - x._1 + 1), canvasUnit * 1.05)
          }
        }
      }
    }
    viewCtx.restore()
    viewCtx.save()
    viewCtx.scale(1, 1)
    viewCtx.setGlobalAlpha(1)
    viewCtx.setFont(Font.font("Microsoft YaHei", FontPosture.findByName("bold"), 25))
    viewCtx.setFill(ColorsSetting.defaultColor)

    val txt1 = s"The Winner is $winner"
    val txt2 = s"Press space to reStart"
    val length = new Text(txt1).getLayoutBounds.getWidth
    viewCtx.fillText(txt1, (windowBoundary.x - length) / 2 , windowBoundary.y / 5)
    viewCtx.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20))
    //    ctx.setfont = "bold 24px Helvetica"
    viewCtx.setFill(ColorsSetting.fontColor2)
    val txt4 = s"WINNER SCORE:" + f"${winningData.winnerScore / canvasSize * 100}%.2f" + "%"
    val length1 = new Text(txt4).getLayoutBounds.getWidth
    if(winningData.yourScore.isDefined) {
      val txt3 = s"YOUR SCORE:" + f"${winningData.yourScore.get / canvasSize * 100}%.2f" + "%"
      viewCtx.fillText(txt3, (windowBoundary.x - length1) / 2 , windowBoundary.y / 4)
    }
    viewCtx.fillText(txt4, (windowBoundary.x - length1) / 2 , windowBoundary.y / 4 + 40)
    viewCtx.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20)) //FontPosture.findByName("bold")
    viewCtx.fillText(txt2, windowBoundary.x - 300, windowBoundary.y - 100)
    viewCtx.drawImage(crownImg, (windowBoundary.x - length) / 2 + length - 50, windowBoundary.y / 5 - 75, 50, 50)
    viewCtx.restore()
  }
  import javafx.scene.text.Text
  def drawUserDieInfo(killedName: String, killerName: String): Unit = {
    viewCtx.save()
    //    ctx.globalAlpha = 0.6

    viewCtx.restore()
    viewCtx.save()
    viewCtx.setFont(Font.font(30))
    viewCtx.setFill(ColorsSetting.gameNameColor)
    val txt = s"$killedName is killed by $killerName"
    val text = new Text(txt)
    text.setFont(Font.font(30))
    text.setFill(ColorsSetting.gameNameColor)
    val length = text.getLayoutBounds.getWidth
    val offx = length / 2
    //    ctx.drawImage(bloodImg, windowBoundary.x / 2 - offx, 115, 300, 50)
    viewCtx.fillText(s"$killedName is killed by $killerName", windowBoundary.x / 2 - offx, 150)
    viewCtx.restore()
  }

  def drawGameDie(killerOpt: Option[String],  myScore :BaseScore, maxArea: Int): Unit = {
    //    rankCtx.clearRect(0, 0, windowBoundary.x, windowBoundary.y)
    //    val endTime = System.currentTimeMillis()
    //    if (myScore.area > maxArea) maxArea = myScore.area
    viewCtx.setFill(ColorsSetting.dieInfoBackgroundColor)
    viewCtx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    viewCtx.setFill(ColorsSetting.gameNameColor)
    viewCtx.setFont(Font.font(20))
    scale = 1
    viewCtx.scale(1, 1)

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
    viewCtx.fillText(text, windowBoundary.x / 2 - offx - 50 , y) //(500,180)
    viewCtx.save()
    viewCtx.setFill(ColorsSetting.dieInfoFontColor)
    viewCtx.setFont(Font.font(20))
    viewCtx.fillText("YOUR SCORE:", x, y + 70)
    viewCtx.fillText(f"${myScore.area / canvasSize * 100}%.2f" + "%", x + 230, y + 70)
    viewCtx.fillText("BEST SCORE:", x, y + 110)
    viewCtx.fillText(f"$bestScore%.2f" + "%", x + 230, y + 110)
    viewCtx.fillText(s"PLAYERS KILLED:", x, y + 150)
    viewCtx.fillText(s"${myScore.kill}", x + 230, y + 150)
    viewCtx.fillText(s"TIME PLAYED:", x, y + 190)
    viewCtx.fillText(s"$time", x + 230, y + 190)
    viewCtx.restore()
  }

  def drawDieBarrage(killedName: String, killerName: String): Unit = {
    viewCtx.save()
    viewCtx.setGlobalAlpha(0.6)
    //    ctx.drawImage(bloodImg, 670, 115, 300, 50)
    viewCtx.restore()
    viewCtx.save()
    viewCtx.setFill(Color.rgb(255, 88, 9))
    viewCtx.setFont(Font.font(25))
    val length = 30
    val offx = (270 - length) / 2
    viewCtx.fillText(s"$killedName is killed by $killerName", 670 + offx, 150)
    viewCtx.restore()
  }

  def drawSmallMap(myHeader: Point, otherSnakes: List[SkDt]): Unit = {
    val offx = myHeader.x.toDouble / border.x * smallMap.x
    val offy = myHeader.y.toDouble / border.y * smallMap.y
    viewCtx.setFill(ColorsSetting.mapColor)
    val w = windowBoundary.x * 0.99 - littleMap.w * canvasUnit //* 1.100
    val h = windowBoundary.y - littleMap.h * canvasUnitY //* 1.170
    viewCtx.save()
    viewCtx.setGlobalAlpha(0.5)
    viewCtx.fillRect(w.toInt, h.toInt, littleMap.w * canvasUnit + 5, littleMap.h * canvasUnit + 5)
    viewCtx.restore()
    viewCtx.drawImage(myHeaderImg, (w + offx * canvasUnit).toInt, (h + offy * canvasUnit).toInt, 10, 10)
    otherSnakes.foreach { i =>
      val x = i.header.x.toDouble / border.x * smallMap.x
      val y = i.header.y.toDouble / border.y * smallMap.y
      viewCtx.setFill(Constant.hex2Rgb(i.color))
      viewCtx.fillRect(w + x * canvasUnit, h + y * canvasUnit, 10, 10)
    }
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

    setScale(scale, windowBoundary.x / 2, windowBoundary.y / 2)
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

    rankCtx.clearRect(20, textLineHeight * 5, 650, textLineHeight * 2)//* 5, * 2
    PerformanceTool.renderFps(rankCtx, 20, 5 * textLineHeight)

  }

  def setScale(scale: Double, x: Double, y: Double): Unit = {
    viewCtx.translate(x, y)
    viewCtx.scale(scale, scale)
    viewCtx.translate(-x, -y)
  }




}
