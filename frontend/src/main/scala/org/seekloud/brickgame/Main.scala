package org.seekloud.brickgame

import org.seekloud.brickgame.paperClient.WebSocketProtocol._
import org.seekloud.brickgame.paperClient._
import mhtml.{Cancelable, Rx, mount}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.xml.Elem
/**
  * Created by haoshuhan on 2018/11/2.
  */

@JSExportTopLevel("paperClient.Main")
object Main extends js.JSApp {
  var currentPage:Elem = <div></div>

  val guestName = List("安琪拉","白起","不知火舞","妲己","狄仁杰","典韦","韩信","老夫子","刘邦",
    "刘禅","鲁班七号","墨子","孙膑","孙尚香","孙悟空","项羽","亚瑟","周瑜",
    "庄周","蔡文姬","甄姬","廉颇","程咬金","后羿","扁鹊","钟无艳","小乔","王昭君",
    "虞姬","李元芳","张飞","刘备","牛魔王","张良","兰陵王","露娜","貂蝉","达摩","曹操",
    "芈月","荆轲","高渐离","钟馗","花木兰","关羽","李白","宫本武藏","吕布","嬴政",
    "娜可露露","武则天","赵云","姜子牙","哪吒","诸葛亮","黄忠","大乔","东皇太一",
    "庞统","干将莫邪","鬼谷子","女娲","SnowWhite","Cinderella","Aurora","Ariel","Belle","Jasmine",
    "Pocahontas","Mulan","Tiana","Rapunzel","Merida","Anna","Elsa","Moana")

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
        currentPage = JoinPage.render
//        currentPage = CanvasPage.render
        show()
//        NetGameHolder.init("test")

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
