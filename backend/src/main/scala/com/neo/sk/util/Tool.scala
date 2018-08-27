package com.neo.sk.util

import scala.collection.mutable

/**
  * Created by dry on 2018/8/27.
  **/
object Tool {

  def findContinuous(points: Array[Int]) = {
    var target = List.empty[(Int, Int)]
    if (points.max - points.min == (points.length + 1)) { //仅存在一段
      target = List((points.min, points.max))
    } else { //分段寻找
      var start = points(0)
      (0 until points.length - 1).foreach { i =>
        if (points(i) + 1 == points(i + 1)) {
          if (i + 1 == points.length - 1) {
            target = target ::: List((start, points(i + 1)))
          }
        } else {
          target = target ::: List((start, points(i)))
          start = points(i + 1)
          if (i + 1 == points.length - 1) {
            target = target ::: List((start, start))
          }
        }
      }
    }
    target
  }

  def main(args: Array[String]): Unit = {
    val points = Array(8, 9, 10)
    findContinuous(points)
  }

}
