package com.neo.sk.carnie

import com.neo.sk.carnie.page.{GamePage, PageSwitcher}
import mhtml.{Cancelable, Rx, mount}
import org.scalajs.dom

import scala.xml.Elem
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/**
  * Created by dry on 2018/10/11.
  */

@JSExportTopLevel("scalajs.Main")
object Main {

  @JSExport
  def run(): Unit = {
    MainEnter.show()
  }

}

object MainEnter extends PageSwitcher {

  val currentPage: Rx[Elem] = currentHashVar.map {
    case Nil => GamePage.render
    case _ => <div>Error Page</div>
  }

  val header: Rx[Elem] = currentHashVar.map {
    case _ => <div></div>
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

