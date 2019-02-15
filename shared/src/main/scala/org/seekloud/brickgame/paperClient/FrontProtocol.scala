package org.seekloud.brickgame.paperClient

import org.seekloud.brickgame.paperClient.Protocol.Data4TotalSync

/**
  * Created by dry on 2018/11/26.
  **/
object FrontProtocol {

  sealed trait DrawFunction

  case object DrawGameWait extends DrawFunction

  case class DrawGameWin(winnerName: String, winData: WinData4Draw) extends DrawFunction

  case object DrawGameOff extends DrawFunction

  case object DrawBaseGame extends DrawFunction

  case object DrawGameDie extends DrawFunction

  case class Data4Draw(
                             frameCount: Int,
                             snakes: List[SkDt],
                             bodyDetails: List[BodyInfo4Draw],
                             fieldDetails: List[Field4Draw]
                           )

  case class WinData4Draw(
                        frameCount: Int,
                        snakes: List[SkDt],
                        fieldDetails: List[Field4Draw]
                      )

  case class BodyInfo4Draw(
                           uid: String,
                           turn: List[Protocol.Point4Trans]
                         )

  case class Field4Draw(
                            uid: String,
                            scanField: List[Scan4Draw]
                          )

  case class Scan4Draw(
                        x: Short,
                        y: List[(Short, Short)]
                       )
}
