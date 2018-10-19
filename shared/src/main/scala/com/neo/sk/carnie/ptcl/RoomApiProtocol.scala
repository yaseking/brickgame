package com.neo.sk.carnie.ptcl

/**
  * User: Jason
  * Date: 2018/10/19
  * Time: 14:37
  */
object RoomApiProtocol {
  trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  case class PlayerIdInfo(
    playerId: String
  )
  case class PlayerInfo(
    playerList:List[(Long,String)]
  )
  case class RoomIdInfo(
    roomId: Int
  )

  case class RoomIdRsp(
    data: RoomIdInfo,
    errCode: Int = 0,
    msg: String = "OK"
  )
  case class PlayerListRsp(
    data: PlayerInfo,
    errCode: Int = 0,
    msg: String = "OK"
  )
  case class RoomListRsp(
    data: RoomListInfo,
    errCode: Int = 0,
    msg: String = "OK"
  )

  case class RoomListInfo(
    roomList:List[Int]
  )

  final case class ErrorRsp(
    errCode: Int,
    msg: String
  ) extends CommonRsp

  final case class SuccessRsp(
    errCode: Int = 0,
    msg: String = "ok"
  ) extends CommonRsp



}
