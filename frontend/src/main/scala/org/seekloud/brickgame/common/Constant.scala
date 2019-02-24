package org.seekloud.brickgame.common

import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.html.Image

/**
  * Created by dry on 2018/9/3.
  **/
object Constant {

  val watchKeys = Set(
    KeyCode.Space,
    KeyCode.Left,
    KeyCode.Right
  )

  object ColorsSetting {
    val backgroundColor = "#F5F5F5"
    val blackColor = "#000000"
    val gameNameColor = "#5BC48C"
    val fontColor = "#E0EEFD"
    val defaultColor = "#000080"
    val borderColor = "#696969"
    val mapColor = "#C0C0C0"
//    val gradeColor = "#3358FF"
    val redColor = "#FF0000"
    val greenColor = "#00FF00"
    val yellowColor = "#FFFF00"
    val darkYellowColor = "#EBEB68"
    val backgroundColor2 = "#333333"
    val darkGreyColor = "#A9A9A9"
  }

  def shortMax(a: Short, b: Short): Short ={
    if(a > b) a else b
  }

  val img0 = dom.document.getElementById("666Img").asInstanceOf[Image]
  val img1 = dom.document.getElementById("cuteImg").asInstanceOf[Image]
  val img2 = dom.document.getElementById("happyImg").asInstanceOf[Image]
  val img3 = dom.document.getElementById("helloImg").asInstanceOf[Image]
  val img4 = dom.document.getElementById("poorImg").asInstanceOf[Image]
  val img5 = dom.document.getElementById("sickImg").asInstanceOf[Image]


  val imgMap = Map(
    0 -> img0,
    1 -> img1,
    2 -> img2,
    3 -> img3,
    4 -> img4,
    5 -> img5,
  )
}
