package com.neo.sk.carnie.paperClient

/**
  * Created by dry on 2018/11/26.
  **/
object FrontProtocol {

  sealed trait DrawFunction

  case object DrawGameWait extends DrawFunction

  case class DrawGameWin(winnerName: String, winData: Protocol.Data4TotalSync) extends DrawFunction

  case object DrawGameOff extends DrawFunction

  case class DrawBaseGame(data: Protocol.Data4TotalSync) extends DrawFunction

  case class DrawGameDie(killerName: Option[String]) extends DrawFunction

}
