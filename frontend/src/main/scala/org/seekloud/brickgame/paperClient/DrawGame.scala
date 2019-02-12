package org.seekloud.brickgame.paperClient

import org.seekloud.brickgame.common.Constant.ColorsSetting
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

  private var windowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
  private val border = Point(BorderSize.w, BorderSize.h)
  private val window = Point(Window.w, Window.h)
  private var canvasUnit = (dom.window.innerWidth.toInt / window.x).toInt
  private val smallMap = Point(littleMap.w, littleMap.h)
  private val canvasSize = (border.x - 1) * (border.y - 1)
  var fieldScale = 1.0
  val defaultLength = 20

  private val textLineHeight = 15
  private val fillWidth = 33

  private val bodyAttribute = dom.document.getElementById("body").asInstanceOf[org.scalajs.dom.html.Body]
  //  private val backBtn = dom.document.getElementById("backBtn").asInstanceOf[Button]
  private var scale = 1.0

  def resetScreen(): Unit = {
    windowBoundary = Point(dom.window.innerWidth.toFloat, dom.window.innerHeight.toFloat)
    canvasUnit = (dom.window.innerWidth.toInt / window.x).toInt
    canvas.width = windowBoundary.x.toInt
    canvas.height = windowBoundary.y.toInt
    fieldScale = (-0.21)*canvasUnit + 7.6
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


  def drawGameOff(firstCome: Boolean, replayFinish: Option[Boolean], loading: Boolean, readFileError: Boolean): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor2
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    if (readFileError) {
      println("==============read file error")
      ctx.fillStyle = ColorsSetting.backgroundColor2
      canvas.width = 800
      canvas.height = 400
      ctx.fillRect(0, 0, 800.0, 400.0)
      ctx.fillStyle = ColorsSetting.fontColor
      //      rankCtx.clearRect(0, 0, dom.window.innerWidth.toInt, dom.window.innerHeight.toInt)
      ctx.font = "36px Helvetica"
      ctx.fillText("文件不存在或文件已损坏...", 150, 180)
    } else if (replayFinish.nonEmpty && replayFinish.get) {
      ctx.font = "36px Helvetica"
      ctx.fillText("Replay ends.", 150, 180)
    } else if (loading) {
      ctx.font = "36px Helvetica"
      ctx.fillText("Loading......", 150, 180)
    } else {
      if (firstCome) {
        ctx.font = "36px Helvetica"
        ctx.fillText("Welcome.", 150, 180)
      } else {
        ctx.font = "36px Helvetica"
        ctx.fillText("Ops, connection lost.", 150, 180)
        ctx.fillText("Press Space Key To Restart!", 150, 250)
      }
    }
  }

  def drawServerShutDown(): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor2
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.font = "36px Helvetica"
    ctx.fillText("Sorry, Some errors happened.", 150, 180)
  }

  def drawGameDie4Replay(): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor2
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.fillStyle = ColorsSetting.backgroundColor2
    canvas.width = 800
    canvas.height = 400
    ctx.fillRect(0, 0, 800.0, 400.0)
    ctx.fillStyle = ColorsSetting.fontColor
    //      rankCtx.clearRect(0, 0, dom.window.innerWidth.toInt, dom.window.innerHeight.toInt)
    ctx.font = "36px Helvetica"
    ctx.fillText("您观看的玩家已死亡...", 150, 180)
  }

  def drawGameWait(): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor2
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.font = "36px Helvetica"
    ctx.fillText("Please wait.", 150, 180)
  }

  //  def drawRecordError(): Unit = {
  //    ctx.fillStyle = ColorsSetting.backgroundColor2
  //    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
  //    ctx.fillStyle = ColorsSetting.fontColor
  //    ctx.font = "36px Helvetica"
  //    ctx.fillText("文件不存在或已损坏...", 150, 180)
  //  }

  def drawGameDie(killerOpt: Option[String], myScore: BaseScore, maxArea: Int, isReplay: Boolean = false): Unit = {
    //    backBtn.style.display="block"
//    ctx.fillStyle = ColorsSetting.backgroundColor2
    //    ctx.fillStyle = ColorsSetting.backgroundColor
//    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
//    ctx.fillStyle = ColorsSetting.gameNameColor
    ctx.fillStyle = ColorsSetting.fontColor3

    ctx.font = "24px Helvetica"
    ctx.scale(1, 1)

    val text = killerOpt match {
      case Some(killer) => s"Ops, You Are Killed By $killer! Press Space Key To Revenge!"
      case None => "Ops, Press Space Key To Restart!"
    }

    val length = ctx.measureText(text).width
    val offx = length / 2
    val x = (dom.window.innerWidth / 2).toInt - 145
    val y = if (isReplay) (dom.window.innerHeight / 2).toInt - 80 else (dom.window.innerHeight / 2).toInt - 100
    //    val y = (dom.window.innerHeight / 2).toInt - 180

    val gameTime = myScore.playTime
    val bestScore = maxArea / canvasSize * 100
    val time = {
      val tempM = gameTime / 60
      val s1 = gameTime % 60
      val s = if (s1 < 0) "00" else if (s1 < 10) "0" + s1 else s1.toString
      val m = if (tempM < 0) "00" else if (tempM < 10) "0" + tempM else tempM.toString
      m + ":" + s
    }
    ctx.fillText(text, dom.window.innerWidth / 2 - offx, y) //(500,180)
    ctx.save()
    ctx.font = "bold 24px Helvetica"
//    ctx.fillStyle = ColorsSetting.fontColor
    ctx.fillStyle = ColorsSetting.fontColor3
    ctx.fillText("YOUR SCORE:", x, y + 70)
    ctx.fillText(f"${myScore.area / canvasSize * 100}%.2f" + "%", x + 230, y + 70)
    ctx.fillText("BEST SCORE:", x, y + 110)
    ctx.fillText(f"$bestScore%.2f" + "%", x + 230, y + 110)
    ctx.fillText(s"PLAYERS KILLED:", x, y + 150)
    ctx.fillText(s"${myScore.kill}", x + 230, y + 150)
    if (!isReplay) {
      ctx.fillText(s"TIME PLAYED:", x, y + 190)
      ctx.fillText(s"$time", x + 230, y + 190)
    }
    ctx.restore()
  }

  def drawBarrage(killedName: String, killerName: String) = {
    ctx.save()
    ctx.globalAlpha = 0.6
    ctx.restore()
    ctx.save()
    if (dom.window.innerWidth > 1200) ctx.font = "bold 20px Microsoft YaHei"
    else ctx.font = "bold 15px Microsoft YaHei"
    ctx.fillStyle = "#FF5809"
    val txt = s"$killedName is killed by $killerName"
    val length = ctx.measureText(txt).width
    val offx = length / 2
    ctx.fillText(s"$killedName is killed by $killerName", dom.window.innerWidth / 2 - offx, (dom.window.innerHeight / 9).toInt)
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
              ctx.fillRect(x * defaultLength, y * defaultLength, defaultLength, defaultLength)

            case SideBorder =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.backgroundColor2
              ctx.fillRect(x * defaultLength, y * defaultLength, defaultLength, defaultLength)

            case Brick =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.greenColor
              ctx.fillRect(x * defaultLength, y * defaultLength, defaultLength - 1, defaultLength - 1)

            case Plank =>
              val x = f._1.x
              val y = f._1.y
              ctx.fillStyle = ColorsSetting.fontColor3
              ctx.fillRect(x * defaultLength, y * defaultLength, defaultLength, defaultLength)

            case _ =>
              //空白领地不做处理
          }

        }

        val ball = d._2.ballLocation
        val x = ball.x
        val y = ball.y
        ctx.fillStyle = ColorsSetting.darkYellowColor
        ctx.fillRect(x * defaultLength, y * defaultLength, defaultLength, defaultLength)
      }
    }
  }

}
