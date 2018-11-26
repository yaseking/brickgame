package com.neo.sk.carnie.paperClient

/**
  * Created by dry on 2018/11/26.
  **/
object FrontProtocol {

  sealed trait DrawFunction

  case object DrawGameWait extends DrawFunction

  case object DrawGameWin extends DrawFunction

  case object DrawGameOff extends DrawFunction

  case class DrawBaseGame(data: Protocol.Data4TotalSync) extends DrawFunction

  case object DrawGameDie extends DrawFunction

}
