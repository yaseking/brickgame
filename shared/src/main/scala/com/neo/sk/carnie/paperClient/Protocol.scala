package com.neo.sk.carnie.paperClient

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:40 PM
  */
object Protocol {

  sealed trait GameMessage extends WsSourceProtocol.WsMsgSource


  case class GridDataSync(
                           frameCount: Long,
                           snakes: List[SkDt],
                           bodyDetails: List[Bd],
                           fieldDetails: List[Fd],
                           borderDetails: List[Bord],
                           killHistory: List[Kill]
                         ) extends GameMessage

  case class NewFieldInfo(
                           frameCount: Long,
                           fieldDetails: List[FieldByColumn]
                         ) extends GameMessage

  case class Data4TotalSync(
                             frameCount: Long,
                             snakes: List[SkDt],
                             bodyDetails: List[BodyBaseInfo],
                             fieldDetails: List[FieldByColumn],
                             killHistory: List[Kill]
                           ) extends GameMessage

  case class Point4Trans(x: Int, y: Int)

  case class BodyBaseInfo(
                           uid: String,
                           turn: TurnInfo
                         )

  case class TurnInfo(
                       turnPoint: List[Point4Trans],
                       pointOnField: List[(Point4Trans, String)]
                     ) // (拐点顺序list， 占着别人领域的身体点)

  case class FieldByColumn(
                            uid: String,
                            scanField: List[ScanByColumn]
                          )

  case class ScanByColumn(
                           y: Int,
                           x: List[(Int, Int)]
                         )

  case class TextMsg(
                      msg: String
                    ) extends GameMessage

  case class Id(id: String) extends GameMessage

  case class NewSnakeJoined(id: Long, name: String) extends GameMessage

  case class SnakeAction(id: String, keyCode: Int, frame: Long, actionId: Int) extends GameMessage

  case class SnakeLeft(id: String, name: String) extends GameMessage

  //  case class Ranks(currentRank: List[Score], historyRank: List[Score]) extends GameMessage
  case class Ranks(currentRank: List[Score]) extends GameMessage

  case class SomeOneWin(winnerName: String, data: Data4TotalSync) extends GameMessage

  case class SomeOneKilled(killedId: String, killedName: String, killerName: String) extends GameMessage

  case class ReceivePingPacket(createTime: Long) extends GameMessage

  sealed trait WsSendMsg
  case object WsSendComplete extends WsSendMsg
  case class WsSendFailed(ex: Throwable) extends WsSendMsg

  sealed trait UserAction extends WsSendMsg

  case class Key(id: String, keyCode: Int, frameCount: Long, actionId: Int) extends UserAction

  case class TextInfo(msg: String) extends UserAction

  case class SendPingPacket(id: String, createTime: Long) extends UserAction

  case class NeedToSync(id: String) extends UserAction


  //essf
  sealed trait GameEvent

  case class JoinEvent(id: String, nickName: String) extends GameEvent

  case class LeftEvent(id: String, nickName: String) extends GameEvent

  case class DirectionEvent(id: String, keyCode: Int) extends GameEvent

  case class EncloseEvent(enclosure: List[(String, List[Point])]) extends GameEvent

  case class Snapshot(grid: List[(Point, Spot)], snakes: List[(String, SkDt)], joinOrLeftEvent: List[GameEvent])

  case class GameInformation(roomId: Int, startTime: Long, index: Int, initFrame: Long)

  case class UserJoinLeft(joinFrame: Long, leftFrame: Long)

  case class UserBaseInfo(id:String, name: String)


  val frameRate = 150

}
