package org.seekloud.brickgame.paperClient

import org.seekloud.brickgame.common.Constant._
import org.seekloud.brickgame.paperClient.Protocol._
import org.seekloud.brickgame.util.TimeTool
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

  private var windowBoundary = Point(dom.window.innerWidth.toFloat/6*5, dom.window.innerHeight.toFloat)

  println(s"ScreenWidth: ${windowBoundary.x.toInt}")

  val mainLength = ((windowBoundary.x/2)/30).toInt
  val sideLength = mainLength

  val middleLine = windowBoundary.x/2-20
  val offX2 = windowBoundary.x/2+50

  val ballBlue = dom.document.getElementById("ballBlueImg").asInstanceOf[Image]
  val ballGrey = dom.document.getElementById("ballGreyImg").asInstanceOf[Image]
  val redBrick = dom.document.getElementById("redBrickImg").asInstanceOf[Image]
  val greenBrick = dom.document.getElementById("greenBrickImg").asInstanceOf[Image]
  val yellowBrick = dom.document.getElementById("yellowBrickImg").asInstanceOf[Image]
  val greyBrick = dom.document.getElementById("greyBrickImg").asInstanceOf[Image]
  val bubble1 = dom.document.getElementById("bubble1Img").asInstanceOf[Image]
  val bubble2 = dom.document.getElementById("bubble2Img").asInstanceOf[Image]

  private val bodyAttribute = dom.document.getElementById("body").asInstanceOf[org.scalajs.dom.html.Body]

  def resetScreen(): Unit = {
    windowBoundary = Point(dom.window.innerWidth.toFloat/6*5, dom.window.innerHeight.toFloat)
    canvas.width = windowBoundary.x.toInt
    canvas.height = windowBoundary.y.toInt
  }

  def drawGameOn(): Unit = {
    bodyAttribute.style_=("overflow:Scroll;overflow-y:hidden;overflow-x:hidden;")

    canvas.width = windowBoundary.x.toInt
    canvas.height = windowBoundary.y.toInt

  }

  def drawGameDuration(time: Int): Unit = {
    ctx.save()
    ctx.fillStyle = ColorsSetting.blackColor
    ctx.font = "24px Helvetica"
    val len1 = ctx.measureText("剩余时间：").width/2
    val len2 = ctx.measureText("10").width/2
    ctx.fillText("剩余时间：", middleLine-len1, 40)
    ctx.fillText(s"${60-time}s", middleLine-len2, 70)
    ctx.restore()
  }

  def drawMyExpression(expression: Int): Unit = {
    val offX = mainLength*13
    val offY = 33*mainLength
    ctx.drawImage(bubble1, offX, offY, 180, 120)
    val img = imgMap(expression)
    ctx.drawImage(img, offX+50, offY+25, 60, 60)
  }

  def drawOtherExpression(expression: Int): Unit = {
    val offY = 33*mainLength
    ctx.drawImage(bubble2, offX2, offY, 180, 130)
    val img = imgMap(expression)
    ctx.drawImage(img, offX2+50, offY+25, 60, 60)
  }


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
    ctx.fillStyle = ColorsSetting.darkGreyColor

    ctx.font = "24px Helvetica"

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
    val off = (offTime/100).toFloat
    data.foreach {d =>
      if(d._1 == uid) {
        val totalField = d._2.field
        totalField.foreach {f =>
          f._2 match {
            case TopBorder =>
              val x = f._1.x
              val y = f._1.y
//              ctx.fillStyle = ColorsSetting.backgroundColor2
              ctx.drawImage(greyBrick, x * mainLength, y * mainLength, mainLength+1, mainLength)

            case SideBorder =>
              val x = f._1.x
              val y = f._1.y
//              ctx.fillStyle = ColorsSetting.backgroundColor2
              ctx.drawImage(greyBrick, x * mainLength, y * mainLength, mainLength, mainLength+1)

            case Brick =>
              val x = f._1.x
              val y = f._1.y
//              ctx.fillStyle = ColorsSetting.greenColor
              ctx.drawImage(greenBrick, x * mainLength, y * mainLength, mainLength - 1, mainLength - 1)

            case RedBrick =>
              val x = f._1.x
              val y = f._1.y
//              ctx.fillStyle = ColorsSetting.redColor
              ctx.drawImage(yellowBrick, x * mainLength, y * mainLength, mainLength - 1, mainLength - 1)

            case HotBall =>
              val x = f._1.x
              val y = f._1.y
//              ctx.fillStyle = ColorsSetting.borderColor
              ctx.drawImage(redBrick, x * mainLength, y * mainLength, mainLength - 1, mainLength - 1)

            case Plank =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.darkGreyColor
              ctx.fillRect(x * mainLength, y * mainLength, mainLength+1, mainLength)

            case _ =>
              //空白领地不做处理
          }

        }

        val ball = d._2.ballLocation
        val x = ball.x
        val y = ball.y
        if(d._2.state==1){
          ctx.drawImage(ballBlue, x * mainLength + off * d._2.velocityX * mainLength, y * mainLength + off * d._2.velocityY * mainLength, mainLength, mainLength) //球的形状改成圆形,offTime逐步绘制
        } else {
          ctx.drawImage(ballGrey, x * mainLength + off * d._2.velocityX * mainLength, y * mainLength + off * d._2.velocityY * mainLength, mainLength, mainLength) //球的形状改成圆形,offTime逐步绘制
        }
        //绘制分数
        val score = d._2.score
        val offX4Score = 20
        val offY4Score = 33*mainLength //675
        ctx.fillStyle = ColorsSetting.blackColor
        ctx.font = "20px Helvetica"
        ctx.fillText(s"昵称:${d._2.name}", offX4Score, offY4Score)
        ctx.fillText(s"得分:$score", offX4Score, offY4Score+30)
      } else {
        //绘制对手的部分
        val totalField = d._2.field
        totalField.foreach {f =>
          f._2 match {
            case TopBorder =>
              val x = f._1.x
              val y = f._1.y
//              ctx.fillStyle = ColorsSetting.backgroundColor2
              ctx.drawImage(greyBrick, offX2 + x * sideLength, y * sideLength, sideLength, sideLength)

            case SideBorder =>
              val x = f._1.x
              val y = f._1.y
//              ctx.fillStyle = ColorsSetting.backgroundColor2
              ctx.drawImage(greyBrick, offX2 + x * sideLength, y * sideLength, sideLength, sideLength)

            case Brick =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.greenColor
              ctx.drawImage(greenBrick, offX2 + x * sideLength, y * sideLength, sideLength - 1, sideLength - 1)

            case RedBrick =>
              val x = f._1.x
              val y = f._1.y
              //              ctx.fillStyle = ColorsSetting.redColor
              ctx.drawImage(yellowBrick, offX2 + x * sideLength, y * sideLength, sideLength - 1, sideLength - 1)

            case HotBall =>
              val x = f._1.x
              val y = f._1.y
              //              ctx.fillStyle = ColorsSetting.borderColor
              ctx.drawImage(redBrick, offX2 + x * sideLength, y * sideLength, sideLength - 1, sideLength - 1)

            case Plank =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.darkGreyColor
              ctx.fillRect(offX2 + x * sideLength, y * sideLength, sideLength+1, sideLength)

            case _ =>
            //空白领地不做处理
          }

        }

        val ball = d._2.ballLocation
        val x = ball.x
        val y = ball.y
        if(d._2.state==1){
          ctx.drawImage(ballBlue, offX2 + x * sideLength + off * d._2.velocityX * sideLength, y * sideLength + off * d._2.velocityY * sideLength, sideLength, sideLength)
        } else {
          ctx.drawImage(ballGrey, offX2 + x * sideLength + off * d._2.velocityX * sideLength, y * sideLength + off * d._2.velocityY * sideLength, sideLength, sideLength) //球的形状改成圆形,offTime逐步绘制
        }
//        ctx.fillStyle = ColorsSetting.darkYellowColor
//        ctx.fillRect(offX2 + x * sideLength, y * sideLength, sideLength, sideLength)
        val score = d._2.score
        val offX4Score = offX2+15*sideLength
        val offY4Score = 33*mainLength //675
        ctx.fillStyle = ColorsSetting.blackColor
        ctx.fillText(s"昵称:${d._2.name}", offX4Score, offY4Score)
        ctx.fillText(s"得分:$score", offX4Score, offY4Score+25)
      }
    }
  }

}
