package com.neo.sk.carnie.scalajs

import com.neo.sk.carnie.paper.Protocol.ReceivePingPacket
import org.scalajs.dom.CanvasRenderingContext2D

/**
  * Created by dry on 2018/9/3.
  **/
object PerformanceTool {
  //FPS
  private var lastRenderTime = System.currentTimeMillis()
  private var lastRenderTimes = 0
  private var renderTimes = 0

  private def addFps() = {
    val time = System.currentTimeMillis()
    renderTimes += 1
    if (time - lastRenderTime > 1000) {
      lastRenderTime = time
      lastRenderTimes = renderTimes
      renderTimes = 0
    }
  }

  def renderFps(ctx: CanvasRenderingContext2D, leftBegin: Int, lineHeight: Int) = {
    addFps()
    ctx.font = "14px Helvetica"
    ctx.textAlign = "start"
    val fpsString = s"fps : $lastRenderTimes, ping: $latency"
    ctx.fillText(fpsString, leftBegin, lineHeight)

  }

  //PING
  private var receiveNetworkLatencyList: List[Long] = Nil
  private val PingTimes = 10
  private var latency: Long = 0L

  def receivePingPackage(p: ReceivePingPacket): Unit = {
    receiveNetworkLatencyList = (System.currentTimeMillis() - p.createTime) :: receiveNetworkLatencyList
    if (receiveNetworkLatencyList.size < PingTimes) {
      //      Shortcut.scheduleOnce(() => startPing(), 10)
    } else {
      latency = receiveNetworkLatencyList.sum / receiveNetworkLatencyList.size
      receiveNetworkLatencyList = Nil
    }
  }

}
