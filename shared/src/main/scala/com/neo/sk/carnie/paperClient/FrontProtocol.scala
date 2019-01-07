package com.neo.sk.carnie.paperClient

/**
  * Created by dry on 2018/11/26.
  **/
object FrontProtocol {

  sealed trait DrawFunction

  case object DrawGameWait extends DrawFunction

  case class DrawGameWin(winnerName: String, winData: Data4Draw) extends DrawFunction

  case object DrawGameOff extends DrawFunction

  case class DrawBaseGame(data: Data4Draw) extends DrawFunction

  case class DrawGameDie(killerName: Option[String]) extends DrawFunction

  case class Data4Draw(
                             frameCount: Int,
                             snakes: List[SkDt],
                             bodyDetails: List[BodyInfo4Draw],
                             fieldDetails: List[Protocol.FieldByColumn]
                           )

  case class BodyInfo4Draw(
                           uid: String,
                           turn: List[Protocol.Point4Trans]
                         )
}
