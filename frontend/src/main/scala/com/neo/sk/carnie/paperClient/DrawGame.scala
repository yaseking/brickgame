package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.common.Constant.ColorsSetting
import com.neo.sk.carnie.paperClient.Protocol.{Data4TotalSync, FieldByColumn}
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

  private var windowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
  private val border = Point(BorderSize.w, BorderSize.h)
  private val window = Point(Window.w, Window.h)
  private var canvasUnit = (dom.window.innerWidth.toInt / window.x).toInt
  private val smallMap = Point(littleMap.w, littleMap.h)
  private val canvasSize = (border.x - 2) * (border.y - 2)

  private val textLineHeight = 15
  private val fillWidth = 33

  private[this] val rankCanvas = dom.document.getElementById("RankView").asInstanceOf[Canvas] //排行榜离屏canvas
  private[this] val rankCtx = rankCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  private[this] val borderCanvas = dom.document.getElementById("BorderView").asInstanceOf[Canvas] //离屏canvas
  private[this] val borderCtx = borderCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private val bodyAttribute = dom.document.getElementById("body").asInstanceOf[org.scalajs.dom.html.Body]
  private val championHeaderImg = dom.document.getElementById("championHeaderImg").asInstanceOf[Image]
  private val myHeaderImg = dom.document.getElementById("myHeaderImg").asInstanceOf[Image]
  private val otherHeaderImg = dom.document.getElementById("otherHeaderImg").asInstanceOf[Image]
  private val goldImg = dom.document.getElementById("goldImg").asInstanceOf[Image]
  private val silverImg = dom.document.getElementById("silverImg").asInstanceOf[Image]
  private val bronzeImg = dom.document.getElementById("bronzeImg").asInstanceOf[Image]
  private val killImg = dom.document.getElementById("killImg").asInstanceOf[Image]
  private val bloodImg = dom.document.getElementById("bloodImg").asInstanceOf[Image]
  private val crownImg = dom.document.getElementById("crownImg").asInstanceOf[Image]

  def reSetScreen(): Unit = {
    windowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
    canvasUnit = (dom.window.innerWidth.toInt / window.x).toInt
    canvas.width = windowBoundary.x.toInt
    canvas.height = windowBoundary.y.toInt
    borderCanvas.width = canvasUnit * Boundary.w
    borderCanvas.height = canvasUnit * Boundary.h
    rankCanvas.width = dom.window.innerWidth.toInt
    rankCanvas.height = dom.window.innerHeight.toInt
    drawCache()
  }

  def drawGameOn(): Unit = {
    bodyAttribute.style_=("background-color:#F5F5F5;overflow:Scroll;overflow-y:hidden;overflow-x:hidden;")

    canvas.width = windowBoundary.x.toInt
    canvas.height = windowBoundary.y.toInt

    borderCanvas.width = canvasUnit * Boundary.w
    borderCanvas.height = canvasUnit * Boundary.h

    rankCanvas.width = dom.window.innerWidth.toInt
    rankCanvas.height = dom.window.innerHeight.toInt

    drawCache()

  }

  def drawVerifyErr(): Unit = {
    canvas.width = windowBoundary.x.toInt
    canvas.height = windowBoundary.y.toInt
    ctx.fillStyle = ColorsSetting.backgroundColor2
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.font = "36px Helvetica"
    ctx.fillText(s"It failed to verify the player's info!", 150, 180)
  }

  def drawCache(): Unit = { //离屏缓存的更新--缓存边界
    borderCtx.fillStyle = ColorsSetting.borderColor

    //画边界
    borderCtx.fillRect(0, 0, canvasUnit * BorderSize.w, canvasUnit)
    borderCtx.fillRect(0, 0, canvasUnit, canvasUnit * BorderSize.h)
    borderCtx.fillRect(0, BorderSize.h * canvasUnit, canvasUnit * (BorderSize.w + 1), canvasUnit)
    borderCtx.fillRect(BorderSize.w * canvasUnit, 0, canvasUnit, canvasUnit * (BorderSize.h + 1))
  }

  def drawGameOff(firstCome: Boolean, replayFinish: Option[Boolean], loading: Boolean, readFileError: Boolean): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor2
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    if (readFileError) {
      rankCtx.clearRect(0, 0, dom.window.innerWidth.toInt, dom.window.innerHeight.toInt)
      ctx.font = "36px Helvetica"
      ctx.fillText("文件不存在或文件已损坏...", 150, 180)
    } else if (replayFinish.nonEmpty && replayFinish.get) {
      rankCtx.clearRect(0, 0, dom.window.innerWidth.toInt, dom.window.innerHeight.toInt)
      ctx.font = "36px Helvetica"
      ctx.fillText("Replay ends.", 150, 180)
    } else if (loading) {
      rankCtx.clearRect(0, 0, dom.window.innerWidth.toInt, dom.window.innerHeight.toInt)
      ctx.font = "36px Helvetica"
      ctx.fillText("Loading......", 150, 180)
    } else {
      if (firstCome) {
        ctx.font = "36px Helvetica"
        ctx.fillText("Welcome.", 150, 180)
      } else {
        rankCtx.clearRect(0, 0, dom.window.innerWidth.toInt, dom.window.innerHeight.toInt)
        ctx.font = "36px Helvetica"
        ctx.fillText("Ops, connection lost.", 150, 180)
      }
    }
  }

  def drawGameWait(): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor2
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.font = "36px Helvetica"
    ctx.fillText("Please wait.", 150, 180)
  }

  def drawGameDie(killerOpt: Option[String], myScore: BaseScore, maxArea: Int): Unit = {
    rankCtx.clearRect(0, 0, dom.window.innerWidth.toInt, dom.window.innerHeight.toInt)
    ctx.fillStyle = ColorsSetting.backgroundColor2
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.gameNameColor

    ctx.font = "24px Helvetica"
    ctx.scale(1, 1)

    val text = killerOpt match {
      case Some(killer) => s"Ops, You Killed By $killer! Press Space Key To Revenge!"
      case None => "Ops, Press Space Key To Restart!"
    }

    val length = ctx.measureText(text).width
    val offx = length / 2
    val x = (dom.window.innerWidth / 2).toInt - 145
    val y = (dom.window.innerHeight / 2).toInt - 180

    val gameTime = (myScore.endTime - myScore.startTime) / 1000
    val bestScore = maxArea / canvasSize * 100
    val time = {
      val tempM = gameTime / 60
      val s1 = gameTime % 60
      val s = if (s1 < 0) "00" else if (s1 < 10) "0" + s1 else s1.toString
      val m = if (tempM < 0) "00" else if (tempM < 10) "0" + tempM else tempM.toString
      m + ":" + s
    }
    ctx.fillText(text, dom.window.innerWidth / 2 - offx , y) //(500,180)
    ctx.save()
    ctx.font = "bold 24px Helvetica"
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.fillText("YOUR SCORE:", x, y + 70)
    ctx.fillText(f"${myScore.area / canvasSize * 100}%.2f" + "%", x + 230, y + 70)
    ctx.fillText("BEST SCORE:", x, y + 110)
    ctx.fillText(f"$bestScore%.2f" + "%", x + 230, y + 110)
    ctx.fillText(s"PLAYERS KILLED:", x, y + 150)
    ctx.fillText(s"${myScore.kill}", x + 230, y + 150)
    ctx.fillText(s"TIME PLAYED:", x, y + 190)
    ctx.fillText(s"$time", x + 230, y + 190)
    ctx.restore()
  }

  def drawUserDieInfo(killedName: String, killerName: String) = {
    ctx.save()
    ctx.globalAlpha = 0.6
    ctx.restore()
    ctx.save()
    ctx.font = "bold 30px Microsoft YaHei"
    ctx.fillStyle = "#FF5809"
    val txt = s"$killedName is killed by $killerName"
    val length = ctx.measureText(txt).width
    val offx = length / 2
    ctx.drawImage(bloodImg, dom.window.innerWidth / 2 - offx, 115, 300, 50)
    ctx.fillText(s"$killedName is killed by $killerName",   dom.window.innerWidth / 2 - offx, (dom.window.innerHeight / 2).toInt - 180)
    ctx.restore()
  }

  def drawGameWin(myId: String, winner: String, data: Data4TotalSync) = {
    rankCtx.clearRect(0, 0, dom.window.innerWidth.toInt, dom.window.innerHeight.toInt)
    val winnerId = data.snakes.find(_.name == winner).map(_.id).get
    val snakes = data.snakes
    val snakesFields = data.fieldDetails
    val width = dom.window.innerWidth.toFloat - BorderSize.w * canvasUnit * 0.33
    val height = dom.window.innerHeight.toFloat - BorderSize.h * canvasUnit * 0.33
    ctx.save()
    ctx.scale(0.33, 0.33)
    ctx.fillStyle = ColorsSetting.borderColor
    ctx.fillRect(1.5 * width - canvasUnit, 1.5 * height - canvasUnit, canvasUnit * BorderSize.w, canvasUnit)
    ctx.fillRect(1.5 * width - canvasUnit, 1.5 * height - canvasUnit, canvasUnit, canvasUnit * BorderSize.h)
    ctx.fillRect(1.5 * width - canvasUnit, BorderSize.h * canvasUnit + 1.5 * height - canvasUnit, canvasUnit * (BorderSize.w + 1), canvasUnit)
    ctx.fillRect(BorderSize.w * canvasUnit + 1.5 * width - canvasUnit, 1.5 * height - canvasUnit, canvasUnit, canvasUnit * (BorderSize.h + 1))
    snakesFields.foreach { field =>
      if (field.uid == myId || field.uid == winnerId) {
        val color = snakes.find(_.id == field.uid).map(_.color).get
        ctx.fillStyle = color
        field.scanField.foreach { point =>
          point.x.foreach { x =>
            ctx.fillRect(x._1 * canvasUnit + 1.5 * width - canvasUnit, point.y * canvasUnit + 1.5 * height - canvasUnit, canvasUnit * (x._2 - x._1 + 1), canvasUnit * 1.05)
          }
        }
      }
    }
    ctx.restore()
    ctx.save()
    ctx.scale(1, 1)
    ctx.globalAlpha = 1
    ctx.font = "bold 30px Microsoft YaHei"
    ctx.fillStyle = "#000000"
    val txt1 = s"The Winner is $winner"
    val txt2 = s"Press space to reStart"
    println(ctx.measureText(txt2).width.toString)
    val length = ctx.measureText(txt1).width
    ctx.fillText(txt1, 700, 150)
    ctx.font = "bold 20px Microsoft YaHei"
    ctx.fillText(txt2, dom.window.innerWidth.toFloat - 300, dom.window.innerHeight.toFloat - 100)
    ctx.drawImage(crownImg, 705 + length, 110, 50, 50)
    ctx.restore()
  }

  def drawGrid(uid: String, data: Data4TotalSync, offsetTime: Long, grid: Grid, championId: String, scale: Double): Double = { //头所在的点是屏幕的正中心
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

    val newWindowBorder = Point(window.x / scale.toFloat, window.y / scale.toFloat)
    val (minPoint, maxPoint) = (lastHeader - newWindowBorder, lastHeader + newWindowBorder)

    ctx.clearRect(0, 0, windowBoundary.x, windowBoundary.y)

    val snakeWithOff = data.snakes.map(i => i.copy(header = Point(i.header.x + offx, y = i.header.y + offy)))
    val fieldInWindow = data.fieldDetails.map { f => FieldByColumn(f.uid, f.scanField.filter(p => p.y < maxPoint.y && p.y > minPoint.y)) }

    val newScale = 1 - grid.getMyFieldCount(uid, maxPoint, minPoint) * 0.00008
    ctx.save()
    setScale(newScale, windowBoundary.x / 2, windowBoundary.y / 2)

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
    fieldInWindow.foreach { field => //按行渲染
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

      ctx.font = "16px Helvetica"
      ctx.fillStyle = "#000000"
      ctx.fillText(s.name, (s.header.x + off.x) * canvasUnit + canvasUnit / 2 - ctx.measureText(s.name).width / 2, (s.header.y + off.y) * canvasUnit - 10)
    }

    //边界离屏
    ctx.drawImage(borderCanvas, offx * canvasUnit, offy * canvasUnit)
    ctx.restore()

    //    //排行榜边界离屏
//    rankCtx.clearRect(20, textLineHeight * 4, rankCanvas.width/2, textLineHeight * 3)//* 5, * 2
    PerformanceTool.renderFps(rankCtx, 20, 5 * textLineHeight)

    newScale
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

  def drawRank(uid: String, snakes: List[SkDt], currentRank: List[Score]): Unit = {

    val leftBegin = 20
    val rightBegin = windowBoundary.x - 230

    rankCtx.clearRect(0, 0, rankCanvas.width, rankCanvas.height) //绘制前清除canvas

    rankCtx.globalAlpha = 1
    rankCtx.textAlign = "left"
    rankCtx.textBaseline = "top"

    val mySnake = snakes.filter(_.id == uid).head
    val baseLine = 2
    rankCtx.font = "22px Helvetica"
    rankCtx.fillStyle = ColorsSetting.fontColor2
    drawTextLine(s"KILL: ", leftBegin, 0, baseLine)
    rankCtx.drawImage(killImg, leftBegin + 55, textLineHeight, textLineHeight * 1.4, textLineHeight * 1.4)
    drawTextLine(s" x ${mySnake.kill}", leftBegin + 55 + (textLineHeight * 1.4).toInt, 0, baseLine)


    val myRankBaseLine = 4
    currentRank.filter(_.id == uid).foreach { score =>
      val color = snakes.find(_.id == uid).map(_.color).getOrElse(ColorsSetting.defaultColor)
      rankCtx.globalAlpha = 0.6
      rankCtx.fillStyle = color
      rankCtx.save()
      rankCtx.fillRect(leftBegin, (myRankBaseLine - 1) * textLineHeight, fillWidth + windowBoundary.x / 8 * (score.area.toDouble / canvasSize), textLineHeight + 10)
      rankCtx.restore()

      rankCtx.globalAlpha = 1
      rankCtx.font = "22px Helvetica"
      rankCtx.fillStyle = ColorsSetting.fontColor2
      drawTextLine(f"${score.area.toDouble / canvasSize * 100}%.2f" + s"%", leftBegin, 0, myRankBaseLine)
    }
    val currentRankBaseLine = 2
    var index = 0
    rankCtx.font = "14px Helvetica"
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
      val color = snakes.find(_.id == score.id).map(_.color).getOrElse(ColorsSetting.defaultColor)
      rankCtx.globalAlpha = 0.6
      rankCtx.fillStyle = color
      rankCtx.save()
      rankCtx.fillRect(windowBoundary.x - 20 - fillWidth - windowBoundary.x / 8 * (score.area.toDouble / canvasSize), (index + currentRankBaseLine) * textLineHeight,
        fillWidth + windowBoundary.x / 8 * (score.area.toDouble / canvasSize), textLineHeight)
      rankCtx.restore()

      rankCtx.globalAlpha = 1
      rankCtx.fillStyle = ColorsSetting.fontColor2
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

//  def cleanMyScore: Unit = {
//    myScore = BaseScore(0, 0, System.currentTimeMillis(), 0l)
//  }


}
