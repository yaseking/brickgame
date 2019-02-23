package org.seekloud.brickgame

import org.seekloud.brickgame.paperClient.WebSocketProtocol._
import org.seekloud.brickgame.paperClient._
import mhtml.{Cancelable, Rx, mount}
import org.scalajs.dom
//import org.seekloud.brickgame.util.PageSwitcher

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
    val info = dom.window.location.href.split("brickgame/")(1)
//    println(s"hello ${info(0)}....")
    println(s"info: $info")
    info match {
      case "playGame" =>
        println("playGame ...")
//        currentPage = JoinPage.render
        currentPage = CanvasPage.render
        show()
        NetGameHolder.init("test")

      case _ =>
        println(s"not playGame ${info(0)}")
        currentPage = CanvasPage.render
        show()
    }
  }

//  def join(nickname: String): Unit = {
//    currentPage = new CanvasPage().render
//    show()
//    new NetGameHolder(nickname).init()
//  }

  def refreshPage(newPage: Elem): Cancelable = {
    println("refreshPage!!!")
    currentPage = newPage
    show()
  }

//  def switchToLogin(): Unit = {
//    dom.document.getElementById("joinPage").setAttribute("display","none")
//    dom.document.getElementById("joinPage").setAttribute("hidden","hidden")
//    currentPage = new LoginPage(switchToR).render
//    show()
//  }


//  def refreshPage(newPage: Elem): Cancelable = {
//    println("refreshPage!!!")
//    currentPage = newPage
//    show()
//  }

  def show(): Cancelable = {
    mount(dom.document.body, currentPage)
  }

}

//object Main {
//
//
//  def main(args: Array[String]): Unit ={
//    println("11111")
//    MainEnter.show()
//  }
//
//}
//
//object MainEnter extends PageSwitcher {
////  var nickname = ""
//
//  val currentPage: Rx[Elem] = currentHashVar.map {
//    case Nil => println("lala");new JoinPage().render
//    case "JoinPage" :: Nil => new JoinPage().render
//    case "CanvasPage" :: Nil => new CanvasPage().render
//    case _ => <div>Error Page</div>
//  }
//
////  def join(name: String): Unit = {
////    nickname = name
////  }
//
////  val header: Rx[Elem] = currentHashVar.map {
////    //    case Nil => Header.render
////    case "LoginPage" :: Nil => <div></div>
////    //    case "GPUOrder" :: Nil => Header.render
////    //    case "rentRecord" :: Nil => Header.render
////    //    case "rulePage" :: Nil => Header.render
////    case _ => Header.render
////  }
//
//  def show(): Cancelable = {
//    val page =
//      <div>
//        {currentPage}
//      </div>
//    switchPageByHash()
//    mount(dom.document.body, page)
//  }
//
//}
