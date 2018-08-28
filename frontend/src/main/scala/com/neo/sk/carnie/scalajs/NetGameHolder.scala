package com.neo.sk.carnie.scalajs

import java.util.concurrent.atomic.AtomicInteger

import com.neo.sk.carnie.paper.Protocol.{GridDataSync, Key, NetTest}
import com.neo.sk.carnie.paper._
import com.neo.sk.carnie.util.MiddleBufferInJs
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.html.{Document => _, _}
import org.scalajs.dom.raw._
import com.neo.sk.carnie.util.byteObject.decoder
import com.neo.sk.carnie.util.byteObject.ByteObject._

import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 12:45 PM
  */
@JSExportTopLevel("scalajs.NetGameHolder")
object NetGameHolder extends js.JSApp {

  val bounds = Point(Boundary.w, Boundary.h)
  val border = Point(BorderSize.w, BorderSize.h)
  val window = Point(Window.w, Window.h)
  val SmallMap = Point(littleMap.w, littleMap.h)
  //  val canvasUnit = 20
  private val canvasUnit = (dom.window.innerWidth.toInt / window.x).toInt
  val textLineHeight = 14
//  private val canvasBoundary = bounds * canvasUnit
  //  private val windowBoundary = window * canvasUnit
  private val windowBoundary = Point(dom.window.innerWidth.toInt, dom.window.innerHeight.toInt)
  private val canvasSize = (border.x - 2) * (border.y - 2)
  private val fillWidth = 33

  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]
  private var myId = -1l

  val grid = new GridOnClient(border)

  var firstCome = true
  var wsSetup = false
  var justSynced = false
  var lastHeader = Point(border.x / 2, border.y / 2)
  var otherHeader: List[Point] = Nil
  var isWin = false
  var winnerName = "unknown"
  var syncGridData: scala.Option[Protocol.Data4Sync] = None
  var firstSyncGridData: scala.Option[Protocol.GridDataSync] = None
  var scale = 1.0
  var base = 1
  var startTime = System.currentTimeMillis()
  var endTime = System.currentTimeMillis()
  var scoreFlag = true
  var area = 0.16
  var kill = 0

  val idGenerator = new AtomicInteger(1)
  private var myActionHistory = Map[Int, (Int, Long)]() //(actionId, (keyCode, frameCount))

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
    val mapColor = "#C0C0C0"
    val gradeColor = "#3358FF"
  }

  private[this] val nameField = dom.document.getElementById("name").asInstanceOf[HTMLInputElement]
  private[this] val joinButton = dom.document.getElementById("join").asInstanceOf[HTMLButtonElement]
  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private[this] val formField = dom.document.getElementById("form").asInstanceOf[HTMLFormElement]
  private[this] val bodyField = dom.document.getElementById("body").asInstanceOf[HTMLBodyElement]
  private[this] val background = dom.document.getElementById("Background").asInstanceOf[Canvas]
  private[this] val backCtx = background.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  private val championHeaderImg = dom.document.createElement("img").asInstanceOf[Image]
  private val myHeaderImg = dom.document.createElement("img").asInstanceOf[Image]
  private val otherHeaderImg = dom.document.createElement("img").asInstanceOf[Image]
  championHeaderImg.src = "/carnie/static/img/champion.png"
  myHeaderImg.src = "/carnie/static/img/girl.png"
  otherHeaderImg.src = "/carnie/static/img/boy.png"

  private var nextFrame = 0
  private var logicFrameTime = System.currentTimeMillis()

  def main(): Unit = {
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
  }

  def startGame(): Unit = {
    drawGameOn()
    dom.window.setInterval(() => gameLoop(), Protocol.frameRate)
    dom.window.requestAnimationFrame(gameRender())
  }

  def gameRender(): Double => Unit = { d =>
    val curTime = System.currentTimeMillis()
    val offsetTime = curTime - logicFrameTime
    draw(offsetTime)

    nextFrame = dom.window.requestAnimationFrame(gameRender())
  }

  def drawGameOn(): Unit = {
    canvas.width = windowBoundary.x.toInt
    canvas.height = windowBoundary.y.toInt

    background.width = windowBoundary.x.toInt
    background.height = windowBoundary.y.toInt

    backCtx.fillStyle = ColorsSetting.backgroundColor
    backCtx.fillRect(0, 0, background.width, background.height)
//
//    backCtx.drawImage(canvas, 0 ,0)
  }

  def drawGameOff(): Unit = {
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

  def drawGameDie(): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    if (firstCome) {
      ctx.font = "36px Helvetica"
      ctx.fillText("Please wait.", 150, 180)
    } else {
      dom.window.cancelAnimationFrame(nextFrame)
      ctx.font = "16px Helvetica"
      val text = grid.getKiller(myId) match {
        case Some(killer) =>
          scale = 1
          ctx.scale(1, 1)
          s"Ops, You Killed By ${killer._2}! Press Space Key To Revenge!"

        case None =>
          scale = 1
          ctx.scale(1, 1)
          "Ops, Press Space Key To Restart!"
      }
      val time = {
        val temp = (endTime - startTime) / 1000
        val tempM = temp / 60
        val s1 = temp % 60
        val s = if(s1<0) "00" else if(s1<10) "0"+s1 else s1.toString
        val m = if(tempM<0) "00" else if(tempM<10) "0"+tempM else tempM.toString
        m + ":" + s
      }
      val bestScore = if(historyRank.exists(_.id == myId)) historyRank.find(_.id == myId).head.area else area
      ctx.fillText(text, 150, 180)
      ctx.save()
      ctx.font = "bold 24px Helvetica"
      ctx.fillStyle = ColorsSetting.gradeColor
      ctx.fillText("YOUR SCORE:", 150, 250)
      ctx.fillText(f"${area / canvasSize * 100}%.2f" + "%", 380, 250)
      ctx.fillText("BEST SCORE:", 150, 290)
      ctx.fillText(f"${bestScore.toDouble / canvasSize * 100}%.2f" + "%", 380, 290)
      ctx.fillText(s"PLAYERS KILLED:", 150, 330)
      ctx.fillText(s"$kill", 380, 330)
      ctx.fillText(s"TIME PLAYED:", 150, 370)
      ctx.fillText(s"$time", 380, 370)
      ctx.restore()
    }
  }

  def drawGameWin(winner: String): Unit = {
    ctx.fillStyle = ColorsSetting.backgroundColor
    ctx.fillRect(0, 0, windowBoundary.x, windowBoundary.y)
    ctx.fillStyle = ColorsSetting.fontColor
    ctx.font = "36px Helvetica"
    ctx.fillText(s"winner is $winner, Press Space Key To Restart!", 150, 180)
    dom.window.cancelAnimationFrame(nextFrame)
  }

  def gameLoop(): Unit = {
    logicFrameTime = System.currentTimeMillis()
    if (wsSetup) {
      if (!justSynced) { //前端更新
        update()
      } else {
        if (syncGridData.nonEmpty) {
          setSyncGridData(syncGridData.get)
          syncGridData = None
        } else if(firstSyncGridData.nonEmpty){
          initSyncGridData(firstSyncGridData.get)
          firstSyncGridData = None
        }
        justSynced = false
      }
    }
  }

  def update(): Unit = {
    grid.update()
  }


  def drawSmallMap(myheader: Point, otherSnakes: List[SkDt]): Unit = {
    val offx = myheader.x.toDouble / border.x * SmallMap.x
    val offy = myheader.y.toDouble / border.y * SmallMap.y
    ctx.fillStyle = ColorsSetting.mapColor
    val w = canvas.width - littleMap.w * canvasUnit * 1.034
    val h = canvas.height - littleMap.h * canvasUnit * 1.026
    ctx.save()
    ctx.globalAlpha = 0.5
    ctx.fillRect(w.toInt, h.toInt, littleMap.w * canvasUnit, littleMap.h * canvasUnit)
    ctx.restore()
    ctx.drawImage(myHeaderImg, (w + offx * canvasUnit).toInt, (h + offy * canvasUnit).toInt, 10, 10)
    otherSnakes.foreach { i =>
      val x = i.header.x.toDouble / border.x * SmallMap.x
      val y = i.header.y.toDouble / border.y * SmallMap.y
      ctx.fillStyle = i.color
      ctx.fillRect((w + x * canvasUnit).toInt, (h + y * canvasUnit).toInt, 10, 10)
    }
  }

  def draw(offsetTime: Long): Unit = {
    if (wsSetup) {
      if (isWin) {
        drawGameWin(winnerName)
      } else {
        val data = grid.getGridData
        data.snakes.find(_.id == myId) match {
          case Some(_) =>
            firstCome = false
            if(scoreFlag){
              startTime = System.currentTimeMillis()
              area = 0.0
              kill = 0
              scoreFlag = false
            }
            drawGrid(myId, data, offsetTime)

          case None =>
            drawGameDie()
        }
      }
    } else {
      drawGameOff()
    }
  }

  def drawGrid(uid: Long, data: GridDataSync, offsetTime: Long): Unit = { //头所在的点是屏幕的正中心
    val snakes = data.snakes
    val otherSnakes = snakes.filterNot(_.id == uid)
    val championId = if (data.fieldDetails.nonEmpty) {
      data.fieldDetails.groupBy(_.id).toList.sortBy(_._2.length).reverse.head._1
    } else 0

    lastHeader = snakes.find(_.id == uid) match {
      case Some(s) =>
        val nextDirection = grid.nextDirection(s.id).getOrElse(s.direction)
        val direction = if(s.direction + nextDirection != Point(0,0)) nextDirection else s.direction
        s.header + direction * offsetTime.toFloat / Protocol.frameRate

      case None =>
        lastHeader
    }

    val offx = window.x / 2 - lastHeader.x //新的框的x偏移量
    val offy = window.y / 2 - lastHeader.y //新的框的y偏移量

    ctx.clearRect(0, 0, windowBoundary.x, windowBoundary.y)

    val criticalX = (window.x + 1) /scale
    val criticalY = window.y / scale

    val bodies = data.bodyDetails.filter(p => Math.abs(p.x - lastHeader.x) < criticalX && Math.abs(p.y - lastHeader.y) < criticalY).map(i => i.copy(x = i.x + offx, y = i.y + offy))
    val fields = data.fieldDetails.filter(p => Math.abs(p.x - lastHeader.x) < criticalX && Math.abs(p.y - lastHeader.y) < criticalY).map(i => i.copy(x = i.x + offx, y = i.y + offy))
    val borders = data.borderDetails.filter(p => Math.abs(p.x - lastHeader.x) < criticalX && Math.abs(p.y - lastHeader.y) < criticalY).map(i => i.copy(x = i.x + offx, y = i.y + offy))

    val myField = fields.count(_.id == myId)
    scale = 1 - Math.sqrt(myField) * 0.0048

    ctx.save()
    setScale(scale, windowBoundary.x / 2, windowBoundary.y / 2)

    ctx.globalAlpha = 0.5
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

    //先画冠军的头
    snakes.filter(_.id == championId).foreach { s =>
      ctx.globalAlpha = 0.5
      ctx.fillStyle = s.color
      val nextDirection = grid.nextDirection(s.id).getOrElse(s.direction)
      val direction = if(s.direction + nextDirection != Point(0,0)) nextDirection else s.direction

      val off = direction * offsetTime.toFloat / Protocol.frameRate
      val tempDir = Point(if (direction.x > 0) 1 else off.x, if (direction.y > 0) 1 else off.y)

      if (direction.x.toInt == 1 || direction.x.toInt == -1)
        ctx.fillRect((s.header.x + offx + tempDir.x) * canvasUnit, (s.header.y + offy) * canvasUnit, math.abs(off.x) * canvasUnit, canvasUnit)
      else
        ctx.fillRect((s.header.x + offx) * canvasUnit, (s.header.y + offy + tempDir.y) * canvasUnit, canvasUnit, math.abs(off.y) * canvasUnit)

      ctx.globalAlpha = 1.0
      ctx.drawImage(championHeaderImg, (s.header.x + offx + off.x) * canvasUnit, (s.header.y + offy + off.y) * canvasUnit, canvasUnit, canvasUnit)
    }

    //画其他人的头
    snakes.filterNot(_.id == championId).foreach { snake =>
      ctx.globalAlpha = 0.5
      ctx.fillStyle = snake.color
      val img = if (snake.id == uid) myHeaderImg else otherHeaderImg

      val nextDirection = grid.nextDirection(snake.id).getOrElse(snake.direction)
      val direction = if(snake.direction + nextDirection != Point(0,0)) nextDirection else snake.direction

      val off = direction * offsetTime.toFloat / Protocol.frameRate
      val tempDir = Point(if (direction.x > 0) 1 else off.x, if (direction.y > 0) 1 else off.y)
      if (direction.x.toInt == 1 || direction.x.toInt == -1)
        ctx.fillRect((snake.header.x + offx + tempDir.x) * canvasUnit, (snake.header.y + offy) * canvasUnit, math.abs(off.x) * canvasUnit, canvasUnit)
      else
        ctx.fillRect((snake.header.x + offx) * canvasUnit, (snake.header.y + offy + tempDir.y) * canvasUnit, canvasUnit, math.abs(off.y) * canvasUnit)
      ctx.globalAlpha = 1.0

      ctx.drawImage(img, (snake.header.x + offx + off.x) * canvasUnit, (snake.header.y + offy + off.y) * canvasUnit, canvasUnit, canvasUnit)
    }
    ctx.fillStyle = ColorsSetting.borderColor
    borders.foreach { case Bord(x, y) =>
      ctx.fillRect(x * canvasUnit, y * canvasUnit, canvasUnit * 1.05, canvasUnit * 1.05)
    }
    ctx.restore()

    drawSmallMap(lastHeader, otherSnakes)

    ctx.fillStyle = ColorsSetting.fontColor
    ctx.textAlign = "left"
    ctx.textBaseline = "top"

    val leftBegin = 10
    val rightBegin = windowBoundary.x - 180

    val mySnake = snakes.filter(_.id == uid).head
    val baseLine = 1
    ctx.font = "12px Helvetica"
    drawTextLine(s"YOU: id=[${mySnake.id}]    name=[${mySnake.name.take(32)}]", leftBegin, 0, baseLine)
    drawTextLine(s"your kill = ${mySnake.kill}", leftBegin, 1, baseLine)

    val myRankBaseLine = 3
    currentRank.filter(_.id == myId).foreach { score =>
      area = score.area
      kill = score.k
      endTime = System.currentTimeMillis()
      val color = snakes.find(_.id == myId).map(_.color).getOrElse(ColorsSetting.defaultColor)
      ctx.globalAlpha = 0.6
      ctx.fillStyle = color
      ctx.save()
      ctx.fillRect(leftBegin, (myRankBaseLine - 1) * textLineHeight, fillWidth + windowBoundary.x / 6 * (score.area.toDouble / canvasSize), textLineHeight)
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
      ctx.fillRect(windowBoundary.x - 10 - fillWidth - windowBoundary.x / 6 * (score.area.toDouble / canvasSize), (index + currentRankBaseLine) * textLineHeight,
        fillWidth + windowBoundary.x / 6 * (score.area.toDouble / canvasSize), textLineHeight)
      ctx.restore()

      ctx.globalAlpha = 1
      ctx.fillStyle = ColorsSetting.fontColor
      index += 1
      drawTextLine(s"[$index]: ${score.n.+("   ").take(3)} area=" + f"${score.area.toDouble / canvasSize * 100}%.2f" + s"% kill=${score.k}", rightBegin.toInt, index, currentRankBaseLine)
    }
  }

  def drawTextLine(str: String, x: Int, lineNum: Int, lineBegin: Int = 0): Unit = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight)
  }

  val sendBuffer = new MiddleBufferInJs(409600) //sender buffer

  def joinGame(name: String): Unit = {
    formField.innerHTML = ""
    bodyField.style.backgroundColor = "white"
    val gameStream = new WebSocket(getWebSocketUri(dom.document, name))
    gameStream.onopen = { event0: Event =>
      startGame()
      wsSetup = true
      canvas.focus()
      canvas.onkeydown = { e: dom.KeyboardEvent => {
        if (watchKeys.contains(e.keyCode)) {
          val msg: Protocol.UserAction = if (e.keyCode == KeyCode.F2) {
            NetTest(myId, System.currentTimeMillis())
          } else {
            val frame = grid.frameCount + 1
            val actionId = idGenerator.getAndIncrement()
            grid.addActionWithFrame(myId, e.keyCode, frame)
            if (e.keyCode != KeyCode.Space) {
              myActionHistory += actionId -> (e.keyCode, frame)
            } else {
              scoreFlag = true
            }
            if (e.keyCode == KeyCode.Space && isWin) { //重新开始游戏
              scale = 1
              ctx.scale(1, 1)
              firstCome = true
              isWin = false
              winnerName = "unknown"
            }
            Key(myId, e.keyCode, frame, actionId)
          }
          msg.fillMiddleBuffer(sendBuffer) //encode msg
          val ab: ArrayBuffer = sendBuffer.result() //get encoded data.
          gameStream.send(ab) // send data.
          e.preventDefault()
        }
      }
      }
      event0
    }

    gameStream.onerror = { event: Event =>
      drawGameOff()
      //      playground.insertBefore(p(s"Failed: code: ${event.`type`}"), playground.firstChild)
      joinButton.disabled = false
      wsSetup = false
      nameField.focus()
    }

    gameStream.onmessage = { event: MessageEvent =>
      event.data match {
        case blobMsg: Blob =>
          val fr = new FileReader()
          fr.readAsArrayBuffer(blobMsg)
          fr.onloadend = { _: Event =>
            val buf = fr.result.asInstanceOf[ArrayBuffer] // read data from ws.

            val middleDataInJs = new MiddleBufferInJs(buf) //put data into MiddleBuffer

            val encodedData: Either[decoder.DecoderFailure, Protocol.GameMessage] =
              bytesDecode[Protocol.GameMessage](middleDataInJs) // get encoded data.
            encodedData match {
              case Right(data) => data match {
                case Protocol.Id(id) => myId = id

                case Protocol.TextMsg(message) => writeToArea(s"MESSAGE: $message")

                case Protocol.NewSnakeJoined(id, user) => writeToArea(s"$user joined!")

                case Protocol.SnakeLeft(id, user) => writeToArea(s"$user left!")

                case Protocol.SnakeAction(id, keyCode, frame, actionId) =>
                  if (id == myId) { //收到自己的进行校验是否与预判一致，若不一致则回溯
                    if (myActionHistory.get(actionId).isEmpty) { //前端没有该项，则加入
                      grid.addActionWithFrame(id, keyCode, frame)
                      if (frame < grid.frameCount && grid.frameCount - frame <= (grid.maxDelayed - 1)) { //回溯
                        grid.recallGrid(frame, grid.frameCount)
                      }
                    } else {
                      if (myActionHistory(actionId)._1 != keyCode || myActionHistory(actionId)._2 != frame) { //若keyCode或则frame不一致则进行回溯
                        grid.deleteActionWithFrame(id, myActionHistory(actionId)._2)
                        grid.addActionWithFrame(id, keyCode, frame)
                        val miniFrame = Math.min(frame, myActionHistory(actionId)._2)
                        if (miniFrame < grid.frameCount && grid.frameCount - miniFrame <= (grid.maxDelayed - 1)) { //回溯
                          grid.recallGrid(miniFrame, grid.frameCount)
                        }
                      }
                      myActionHistory -= actionId
                    }
                  } else { //收到别人的动作则加入action，若帧号滞后则进行回溯
                    //                    println(s"receive--back$frame now-${grid.frameCount}")
                    grid.addActionWithFrame(id, keyCode, frame)
                    if (frame < grid.frameCount && grid.frameCount - frame <= (grid.maxDelayed - 1)) { //回溯
                      grid.recallGrid(frame, grid.frameCount)
                    }
                  }

                case Protocol.SomeOneWin(winner) =>
                  isWin = true
                  winnerName = winner
                  grid.cleanData()

                case Protocol.Ranks(current, history) =>
                  currentRank = current
                  historyRank = history

                case data: Protocol.GridDataSync =>
                  firstSyncGridData = Some(data)
                  justSynced = true

                case data: Protocol.Data4Sync =>
                  syncGridData = Some(data)
                  justSynced = true

                case Protocol.NetDelayTest(createTime) =>
                  val receiveTime = System.currentTimeMillis()
                  val m = s"Net Delay Test: createTime=$createTime, receiveTime=$receiveTime, twoWayDelay=${receiveTime - createTime}"
                  writeToArea(m)
              }

              case Left(e) =>
                println(s"got error: ${e.message}")
            }
          }
      }
    }

    gameStream.onclose = { event: Event =>
      drawGameOff()
      //      playground.insertBefore(p("Connection to game lost. You can try to rejoin manually."), playground.firstChild)
      joinButton.disabled = false
      wsSetup = false
      nameField.focus()
    }

    def writeToArea(text: String): Unit = {}
    //      playground.insertBefore(p(text), playground.firstChild)
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

  def setSyncGridData(data: Protocol.Data4Sync): Unit = {
    grid.frameCount = data.frameCount
    var newGrid = grid.grid.filter(_._2 match { case Body(_, _) => false case _ => true })
    data.blankDetails.foreach { blank =>
      blank._2.foreach { l => (l._1 to l._2 by 1).foreach(y => newGrid -= Point(blank._1, y)) }
    }
    data.fieldDetails.foreach { users =>
      users._2.foreach { x =>
        x._2.foreach { l => (l._1 to l._2 by 1).foreach(y => newGrid += Point(x._1, y) -> Field(users._1)) }
      }
    }
    data.bodyDetails.foreach(b => newGrid += Point(b.x, b.y) -> Body(b.id, b.fid))
    grid.grid = newGrid
    grid.actionMap = grid.actionMap.filterKeys(_ >= (data.frameCount - grid.maxDelayed))
    grid.snakes = data.snakes.map(s => s.id -> s).toMap
    grid.killHistory = data.killHistory.map(k => k.killedId -> (k.killerId, k.killerName)).toMap
  }

  def initSyncGridData(data: Protocol.GridDataSync): Unit = {
    grid.frameCount = data.frameCount
    val bodyMap = data.bodyDetails.map(b => Point(b.x, b.y) -> Body(b.id, b.fid)).toMap
    val fieldMap = data.fieldDetails.map(f => Point(f.x, f.y) -> Field(f.id)).toMap
    val bordMap = grid.grid.filter(_._2 match {case Border => true case _ => false})
    val gridMap = bodyMap ++ fieldMap ++ bordMap
    grid.grid = gridMap
    grid.actionMap = grid.actionMap.filterKeys(_ >= (data.frameCount - grid.maxDelayed))
    grid.snakes = data.snakes.map(s => s.id -> s).toMap
    grid.killHistory = data.killHistory.map(k => k.killedId -> (k.killerId, k.killerName)).toMap
  }

  def setScale(scale: Double, x: Double, y: Double) = {
    ctx.translate(x, y)
    ctx.scale(scale, scale)
    ctx.translate(-x, -y)
  }


}
