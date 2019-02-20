package org.seekloud.brickgame

import org.seekloud.brickgame.paperClient.WebSocketProtocol._
import org.seekloud.brickgame.paperClient._
import mhtml.{Cancelable, mount}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.Random
import scala.xml.Elem
/**
  * Created by haoshuhan on 2018/11/2.
  */

@JSExportTopLevel("paperClient.Main")
object Main extends js.JSApp {
  var currentPage:Elem = <div></div>
  def main(): Unit = {
    selectPage()
  }

  def selectPage():Unit = {
    val info = dom.window.location.href.split("carnie/")(1)
//    println(s"hello ${info(0)}....")
    println(s"info: $info")
    info match {
      case "playGame" =>
        println("playGame ...")
        currentPage = new CanvasPage().render
        show()
        new NetGameHolder("test").init()

      case _ =>
        println(s"not playGame ${info(0)}")
        currentPage = new CanvasPage().render
        show()
    }
  }

  def login(nickname: String): Unit = {
    println("111111")
    currentPage = new CanvasPage().render
    println("222222")
    show()
    println("333333")
    new NetGameHolder(nickname).init()
    println("444444")

  }

//  def refreshPage(newPage: Elem): Cancelable = {
//    println("refreshPage!!!")
//    currentPage = newPage
//    show()
//  }

  def show(): Cancelable = {
    mount(dom.document.body, currentPage)
  }

}
