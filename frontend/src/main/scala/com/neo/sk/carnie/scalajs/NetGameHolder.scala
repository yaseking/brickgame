package com.neo.sk.carnie.scalajs

import com.neo.sk.carnie.Protocol
import com.neo.sk.carnie.Protocol.GridDataSync
import com.neo.sk.carnie._
import org.scalajs.dom
import org.scalajs.dom.ext.{Color, KeyCode}
import org.scalajs.dom.html.{Document => _, _}
import org.scalajs.dom.raw._

import scala.scalajs.js

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 12:45 PM
  */
object NetGameHolder extends js.JSApp {


  val bounds = Point(Boundary.w, Boundary.h)
  val border = Point(BorderSize.w, BorderSize.h)
  val window = Point(Window.w, Window.h)
  val SmallMap = Point(LittleMap.w, LittleMap.h)
  val canvasUnit = 20
  val textLineHeight = 14
  private val canvasBoundary = bounds * canvasUnit
  private val windowBoundary = window * canvasUnit
  private val canvasSize = border.x * border.y
  private val fillWidth = 33

  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]
  private var myId = -1l

  val grid = new GridOnClient(border)

  var firstCome = true
  var wsSetup = false
  var justSynced = false
  var lastHeader = Point(border.x / 2, border.y / 2)
  var isWin = false
  var winnerName = "unknown"

  val watchKeys = Set(
    KeyCode.Space,
    KeyCode.Left,
    KeyCode.Up,
    KeyCode.Right,
    KeyCode.Down,
    KeyCode.F2
  )

  object ColorsSetting {
    val backgroundColor = "#F5F5F5"
    val fontColor = "#000000"
    val defaultColor = "#000080"
    val borderColor = "#696969"
    val mapColor = "#d8d8d866"
  }

  private[this] val nameField = dom.document.getElementById("name").asInstanceOf[HTMLInputElement]
  private[this] val joinButton = dom.document.getElementById("join").asInstanceOf[HTMLButtonElement]
  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  val championHeaderImg = dom.document.createElement("img")
  val myHeaderImg = dom.document.createElement("img")
  val otherHeaderImg = dom.document.createElement("img")
  val myLocation = dom.document.createElement("img")
  championHeaderImg.asInstanceOf[Image].src = "/carnie/static/img/champion.png"
  myHeaderImg.asInstanceOf[Image].src = "/carnie/static/img/myHeader.png"
  otherHeaderImg.asInstanceOf[Image].src = "/carnie/static/img/otherHeader.png"
  myLocation.asInstanceOf[Image].src = "/carnie/static/img/myLocation.png"

  @scala.scalajs.js.annotation.JSExport
  override def main(): Unit = {
    drawGameOff()
    canvas.width = windowBoundary.x
    canvas.height = windowBoundary.y

    joinButton.onclick = { event: MouseEvent =>
      joinGame(nameField.value)
      event.preventDefault()
    }
    nameField.focus()
    nameField.onkeypress = { event: KeyboardEvent =>
      if (event.keyCode == 13) {
        joinButton.click()
        event.preventDefault()
      }
    }

    dom.window.setInterval(() => gameLoop(), Protocol.frameRate)
  }

  def drawGameOn(): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor
    ctx.fillRect(0, 0, canvas.width, canvas.height)
  }

  def drawGameOff(): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor
    ctx.fillRect(0, 0, windowBoundary.x * canvasUnit, windowBoundary.y * canvasUnit)
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
    ctx.fillRect(0, 0, windowBoundary.x * canvasUnit, windowBoundary.y * canvasUnit)
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.font = "36px Helvetica"
    ctx.fillText(s"winner is $winner, Press Space Key To Restart!", 150, 180)
  }


  def gameLoop(): Unit = {
    if (wsSetup) {
      if (!justSynced) {
        update()
      } else {
        justSynced = false
      }
    }
    draw()
  }


  def update(): Unit = {
    grid.update()
  }

  def drawMap(myheader: Point): Unit = {
    val Offx = myheader.x.toDouble / border.x * SmallMap.x
    val Offy = myheader.y.toDouble / border.y * SmallMap.y
    ctx.fillStyle = ColorsSetting.mapColor
    ctx.fillRect(990, 490, LittleMap.w * canvasUnit, LittleMap.h * canvasUnit)
    ctx.drawImage(myLocation.asInstanceOf[Image], 990 + Offx * canvasUnit / 2, 490 + Offy * canvasUnit / 2, canvasUnit / 2, canvasUnit / 2)
  }

  def draw(): Unit = {
    if (wsSetup) {
      val data = grid.getGridData
      if (isWin) {
        drawGameWin(winnerName)
      } else {
        if (data.fieldDetails.nonEmpty) {
          drawGrid(myId, data, data.fieldDetails.groupBy(_.id).toList.sortBy(_._2.length).reverse.head._1)
        } else {
          drawGrid(myId, data, 0)
        }
      }
    } else {
      drawGameOff()
    }
  }

  def drawGrid(uid: Long, data: GridDataSync, championId: Long): Unit = { //头所在的点是屏幕的正中心

    val snakes = data.snakes
    lastHeader = snakes.find(_.id == uid) match {
      case Some(s) => s.header
      case None => lastHeader
    }
    val offx = window.x / 2 - lastHeader.x //新的框的x偏移量
    val offy = window.y / 2 - lastHeader.y //新的框的y偏移量

    ctx.fillStyle = ColorsSetting.backgroundColor
    ctx.fillRect(0, 0, windowBoundary.x * canvasUnit, windowBoundary.y * canvasUnit)

    val bodies = data.bodyDetails.map(i => i.copy(x = i.x + offx, y = i.y + offy))
    val fields = data.fieldDetails.map(i => i.copy(x = i.x + offx, y = i.y + offy))
    val borders = data.borderDetails.map(i => i.copy(x = i.x + offx, y = i.y + offy))

    bodies.foreach { case Bd(id, x, y) =>
      val color = snakes.find(_.id == id).map(_.color).getOrElse(ColorsSetting.defaultColor)
      ctx.globalAlpha = 0.6
      ctx.fillStyle = color
      if (id == uid) {
        ctx.save()
        ctx.fillRect(x * canvasUnit, y * canvasUnit, canvasUnit, canvasUnit)
        ctx.restore()
      } else {
        ctx.fillRect(x * canvasUnit, y * canvasUnit, canvasUnit, canvasUnit)
      }
    }

    fields.foreach { case Fd(id, x, y) =>
      val color = snakes.find(_.id == id).map(_.color).getOrElse(ColorsSetting.defaultColor)
      ctx.globalAlpha = 1.0
      ctx.fillStyle = color
      if (id == uid) {
        ctx.save()
        ctx.fillRect(x * canvasUnit, y * canvasUnit, canvasUnit, canvasUnit)
        ctx.restore()
      } else {
        ctx.fillRect(x * canvasUnit, y * canvasUnit, canvasUnit, canvasUnit)
      }
    }

    ctx.fillStyle = ColorsSetting.borderColor
    borders.foreach { case Bord(x, y) =>
      ctx.fillRect(x * canvasUnit, y * canvasUnit, canvasUnit, canvasUnit)
    }

    //先画冠军的头
    snakes.filter(_.id == championId).foreach { s =>
      ctx.drawImage(championHeaderImg.asInstanceOf[Image], (s.header.x + offx) * canvasUnit, (s.header.y + offy) * canvasUnit, canvasUnit, canvasUnit)
    }

    //画其他人的头
    snakes.filterNot(_.id == championId).foreach { snake =>
      val img = if (snake.id == uid) myHeaderImg else otherHeaderImg
      val x = snake.header.x + offx
      val y = snake.header.y + offy
      ctx.drawImage(img.asInstanceOf[Image], x * canvasUnit, y * canvasUnit, canvasUnit, canvasUnit)
    }

    ctx.fillStyle = ColorsSetting.fontColor
    ctx.textAlign = "left"
    ctx.textBaseline = "top"

    val leftBegin = 10
    val rightBegin = windowBoundary.x - 180

    snakes.find(_.id == uid) match {
      case Some(mySnake) =>
        firstCome = false
        val baseLine = 1
        ctx.font = "12px Helvetica"
        drawTextLine(s"YOU: id=[${mySnake.id}]    name=[${mySnake.name.take(32)}]", leftBegin, 0, baseLine)
        drawTextLine(s"your kill = ${mySnake.kill}", leftBegin, 1, baseLine)

      case None =>
        if (firstCome) {
          println("????????????????????????????????????")
          ctx.font = "36px Helvetica"
          ctx.fillText("Please wait.", 150 + offx, 180 + offy)
        } else {
          ctx.font = "36px Helvetica"
          val text = grid.getKiller(uid) match {
            case Some(killer) =>
              s"Ops, You Killed By ${killer._2}! Press Space Key To Revenge!"

            case None =>
              "Ops, Press Space Key To Restart!"
          }
          ctx.fillText(text, 150 + offx, 180 + offy)
        }
    }

    ctx.font = "12px Helvetica"
    val myRankBaseLine = 3
//    drawTextLine(s" --- My Rank --- ", leftBegin, index, myRankBaseLine)
    currentRank.filter(_.id == myId).foreach { score =>
      val color = snakes.find(_.id == myId).map(_.color).getOrElse(ColorsSetting.defaultColor)
      ctx.globalAlpha = 0.6
      ctx.fillStyle = color
      ctx.save()
      ctx.fillRect(leftBegin, (myRankBaseLine - 1) * textLineHeight, fillWidth + windowBoundary.x / 3 * (score.area.toDouble / canvasSize), textLineHeight)
      ctx.restore()

      ctx.globalAlpha = 1
      ctx.fillStyle = ColorsSetting.fontColor
      drawTextLine(f"${score.area.toDouble / canvasSize * 100}%.2f" + s"%", leftBegin, 0, myRankBaseLine)
//      drawTextLine(s"kill=${score.k}", leftBegin + fillWidth + (windowBoundary.x / 3 * (score.area.toDouble / canvasSize)).toInt + 6, index, myRankBaseLine)
//      drawTextLine(s"area=" + f"${score.area.toDouble / canvasSize * 100}%.2f" + s"% kill=${score.k}", leftBegin + fillWidth + (windowBoundary.x / 3 * (score.area.toDouble / canvasSize)).toInt + 10, index, myRankBaseLine)
    }

//    ctx.font = "12px Helvetica"
    val currentRankBaseLine = 1
    var index = 0
    drawTextLine(s" --- Current Rank --- ", rightBegin, index, currentRankBaseLine)
    currentRank.foreach { score =>
      val color = snakes.find(_.id == score.id).map(_.color).getOrElse(ColorsSetting.defaultColor)
      ctx.globalAlpha = 0.6
      ctx.fillStyle = color
      ctx.save()
      ctx.fillRect(windowBoundary.x - 10 - fillWidth - windowBoundary.x / 3 * (score.area.toDouble / canvasSize), (index + currentRankBaseLine) * textLineHeight,
        fillWidth + windowBoundary.x / 3 * (score.area.toDouble / canvasSize), textLineHeight)
      ctx.restore()

      ctx.globalAlpha = 1
      ctx.fillStyle = ColorsSetting.fontColor
      index += 1
      drawTextLine(s"[$index]: ${score.n.+("   ").take(3)} area=" + f"${score.area.toDouble / canvasSize * 100}%.2f" + s"% kill=${score.k}", rightBegin, index, currentRankBaseLine)
    }

//    val historyRankBaseLine = 1
//    index = 0
//    drawTextLine(s" --- History Rank --- ", rightBegin, index, historyRankBaseLine)
//    historyRank.foreach { score =>
//      index += 1
//      drawTextLine(s"[$index]: ${score.n.+("   ").take(3)} area=" + f"${score.area.toDouble / canvasSize * 100}%.2f" + s"% kill=${score.k}", rightBegin, index, historyRankBaseLine)
//    }
    drawMap(lastHeader)
  }

  def drawTextLine(str: String, x: Int, lineNum: Int, lineBegin: Int = 0) = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight)
  }

  def joinGame(name: String): Unit = {
    joinButton.disabled = true
    val playground = dom.document.getElementById("playground")
    playground.innerHTML = s"Trying to join game as '$name'..."
    val gameStream = new WebSocket(getWebSocketUri(dom.document, name))
    gameStream.onopen = { event0: Event =>
      drawGameOn()
      playground.insertBefore(p("Game connection was successful!"), playground.firstChild)
      wsSetup = true
      canvas.focus()
      canvas.onkeydown = { e: dom.KeyboardEvent => {
        println(s"keydown: ${e.keyCode}")
        if (watchKeys.contains(e.keyCode)) {
          println(s"key down: [${e.keyCode}]")
          if (e.keyCode == KeyCode.F2) {
            gameStream.send("T" + System.currentTimeMillis())
          } else {
            if (e.keyCode == KeyCode.Space && isWin) {
              firstCome = true
              isWin = false
              winnerName = "unknown"
            }
            gameStream.send(e.keyCode.toString)
          }
          e.preventDefault()
        }
      }
      }
      event0
    }

    gameStream.onerror = { event: ErrorEvent =>
      drawGameOff()
      playground.insertBefore(p(s"Failed: code: ${event.colno}"), playground.firstChild)
      joinButton.disabled = false
      wsSetup = false
      nameField.focus()
    }


    import io.circe.generic.auto._
    import io.circe.parser._

    gameStream.onmessage = { event: MessageEvent =>
      val wsMsg = decode[Protocol.GameMessage](event.data.toString).right.get
      wsMsg match {
        case Protocol.Id(id) => myId = id

        case Protocol.TextMsg(message) => writeToArea(s"MESSAGE: $message")

        case Protocol.NewSnakeJoined(id, user) => writeToArea(s"$user joined!")

        case Protocol.SnakeLeft(id, user) => writeToArea(s"$user left!")

        case Protocol.SnakeAction(id, keyCode, frame) => grid.addActionWithFrame(id, keyCode, frame)

        case Protocol.SomeOneWin(winner) =>
          isWin = true
          winnerName = winner
          grid.cleanData()

        case Protocol.Ranks(current, history) =>
          currentRank = current
          historyRank = history

        case data: Protocol.GridDataSync =>
          grid.frameCount = data.frameCount
          val bodyMap = data.bodyDetails.map(b => Point(b.x, b.y) -> Body(b.id)).toMap
          val fieldMap = data.fieldDetails.map(f => Point(f.x, f.y) -> Field(f.id)).toMap
          val bordMap = data.borderDetails.map(b => Point(b.x, b.y) -> Border).toMap
          val gridMap = bodyMap ++ fieldMap ++ bordMap
          grid.grid = gridMap
          grid.actionMap = grid.actionMap.filterKeys(_ > data.frameCount)
          grid.snakes = data.snakes.map(s => s.id -> s).toMap
          grid.killHistory = data.killHistory.map(k => k.killedId -> (k.killerId, k.killerName)).toMap

          justSynced = true

        case Protocol.NetDelayTest(createTime) =>
          val receiveTime = System.currentTimeMillis()
          val m = s"Net Delay Test: createTime=$createTime, receiveTime=$receiveTime, twoWayDelay=${receiveTime - createTime}"
          writeToArea(m)
      }
    }


    gameStream.onclose = { event: Event =>
      drawGameOff()
      playground.insertBefore(p("Connection to game lost. You can try to rejoin manually."), playground.firstChild)
      joinButton.disabled = false
      wsSetup = false
      nameField.focus()
    }

    def writeToArea(text: String): Unit =
      playground.insertBefore(p(text), playground.firstChild)
  }

  def getWebSocketUri(document: Document, nameOfChatParticipant: String): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}/carnie/netSnake/join?name=$nameOfChatParticipant"
  }

  def p(msg: String) = {
    val paragraph = dom.document.createElement("p")
    paragraph.innerHTML = msg
    paragraph
  }


}
