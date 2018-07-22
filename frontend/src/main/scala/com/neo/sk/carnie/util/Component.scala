package com.neo.sk.carnie.util

import scala.language.implicitConversions
import scala.xml.Elem

/**
  * User: Taoz
  * Date: 3/29/2018
  * Time: 1:59 PM
  */
trait Component {

  def render: Elem



}

object Component {
  implicit def component2Element(comp: Component): Elem = comp.render


}
