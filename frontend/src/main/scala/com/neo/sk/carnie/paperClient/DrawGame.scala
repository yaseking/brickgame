package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.paperClient.Constant.ColorsSetting
import com.neo.sk.carnie.paperClient.Protocol.Data4TotalSync
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.{Canvas, Image}

/**
  * Created by dry on 2018/9/3.
  **/
class DrawGame(
              ctx: CanvasRenderingContext2D,
              canvas: Canvas
              ) {

  private val windowBoundary = Point(dom.window.innerWidth.toInt, dom.window.innerHeight.toInt)
  private val border = Point(BorderSize.w, BorderSize.h)
  private val window = Point(Window.w, Window.h)
  private val canvasUnit = (dom.window.innerWidth.toInt / window.x).toInt
  private val smallMap = Point(littleMap.w, littleMap.h)
  private val canvasSize = (border.x - 2) * (border.y - 2)

  private val textLineHeight = 15
  private val fillWidth = 33

  private[this] val rankCanvas = dom.document.getElementById("RankView").asInstanceOf[Canvas] //排行榜离屏canvas
  private[this] val rankCtx = rankCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  private[this] val borderCanvas = dom.document.getElementById("BorderView").asInstanceOf[Canvas] //离屏canvas
  private[this] val borderCtx = borderCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private[this] val background = dom.document.getElementById("Background").asInstanceOf[Canvas]
  private[this] val backCtx = background.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private val championHeaderImg = dom.document.getElementById("championHeaderImg").asInstanceOf[Image]
  private val myHeaderImg = dom.document.getElementById("myHeaderImg").asInstanceOf[Image]
  private val otherHeaderImg = dom.document.getElementById("otherHeaderImg").asInstanceOf[Image]
  private val goldImg = dom.document.getElementById("goldImg").asInstanceOf[Image]
  private val silverImg = dom.document.getElementById("silverImg").asInstanceOf[Image]
  private val bronzeImg = dom.document.getElementById("bronzeImg").asInstanceOf[Image]
  private val killImg = dom.document.getElementById("killImg").asInstanceOf[Image]

  private var myScore = BaseScore(0, 0, 0l, 0l)
  private var scale = 1.0

  def drawGameOn(): Unit = {
    canvas.width = windowBoundary.x.toInt
    canvas.height = windowBoundary.y.toInt

    background.width = windowBoundary.x.toInt
    background.height = windowBoundary.y.toInt

    borderCanvas.width = canvasUnit * Boundary.w
    borderCanvas.height = canvasUnit * Boundary.h

    rankCanvas.width = canvasUnit * Window.w
    rankCanvas.height = canvasUnit * Window.h

    drawCache()

    backCtx.fillStyle = ColorsSetting.backgroundColor
    backCtx.fillRect(0, 0, background.width, background.height)
  }

  def drawCache(): Unit = { //离屏缓存的更新--缓存边界
    borderCtx.fillStyle = ColorsSetting.borderColor

    //画边界
    borderCtx.fillRect(0, 0, canvasUnit * BorderSize.w, canvasUnit)
    borderCtx.fillRect(0, 0, canvasUnit, canvasUnit * BorderSize.h)
    borderCtx.fillRect(0, BorderSize.h * canvasUnit, canvasUnit * (BorderSize.w + 1), canvasUnit)
    borderCtx.fillRect(BorderSize.w * canvasUnit, 0, canvasUnit, canvasUnit * (BorderSize.h + 1))
  }

  def drawGameOff(firstCome: Boolean): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    if (firstCome) {
      ctx.font = "36px Helvetica"
      ctx.fillText("Welcome.", 150, 180)
    } else {
      ctx.font = "36px Helvetica"
      ctx.fillText("Ops, connection lost.", 150, 180)
    }
  }

  def drawGameWin(winner: String): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.font = "36px Helvetica"
    ctx.fillText(s"winner is $winner, Press Space Key To Restart!", 150, 180)
  }

  def drawGameWait(): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.font = "36px Helvetica"
    ctx.fillText("Please wait.", 150, 180)
  }

  def drawGameDie(killerOpt: Option[String], bestScoreArea: Option[Int]): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor

    ctx.font = "16px Helvetica"
    scale = 1
    ctx.scale(1, 1)

    val text = killerOpt match {
      case Some(killer) => s"Ops, You Killed By $killer! Press Space Key To Revenge!"
      case None => "Ops, Press Space Key To Restart!"
    }

    val gameTime = (myScore.endTime - myScore.startTime) / 1000
    val bestScore = bestScoreArea.getOrElse(myScore.area) / canvasSize * 100
    val time = {
      val tempM = gameTime / 60
      val s1 = gameTime % 60
      val s = if (s1 < 0) "00" else if (s1 < 10) "0" + s1 else s1.toString
      val m = if (tempM < 0) "00" else if (tempM < 10) "0" + tempM else tempM.toString
      m + ":" + s
    }
    ctx.fillText(text, 150, 180)
    ctx.save()
    ctx.font = "bold 24px Helvetica"
    ctx.fillStyle = ColorsSetting.gradeColor
    ctx.fillText("YOUR SCORE:", 150, 250)
    ctx.fillText(f"${myScore.kill / canvasSize * 100}%.2f" + "%", 380, 250)
    ctx.fillText("BEST SCORE:", 150, 290)
    ctx.fillText(f"$bestScore%.2f" + "%", 380, 290)
    ctx.fillText(s"PLAYERS KILLED:", 150, 330)
    ctx.fillText(s"${myScore.kill}", 380, 330)
    ctx.fillText(s"TIME PLAYED:", 150, 370)
    ctx.fillText(s"$time", 380, 370)
    ctx.restore()
  }

  def drawGrid(uid: Long, data: Data4TotalSync, offsetTime: Long, grid: Grid, championId: Long, myField: Int): Unit = { //头所在的点是屏幕的正中心
    val snakes = data.snakes

    val lastHeader = snakes.find(_.id == uid) match {
      case Some(s) =>
        val nextDirection = grid.nextDirection(s.id).getOrElse(s.direction)
        val direction = if (s.direction + nextDirection != Point(0, 0)) nextDirection else s.direction
        s.header + direction * offsetTime.toFloat / Protocol.frameRate

      case None =>
        Point(border.x / 2, border.y / 2)
    }

    val offx = window.x / 2 - lastHeader.x //新的框的x偏移量
    val offy = window.y / 2 - lastHeader.y //新的框的y偏移量

    val newWindowBorder = Point(window.x / (2.0 * scale).toFloat, window.y / (2.0 * scale).toFloat)
    val (minPoint, maxPoint) = (lastHeader - newWindowBorder, lastHeader + newWindowBorder)

    ctx.clearRect(0, 0, windowBoundary.x, windowBoundary.y)

    val snakeWithOff = data.snakes.map(i => i.copy(header = Point(i.header.x + offx, y = i.header.y + offy)))

    scale = 1 - Math.sqrt(grid.getMyFieldCount(uid, maxPoint, minPoint)) * 0.0048

    ctx.save()
    setScale(scale, windowBoundary.x / 2, windowBoundary.y / 2)

    ctx.globalAlpha = 0.6
    data.bodyDetails.foreach { bds =>
      val color = snakes.find(_.id == bds.uid).map(_.color).getOrElse(ColorsSetting.defaultColor)
      ctx.fillStyle = color
      val turnPoints = bds.turn.turnPoint
      (0 until turnPoints.length - 1).foreach { i => //拐点渲染
        val start = turnPoints(i)
        val end = turnPoints(i + 1)
        if (start.x == end.x) { //同x
          if (start.y > end.y) {
            ctx.fillRect((start.x + offx) * canvasUnit, (end.y + 1 + offy) * canvasUnit, canvasUnit, (start.y - end.y) * canvasUnit)
          } else {
            ctx.fillRect((start.x + offx) * canvasUnit, (start.y + offy) * canvasUnit, canvasUnit, (end.y - start.y) * canvasUnit)
          }
        } else { // 同y
          if (start.x > end.x) {
            ctx.fillRect((end.x + 1 + offx) * canvasUnit, (end.y + offy) * canvasUnit, (start.x - end.x) * canvasUnit, canvasUnit)
          } else {
            ctx.fillRect((start.x + offx) * canvasUnit, (start.y + offy) * canvasUnit, (end.x - start.x) * canvasUnit, canvasUnit)
          }
        }
      }
      if (turnPoints.nonEmpty) ctx.fillRect((turnPoints.last.x + offx) * canvasUnit, (turnPoints.last.y + offy) * canvasUnit, canvasUnit, canvasUnit)
    }


    ctx.globalAlpha = 1.0
    data.fieldDetails.foreach { field => //按行渲染
      val color = snakes.find(_.id == field.uid).map(_.color).getOrElse(ColorsSetting.defaultColor)
      ctx.fillStyle = color
      field.scanField.foreach { point =>
        point.x.foreach { x =>
          ctx.fillRect((x._1 + offx) * canvasUnit, (point.y + offy) * canvasUnit, canvasUnit * (x._2 - x._1 + 1), canvasUnit * 1.05)
        }
      }
    }

    snakeWithOff.foreach { s =>
      ctx.fillStyle = s.color

      val nextDirection = grid.nextDirection(s.id).getOrElse(s.direction)
      val direction = if (s.direction + nextDirection != Point(0, 0)) nextDirection else s.direction
      val off = direction * offsetTime.toFloat / Protocol.frameRate
      ctx.fillRect((s.header.x + off.x) * canvasUnit, (s.header.y + off.y) * canvasUnit, canvasUnit, canvasUnit)

      val img = if (s.id == championId) championHeaderImg else {
        if (s.id == uid) myHeaderImg else otherHeaderImg
      }
      ctx.drawImage(img, (s.header.x + off.x) * canvasUnit, (s.header.y + off.y) * canvasUnit, canvasUnit, canvasUnit)

    }

    //边界离屏
    ctx.drawImage(borderCanvas, offx * canvasUnit, offy * canvasUnit)
    ctx.restore()

    //排行榜边界离屏
    rankCtx.clearRect(20, textLineHeight * 5, 600, textLineHeight * 2)
    PerformanceTool.renderFps(rankCtx, 20, 5 * textLineHeight)
    ctx.drawImage(rankCanvas, 0, 0)
    ctx.restore()

  }

  def drawSmallMap(myHeader: Point, otherSnakes: List[SkDt]): Unit = {
    val offx = myHeader.x.toDouble / border.x * smallMap.x
    val offy = myHeader.y.toDouble / border.y * smallMap.y
    ctx.fillStyle = ColorsSetting.mapColor
    val w = canvas.width - littleMap.w * canvasUnit * 1.042
    val h = canvas.height - littleMap.h * canvasUnit * 1.030
    ctx.save()
    ctx.globalAlpha = 0.5
    ctx.fillRect(w.toInt, h.toInt, littleMap.w * canvasUnit + 5, littleMap.h * canvasUnit + 5)
    ctx.restore()
    ctx.drawImage(myHeaderImg, (w + offx * canvasUnit).toInt, (h + offy * canvasUnit).toInt, 10, 10)
    otherSnakes.foreach { i =>
      val x = i.header.x.toDouble / border.x * smallMap.x
      val y = i.header.y.toDouble / border.y * smallMap.y
      ctx.fillStyle = i.color
      ctx.fillRect(w + x * canvasUnit, h + y * canvasUnit, 10, 10)
    }
  }


  def drawRank(uid: Long, snakes: List[SkDt], currentRank: List[Score]): Unit = {
    val leftBegin = 20
    val rightBegin = windowBoundary.x - 230

    rankCtx.clearRect(leftBegin, textLineHeight, fillWidth + windowBoundary.x / 6, textLineHeight * 4) //绘制前清除canvas
    rankCtx.clearRect(rightBegin, textLineHeight, 210, textLineHeight * (currentRank.length + 1) + 3)

    rankCtx.globalAlpha = 1
    rankCtx.textAlign = "left"
    rankCtx.textBaseline = "top"

    val mySnake = snakes.filter(_.id == uid).head
    val baseLine = 2
    rankCtx.font = "22px Helvetica"
    rankCtx.fillStyle = ColorsSetting.fontColor
//    drawTextLine(s"NAME: ${mySnake.name.take(32)}", leftBegin, 0, baseLine)
    drawTextLine(s"KILL: ", leftBegin, 0, baseLine)
    rankCtx.drawImage(killImg, leftBegin + 55, textLineHeight, textLineHeight * 1.4, textLineHeight * 1.4)
    drawTextLine(s" x ${mySnake.kill}", leftBegin + 55 + (textLineHeight * 1.4).toInt, 0, baseLine)
    rankCtx.fillStyle = ColorsSetting.fontColor
//    PerformanceTool.renderFps(rankCtx, leftBegin, (baseLine + 3) * textLineHeight)

    val myRankBaseLine = 4
    currentRank.filter(_.id == uid).foreach { score =>
      myScore = myScore.copy(kill = score.k, area = score.area, endTime = System.currentTimeMillis())
      val color = snakes.find(_.id == uid).map(_.color).getOrElse(ColorsSetting.defaultColor)
      rankCtx.globalAlpha = 0.6
      rankCtx.fillStyle = color
      rankCtx.save()
      rankCtx.fillRect(leftBegin, (myRankBaseLine - 1) * textLineHeight, fillWidth + windowBoundary.x / 8 * (score.area.toDouble / canvasSize), textLineHeight + 10)
      rankCtx.restore()

      rankCtx.globalAlpha = 1
      rankCtx.font = "22px Helvetica"
      rankCtx.fillStyle = ColorsSetting.fontColor
      drawTextLine(f"${score.area.toDouble / canvasSize * 100}%.2f" + s"%", leftBegin, 0, myRankBaseLine)
    }

    val currentRankBaseLine = 2
    var index = 0
    rankCtx.font = "14px Helvetica"
    drawTextLine(s" --- Current Rank --- ", rightBegin.toInt, index, currentRankBaseLine)
    if(currentRank.lengthCompare(3) >= 0){
      ctx.drawImage(goldImg, rightBegin - 5 - textLineHeight, textLineHeight * 2, textLineHeight, textLineHeight)
      ctx.drawImage(silverImg, rightBegin - 5 - textLineHeight, textLineHeight * 3, textLineHeight, textLineHeight)
      ctx.drawImage(bronzeImg, rightBegin - 5 - textLineHeight, textLineHeight * 4, textLineHeight, textLineHeight)
    }
    else if (currentRank.lengthCompare(2) == 0) {
      ctx.drawImage(goldImg, rightBegin - 5 - textLineHeight, textLineHeight * 2, textLineHeight, textLineHeight)
      ctx.drawImage(silverImg, rightBegin - 5 - textLineHeight, textLineHeight * 3, textLineHeight, textLineHeight)
    }
    else {
      ctx.drawImage(goldImg, rightBegin - 5 - textLineHeight, textLineHeight * 2, textLineHeight, textLineHeight)
    }
    currentRank.foreach { score =>
      val color = snakes.find(_.id == score.id).map(_.color).getOrElse(ColorsSetting.defaultColor)
      rankCtx.globalAlpha = 0.6
      rankCtx.fillStyle = color
      rankCtx.save()
      rankCtx.fillRect(windowBoundary.x - 20 - fillWidth - windowBoundary.x / 8 * (score.area.toDouble / canvasSize), (index + currentRankBaseLine) * textLineHeight,
        fillWidth + windowBoundary.x / 8 * (score.area.toDouble / canvasSize), textLineHeight)
      rankCtx.restore()

      rankCtx.globalAlpha = 1
      rankCtx.fillStyle = ColorsSetting.fontColor
      index += 1
      drawTextLine(s"[$index]: ${score.n.+("   ").take(3)}", rightBegin.toInt, index, currentRankBaseLine)
      drawTextLine(s"area=" + f"${score.area.toDouble / canvasSize * 100}%.2f" + s"%", rightBegin.toInt + 70, index, currentRankBaseLine)
      drawTextLine(s"kill=${score.k}", rightBegin.toInt + 160, index, currentRankBaseLine)
    }
  }

  def drawTextLine(str: String, x: Int, lineNum: Int, lineBegin: Int = 0): Unit = {
    rankCtx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight)
  }

  def setScale(scale: Double, x: Double, y: Double): Unit = {
    ctx.translate(x, y)
    ctx.scale(scale, scale)
    ctx.translate(-x, -y)
  }

  def cleanMyScore: Unit = {
    myScore = BaseScore(0, 0, System.currentTimeMillis(), 0l)
  }


}
