package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.paperClient.Constant.ColorsSetting
import com.neo.sk.carnie.paperClient.Protocol.GridDataSync
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

  private val textLineHeight = 14
  private val fillWidth = 33

  private[this] val cacheCanvas = dom.document.getElementById("CacheView").asInstanceOf[Canvas]
  private[this] val cacheCtx = cacheCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private[this] val background = dom.document.getElementById("Background").asInstanceOf[Canvas]
  private[this] val backCtx = background.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private val championHeaderImg = dom.document.getElementById("championHeaderImg").asInstanceOf[Image]
  private val myHeaderImg = dom.document.getElementById("myHeaderImg").asInstanceOf[Image]
  private val otherHeaderImg = dom.document.getElementById("otherHeaderImg").asInstanceOf[Image]

  private var myScore = BaseScore(0, 0, 0l, 0l)
  private var scale = 1.0

  def drawGameOn(): Unit = {
    canvas.width = windowBoundary.x.toInt
    canvas.height = windowBoundary.y.toInt

    background.width = windowBoundary.x.toInt
    background.height = windowBoundary.y.toInt

    backCtx.fillStyle = ColorsSetting.backgroundColor
    backCtx.fillRect(0, 0, background.width, background.height)
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

  def drawGrid(uid: Long, data: GridDataSync, offsetTime: Long, grid: Grid): Unit = { //头所在的点是屏幕的正中心
    val snakes = data.snakes
    val championId = if (data.fieldDetails.nonEmpty) {
      data.fieldDetails.groupBy(_.id).toList.sortBy(_._2.length).reverse.head._1
    } else 0

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

    ctx.clearRect(0, 0, windowBoundary.x, windowBoundary.y)

    val criticalX = (window.x + 1) / scale
    val criticalY = window.y / scale

    val bodies = data.bodyDetails.filter(p => Math.abs(p.x - lastHeader.x) < criticalX && Math.abs(p.y - lastHeader.y) < criticalY).map(i => i.copy(x = i.x + offx, y = i.y + offy))
    val fields = data.fieldDetails.filter(p => Math.abs(p.x - lastHeader.x) < criticalX && Math.abs(p.y - lastHeader.y) < criticalY).map(i => i.copy(x = i.x + offx, y = i.y + offy))
    val borders = data.borderDetails.filter(p => Math.abs(p.x - lastHeader.x) < criticalX && Math.abs(p.y - lastHeader.y) < criticalY).map(i => i.copy(x = i.x + offx, y = i.y + offy))
    val snakeInWindow = data.snakes.filter(s => Math.abs(s.header.x - lastHeader.x) < criticalX && Math.abs(s.header.y - lastHeader.y) < criticalY).map(i => i.copy(header = Point(i.header.x + offx, y = i.header.y + offy)))

    val myField = fields.count(_.id == uid)
    scale = 1 - Math.sqrt(myField) * 0.0048

    ctx.save()
    setScale(scale, windowBoundary.x / 2, windowBoundary.y / 2)

    ctx.globalAlpha = 0.6
    bodies.groupBy(_.id).foreach { case (sid, body) =>
      val color = snakes.find(_.id == sid).map(_.color).getOrElse(ColorsSetting.defaultColor)
      ctx.fillStyle = color
      body.foreach { i => ctx.fillRect(i.x * canvasUnit, i.y * canvasUnit, canvasUnit, canvasUnit) }
    }

    ctx.globalAlpha = 1.0
    fields.groupBy(_.id).foreach { case (sid, field) =>
      val color = snakes.find(_.id == sid).map(_.color).getOrElse(ColorsSetting.defaultColor)
      ctx.fillStyle = color
      field.foreach { i => ctx.fillRect(i.x * canvasUnit, i.y * canvasUnit, canvasUnit * 1.05, canvasUnit * 1.05) }
    }

    snakeInWindow.foreach { s =>
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

    ctx.fillStyle = ColorsSetting.borderColor
    borders.foreach { case Bord(x, y) =>
      ctx.fillRect(x * canvasUnit, y * canvasUnit, canvasUnit * 1.05, canvasUnit * 1.05)
    }

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
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.textAlign = "left"
    ctx.textBaseline = "top"

    val leftBegin = 10
    val rightBegin = windowBoundary.x - 220

    val mySnake = snakes.filter(_.id == uid).head
    val baseLine = 1
    ctx.font = "12px Helvetica"
    drawTextLine(s"YOU: id=[${mySnake.id}]    name=[${mySnake.name.take(32)}]", leftBegin, 0, baseLine)
    drawTextLine(s"your kill = ${mySnake.kill}", leftBegin, 1, baseLine)
    PerformanceTool.renderFps(ctx, leftBegin, (baseLine + 2) * textLineHeight)

    val myRankBaseLine = 3
    currentRank.filter(_.id == uid).foreach { score =>
      myScore = myScore.copy(kill = score.k, area = score.area, endTime = System.currentTimeMillis())
      val color = snakes.find(_.id == uid).map(_.color).getOrElse(ColorsSetting.defaultColor)
      ctx.globalAlpha = 0.6
      ctx.fillStyle = color
      ctx.save()
      ctx.fillRect(leftBegin, (myRankBaseLine - 1) * textLineHeight, fillWidth + windowBoundary.x / 8 * (score.area.toDouble / canvasSize), textLineHeight)
      ctx.restore()

      ctx.globalAlpha = 1
      ctx.fillStyle = ColorsSetting.fontColor
      drawTextLine(f"${score.area.toDouble / canvasSize * 100}%.2f" + s"%", leftBegin, 0, myRankBaseLine)
    }

    val currentRankBaseLine = 1
    var index = 0
    drawTextLine(s" --- Current Rank --- ", rightBegin.toInt, index, currentRankBaseLine)
    currentRank.foreach { score =>
      val color = snakes.find(_.id == score.id).map(_.color).getOrElse(ColorsSetting.defaultColor)
      ctx.globalAlpha = 0.6
      ctx.fillStyle = color
      ctx.save()
      ctx.fillRect(windowBoundary.x - 10 - fillWidth - windowBoundary.x / 8 * (score.area.toDouble / canvasSize), (index + currentRankBaseLine) * textLineHeight,
        fillWidth + windowBoundary.x / 8 * (score.area.toDouble / canvasSize), textLineHeight)
      ctx.restore()

      ctx.globalAlpha = 1
      ctx.fillStyle = ColorsSetting.fontColor
      index += 1
      drawTextLine(s"[$index]: ${score.n.+("   ").take(3)}", rightBegin.toInt, index, currentRankBaseLine)
      drawTextLine(s"area=" + f"${score.area.toDouble / canvasSize * 100}%.2f" + s"%", rightBegin.toInt + 70, index, currentRankBaseLine)
      drawTextLine(s"kill=${score.k}", rightBegin.toInt + 160, index, currentRankBaseLine)
    }
  }

  def drawTextLine(str: String, x: Int, lineNum: Int, lineBegin: Int = 0): Unit = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight)
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
