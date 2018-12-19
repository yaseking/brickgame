package com.neo.sk.carnie.frontendAdmin

import mhtml.{Cancelable, Rx, mount}
import org.scalajs.dom
import scala.xml.Elem
import com.neo.sk.carnie.frontendAdmin.util.PageSwitcher
import com.neo.sk.carnie.frontendAdmin.pages._
import com.neo.sk.carnie.frontendAdmin.components.Header
import com.neo.sk.carnie.frontendAdmin.styles._

/**
  * User: Jason
  * Date: 2018/12/17
  * Time: 17:27
  */
object Main {

  import scalacss.DevDefaults._
  styles.Demo2Styles.addToDocument()

  def main(args: Array[String]): Unit ={
    MainEnter.show()
  }

}

object MainEnter extends PageSwitcher {

  val currentPage: Rx[Elem] = currentHashVar.map {
    case Nil => LoginPage.render
    case "LoginPage" :: Nil => LoginPage.render
    case "View"  :: Nil => ViewPage.render
    case "CurrentDataPage"  :: Nil => CurrentDataPage.render
    case _ => <div>Error Page</div>
  }

  val header: Rx[Elem] = currentHashVar.map {
    //    case Nil => Header.render
    case "LoginPage" :: Nil => <div></div>
    case _ => Header.render
  }

  def show(): Cancelable = {
    val page =
      <div>
        {header}{currentPage}
      </div>
    switchPageByHash()
    mount(dom.document.body, page)
  }

}