package com.neo.sk.carnie.paperClient

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:40 PM
  */
object Protocol {

  sealed trait GameMessage

  case class GridDataSync(
                           frameCount: Long,
                           snakes: List[SkDt],
                           bodyDetails: List[Bd],
                           fieldDetails: List[Fd],
                           borderDetails: List[Bord],
                           killHistory: List[Kill]
                         ) extends GameMessage

  case class Data4Sync(
                           frameCount: Long,
                           snakes: List[SkDt],
                           bodyDetails: List[(Long, (scala.List[Point4Trans], scala.List[(Point4Trans, Long)]))],
                           fieldDetails: List[(Long, List[(Int, List[(Int, Int)])])],
                           blankDetails: List[(Int, List[(Int, Int)])],
                           killHistory: List[Kill]
                         ) extends GameMessage

  case class Data4TotalSync(
                           frameCount: Long,
                           snakes: List[SkDt],
                           bodyDetails: List[(Long, (scala.List[Point4Trans], scala.List[(Point4Trans, Long)]))],
                           fieldDetails: List[(Long, List[(Int, List[(Int, Int)])])],
                           borderDetails: List[Bord],
                           killHistory: List[Kill]
                         ) extends GameMessage

  case class Point4Trans(x:Int, y: Int)

  case class TextMsg(
                      msg: String
                    ) extends GameMessage

  case class Id(id: Long) extends GameMessage

  case class NewSnakeJoined(id: Long, name: String) extends GameMessage

  case class SnakeAction(id: Long, keyCode: Int, frame: Long, actionId: Int) extends GameMessage

  case class SnakeLeft(id: Long, name: String) extends GameMessage

  case class Ranks(currentRank: List[Score], historyRank: List[Score]) extends GameMessage

  case class SomeOneWin(winnerName: String) extends GameMessage

  case class ReceivePingPacket(createTime: Long) extends GameMessage



  sealed trait UserAction

  case class Key(id: Long, keyCode: Int, frameCount: Long, actionId: Int) extends UserAction

  case class TextInfo(msg: String) extends UserAction

  case class SendPingPacket(id: Long, createTime: Long) extends UserAction

  val frameRate = 150

}
