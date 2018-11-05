package com.neo.sk.carnie.paperClient

import com.neo.sk.carnie.common.Constant.ColorsSetting
import com.neo.sk.carnie.paperClient.Protocol.ReceivePingPacket
import org.scalajs.dom.CanvasRenderingContext2D

/**
  * Created by dry on 2018/9/3.
  **/
object PerformanceTool {
  //FPS
  private var lastRenderTime = System.currentTimeMillis()
  private var lastRenderTimes = 0
  private var renderTimes = 0
  private var tempTime = System.currentTimeMillis()

  private def addFps() = {
    val time = System.currentTimeMillis()
    renderTimes += 1
//    println(s"addFps time:${time - tempTime}")
    tempTime = time
    if (time - lastRenderTime > 1000) {
      lastRenderTime = time
      lastRenderTimes = renderTimes
      renderTimes = 0
    }
  }

  def renderFps(ctx: CanvasRenderingContext2D, leftBegin: Int, lineHeight: Int) = {
    addFps()
//    ctx.font = "14px Helvetica"
    ctx.textAlign = "start"
//    val fpsString = s"fps : $lastRenderTimes, ping: $latency"
//    ctx.fillText(fpsString, leftBegin, lineHeight)

    ctx.font = "20px Helvetica"
    ctx.fillStyle = ColorsSetting.fontColor2
    val fpsString = "fps : "
    val pingString = "ping: "
    ctx.fillText(fpsString, leftBegin, lineHeight)
    ctx.fillText(pingString, leftBegin + ctx.measureText(fpsString).width + 50, lineHeight)//50
    ctx.strokeStyle = "black"
    ctx.strokeText(lastRenderTimes.toString, leftBegin + ctx.measureText(fpsString).width, lineHeight)
    ctx.fillStyle = if (lastRenderTimes < 50) ColorsSetting.redColor else ColorsSetting.greenColor
    ctx.fillText(lastRenderTimes.toString, leftBegin + ctx.measureText(fpsString).width, lineHeight)
    ctx.strokeStyle = "black"
    ctx.strokeText(s"${latency}ms", leftBegin + ctx.measureText(fpsString).width + ctx.measureText(pingString).width + 60, lineHeight)
    ctx.fillStyle = if (latency <= 100) ColorsSetting.greenColor else if (latency > 100 && latency <= 200) ColorsSetting.yellowColor else ColorsSetting.redColor
    ctx.fillText(s"${latency}ms", leftBegin + ctx.measureText(fpsString).width + ctx.measureText(pingString).width + 60, lineHeight)

  }

  //PING
  private var receiveNetworkLatencyList: List[Long] = Nil
  private val PingTimes = 10
  private var latency: Long = 0L

  def receivePingPackage(p: ReceivePingPacket): Unit = {
    receiveNetworkLatencyList = (System.currentTimeMillis() - p.createTime) :: receiveNetworkLatencyList
    if (receiveNetworkLatencyList.size >= PingTimes) {
      latency = receiveNetworkLatencyList.sum / receiveNetworkLatencyList.size
      receiveNetworkLatencyList = Nil
    }
  }

}
