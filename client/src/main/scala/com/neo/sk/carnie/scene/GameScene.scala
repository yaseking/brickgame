package com.neo.sk.carnie.scene

import com.neo.sk.carnie.paperClient.{Protocol, Score}
import javafx.scene.canvas.Canvas
import javafx.scene.{Group, Scene}
import javafx.scene.input.KeyCode

/**
  * Created by dry on 2018/10/29.
  **/

object GameScene{
  trait GameSceneListener {
    def onKeyPressed(e: KeyCode): Unit
  }
}

class GameScene {

  import GameScene._

  var gameSceneListener: GameSceneListener = _

  val viewWidth = 1200
  val viewHeight = 600
  val rankWidth = 250
  val rankHeight = 300
  val group = new Group
  val backgroundCanvas = new Canvas(viewWidth, viewHeight)
  val viewCanvas = new Canvas(viewWidth, viewHeight)
  val rankCanvas = new Canvas(rankWidth, rankHeight)
  rankCanvas.setStyle("z-index = 100")
  viewCanvas.setStyle("z-index = 120")

  val scene = new Scene(group)
  group.getChildren.add(backgroundCanvas)
  group.getChildren.add(viewCanvas)
  group.getChildren.add(rankCanvas)

  val view = new GameViewCanvas(viewCanvas)
  val background = new BackgroundCanvas(backgroundCanvas)
  val rank = new RankCanvas(rankCanvas)

  viewCanvas.requestFocus()
  viewCanvas.setOnKeyPressed(event => gameSceneListener.onKeyPressed(event.getCode))

  background.drawCache()

  def draw(myId: String, data: Protocol.Data4TotalSync, currentRank: List[Score]): Unit = {
//    view.drawSnake(myId, data)
//    background.drawMap(myId, data)
//    rank.drawInfo(myId, data, currentRank)
  }

  def setGameSceneListener(listener: GameSceneListener) {
    gameSceneListener = listener
  }

}
