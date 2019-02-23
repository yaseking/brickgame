package org.seekloud.brickgame.paperClient

import org.seekloud.brickgame.common.Constant._
import org.seekloud.brickgame.paperClient.Protocol._
import org.seekloud.brickgame.util.TimeTool
import javafx.scene.paint.Color
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.{Button, Canvas, Image}

/**
  * Created by dry on 2018/9/3.
  **/
class DrawGame(
  ctx: CanvasRenderingContext2D,
  canvas: Canvas
) {

  //todo 所有的大小均需适配浏览器

  private var windowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
  val mainLength = 20
  val sideLength = 10

  val offX = 0
  val offX2 = windowBoundary.x/2-20

  private val bodyAttribute = dom.document.getElementById("body").asInstanceOf[org.scalajs.dom.html.Body]
  //  private val backBtn = dom.document.getElementById("backBtn").asInstanceOf[Button]
//  private var scale = 1.0

  def resetScreen(): Unit = {
    windowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
    canvas.width = windowBoundary.x.toInt
    canvas.height = windowBoundary.y.toInt
  }

  def drawGameOn(): Unit = {
    bodyAttribute.style_=("overflow:Scroll;overflow-y:hidden;overflow-x:hidden;")

    canvas.width = windowBoundary.x.toInt
    canvas.height = windowBoundary.y.toInt

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

  def drawGameDuration(time: Int): Unit = {
    val off = offX + 500
    ctx.save()
    ctx.fillStyle = ColorsSetting.fontColor2
    ctx.font = "24px Helvetica"
    ctx.fillText("剩余时间：", off-10, 40)
    ctx.fillText(s"${60-time}s", off+25, 70)
    ctx.restore()
  }

  def drawMyExpression(expression: Int): Unit = {
    val img = imgMap(expression)
    ctx.drawImage(img, 100, 700, 30, 30)
  }

  def drawOtherExpression(expression: Int): Unit = {}


  def drawGameOff(firstCome: Boolean): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor2
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

  def drawServerShutDown(): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor2
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.font = "36px Helvetica"
    ctx.fillText("Sorry, Some errors happened.", 150, 180)
  }

  def drawGameWait(): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor2
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.font = "36px Helvetica"
//    ctx.fillText("Please wait.", 150, 180)
    ctx.fillText("匹配中...", 150, 180)
  }


  def drawGameWin(name: String): Unit = {
    ctx.save()
    ctx.fillStyle = ColorsSetting.fontColor3

    ctx.font = "24px Helvetica"
//    ctx.scale(1, 1)

    val text = s"Winner is $name, Press Space Key To Restart!"

    val length = ctx.measureText(text).width
    val offx = length / 2
    val x = (dom.window.innerWidth / 2).toInt - 145
    val y = (dom.window.innerHeight / 2).toInt - 100
    //    val y = (dom.window.innerHeight / 2).toInt - 180

    ctx.fillText(text, dom.window.innerWidth / 2 - offx, y) //(500,180)
    ctx.restore()
  }

  def draw(uid: Int, data: Map[Int, PlayerDt], offTime: Long): Unit = {
    //drawBorder..
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    data.foreach {d =>
      if(d._1 == uid) {
        val totalField = d._2.field
        totalField.foreach {f =>
          f._2 match {
            case TopBorder =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.backgroundColor2
              ctx.fillRect(offX + x * mainLength, y * mainLength, mainLength+1, mainLength)

            case SideBorder =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.backgroundColor2
              ctx.fillRect(offX + x * mainLength, y * mainLength, mainLength, mainLength+1)

            case Brick =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.greenColor
              ctx.fillRect(offX + x * mainLength, y * mainLength, mainLength - 1, mainLength - 1)

            case RedBrick =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.redColor
              ctx.fillRect(offX + x * mainLength, y * mainLength, mainLength - 1, mainLength - 1)

            case HotBall =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.borderColor
              ctx.fillRect(offX + x * mainLength, y * mainLength, mainLength - 1, mainLength - 1)

            case Plank =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.fontColor3
              ctx.fillRect(offX + x * mainLength, y * mainLength, mainLength+1, mainLength)

            case _ =>
              //空白领地不做处理
          }

        }

        val ball = d._2.ballLocation
        val x = ball.x
        val y = ball.y
        val off = (offTime/100).toFloat
        if(d._2.state==1){
          ctx.fillStyle = ColorsSetting.fontColor3
        } else {
          ctx.fillStyle = ColorsSetting.darkYellowColor
        }
        ctx.fillRect(offX + x * mainLength + off * d._2.velocityX * mainLength, y * mainLength + off * d._2.velocityY * mainLength, mainLength, mainLength) //球的形状改成圆形,offTime逐步绘制
//        ctx.arc(offX + x * mainLength, y * mainLength, 0.5*mainLength, 0, 2*Math.PI)
//        ctx.fill()
        //绘制分数
        val score = d._2.score
        val offX4Score = offX + 190
        val offY4Score = 660
        ctx.fillStyle = ColorsSetting.fontColor2
        ctx.font = "20px Helvetica"
        ctx.fillText(s"Score:${score}", offX4Score, offY4Score)
      } else {
        //绘制对手的部分
        val totalField = d._2.field
        totalField.foreach {f =>
          f._2 match {
            case TopBorder =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.backgroundColor2
              ctx.fillRect(offX2 + x * sideLength, y * sideLength, sideLength, sideLength)

            case SideBorder =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.backgroundColor2
              ctx.fillRect(offX2 + x * sideLength, y * sideLength, sideLength, sideLength)

            case Brick =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.greenColor
              ctx.fillRect(offX2 + x * sideLength, y * sideLength, sideLength - 1, sideLength - 1)

            case Plank =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.fontColor3
              ctx.fillRect(offX2 + x * sideLength, y * sideLength, sideLength, sideLength)

            case _ =>
            //空白领地不做处理
          }

        }

        val ball = d._2.ballLocation
        val x = ball.x
        val y = ball.y
        ctx.fillStyle = ColorsSetting.darkYellowColor
        ctx.fillRect(offX2 + x * sideLength, y * sideLength, sideLength, sideLength)
        val score = d._2.score
        val offX4Score = offX2 + 85
        val offY4Score = 325
        ctx.fillStyle = ColorsSetting.fontColor2
        ctx.font = "20px Helvetica"
        ctx.fillText(s"Score:${score}", offX4Score, offY4Score)
      }
    }
  }

}
