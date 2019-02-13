package org.seekloud.brickgame.paperClient

import java.awt.event.KeyEvent

import org.seekloud.brickgame.paperClient.Protocol.{NewFieldInfo, Point4Trans}
import scala.collection.mutable

/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 10:13 PM
  */
class GridOnClient() extends Grid {

  override def debug(msg: String): Unit = println(msg)

  override def info(msg: String): Unit = println(msg)

  var fieldDrawMap = mutable.Map.empty[Int, mutable.Map[String, mutable.Map[Short, List[Short]]]] //(frameCount, List[Field4Draw])

  def initSyncGridData2(data: Protocol.Data4TotalSync2): Unit = {
    println("start to init SyncGridData")

    frameCount = data.frameCount
    players = data.players

    actionMap = actionMap.filterKeys(_ >= (data.frameCount - maxDelayed))
    historyStateMap += frameCount -> players
  }




  def setGridInGivenFrame(frame: Int): Unit = { //直接跳转至某个帧号
    frameCount = frame
    players = historyStateMap(frame)
  }

  def findRecallFrame(receiveFame: Int, oldRecallFrame: Option[Int]): Option[Int] = {
    if (historyStateMap.get(receiveFame).nonEmpty) { //回溯
      oldRecallFrame match {
        case Some(oldFrame) => Some(Math.min(receiveFame, oldFrame))
        case None => Some(receiveFame)
      }
    } else {
      Some(-1)
    }
  }


}
