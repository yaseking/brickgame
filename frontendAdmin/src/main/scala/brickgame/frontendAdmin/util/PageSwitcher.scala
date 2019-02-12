package org.seekloud.brickgame.frontendAdmin.util

import org.seekloud.brickgame.frontendAdmin.MainEnter.getCurrentHash
import mhtml.{Rx, Var}
import org.scalajs.dom
import org.scalajs.dom.raw.Event

/**
  * User: Jason
  * Date: 2018/12/17
  * Time: 20:05
  */
trait PageSwitcher{

  def getCurrentHash: String = dom.window.location.hash

  protected val currentHashVar: Rx[List[String]] = PageSwitcher.currentPageHash

  def switchPageByHash(): Unit = PageSwitcher.switchPageByHash()

}

object PageSwitcher {
  private val currentPageHash: Var[List[String]] = Var(Nil)


  def hashStr2Seq(str: String): IndexedSeq[String] = {
    if (str.length == 0) {
      IndexedSeq.empty[String]
    } else if (str.startsWith("#/")) {
      val t = str.substring(2).split("/").toIndexedSeq
      if (t.nonEmpty) {
        t
      } else IndexedSeq.empty[String]
    } else {
      println("Error hash string:" + str + ". hash string must start with [#/]")
      IndexedSeq.empty[String]
    }
  }

  def switchPageByHash(): Unit = {
    println("PageSwitcher.switchPageByHash: " + getCurrentHash)
    currentPageHash := hashStr2Seq(getCurrentHash).toList
  }

  dom.window.onhashchange = { _: Event =>
    println("PageSwitcher.onhashchange: " + getCurrentHash)
    switchPageByHash()
  }

}

