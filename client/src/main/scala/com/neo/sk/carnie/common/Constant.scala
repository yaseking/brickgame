package com.neo.sk.carnie.common

import java.awt.event.KeyEvent

import javafx.scene.input.KeyCode
import javafx.scene.paint.Color

/**
  * Created by dry on 2018/9/3.
  **/
object Constant {

  val watchKeys = Set(
    KeyCode.SPACE,
    KeyCode.LEFT,
    KeyCode.UP,
    KeyCode.RIGHT,
    KeyCode.DOWN
  )

  object ColorsSetting {
    val backgroundColor: Color = Color.rgb(245, 245, 245)
    val fontColor2: Color = Color.BLACK
    val gameNameColor: Color = Color.rgb(91, 196, 140)
    val defaultColor: Color = Color.rgb(0, 0, 128)
    val borderColor: Color = Color.rgb(105, 105, 105)
    val mapColor: Color = Color.rgb(192, 192, 192)
    val redColor: Color = Color.RED
    val greenColor: Color = Color.GREEN
    val yellowColor: Color = Color.YELLOW
    val dieInfoBackgroundColor: Color = Color.rgb(51, 51, 51)
    val dieInfoFontColor: Color = Color.rgb(224, 238, 253)
  }

  def keyCode2Int(c: KeyCode): Int = {
    c match {
      case KeyCode.SPACE => KeyEvent.VK_SPACE
      case KeyCode.LEFT => KeyEvent.VK_LEFT
      case KeyCode.UP => KeyEvent.VK_UP
      case KeyCode.RIGHT => KeyEvent.VK_RIGHT
      case KeyCode.DOWN => KeyEvent.VK_DOWN
      case KeyCode.F2 => KeyEvent.VK_F2
      case _ => KeyEvent.VK_F2
    }
  }

  def hex2Rgb(hex: String) = {
    val red = Constant.hexToDec(hex.slice(1,3))
    val green = hexToDec(hex.slice(3,5))
    val blue = hexToDec(hex.takeRight(2))
    Color.color(red, green, blue)
  }

  def hexToDec(hex: String): Int ={
    val hexString: String = "0123456789ABCDEF"
    var target = 0
    var base = Math.pow(16, hex.length - 1).toInt
    for(i <- 0 until hex.length){
      target = target + hexString.indexOf(hex(i)) * base
      base = base / 16
    }
    target
  }

}
