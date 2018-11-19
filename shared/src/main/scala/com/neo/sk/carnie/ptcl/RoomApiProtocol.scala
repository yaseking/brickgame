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

  case class RecordReq(
                            recordId: Long
  )
  case class RecordListReq(
                            lastRecordId: Long,
                            count: Int
  )

  case class RecordByTimeReq(
                            startTime: Long,
                            endTime: Long,
                            lastRecordId: Long,
                            count: Int
  )

  case class RecordByPlayerReq(
                          playerId: String,
                          lastRecordId: Long,
                          count: Int
  )
  case class RecordListRsp(
                          data:List[recordInfo],
                          errCode: Int = 0,
                          msg: String = "ok"
  )

  case class recordInfo(
                          recordId: Long,
                          roomId: Long,
                          startTime: Long,
                          endTime: Long,
                          userCounts: Int,
                          userList: Seq[(String,String)]
  )

  case class RecordInfoReq(
                           recordId: Long,
                           playerId: String //正在观看玩家的ID
                           )

  case class RecordFrameInfo(
                            frame: Int,
                            frameNum: Int
                            )

  case class RecordPlayerList(
                               totalFrame: Int,
                               playerList: List[RecordPlayerInfo]
                             )

  case class ExistTime(
                           startFrame: Long,
                           endFrame: Long
                         )

  case class RecordPlayerInfo(
                             playerId: String,
                             nickname: String,
                             existTime: List[ExistTime]
                             )

  case class PlayerIdInfo(
                           playerId: String
                         )

  case class PlayerInfo(
                         playerList: List[PlayerIdName]
                       )

  case class PlayerIdName(
                        playerId: String,
                        nickname: String
  )

  case class RoomIdInfo(
                         roomId: Int
                       )

  case class RoomIdReq(
                        roomId: Int
                      )
  case class AllRoomReq(
                        data: String
  )

  case class RoomIdRsp(
                        data: RoomIdInfo,
                        errCode: Int = 0,
                        msg: String = "ok"
                      )

  case class PlayerListRsp(
                            data: PlayerInfo,
                            errCode: Int = 0,
                            msg: String = "ok"
                          )

  case class RoomListRsp(
                          data: RoomListInfo,
                          errCode: Int = 0,
                          msg: String = "ok"
                        )

  case class RoomListInfo(
                           roomList: List[Int]
                         )

  case class RecordFrameRsp(
                        data: RecordFrameInfo,
                        errCode: Int = 0,
                        msg: String = "ok"
                      ) extends CommonRsp

  case class RecordPlayerInfoRsp(
                                data: RecordPlayerList,
                                errCode: Int = 0,
                                msg: String = "ok"
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
