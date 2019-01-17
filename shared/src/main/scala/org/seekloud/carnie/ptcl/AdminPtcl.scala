package org.seekloud.carnie.ptcl

/**
  * User: Jason
  * Date: 2018/12/17
  * Time: 17:22
  */
object AdminPtcl {

  trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  final case class ErrorRsp(
    errCode: Int,
    msg: String
  ) extends CommonRsp

  final case class SuccessRsp(
    errCode: Int = 0,
    msg: String = "ok"
  ) extends CommonRsp

  case class LoginReq(
    id: String,
    passWord: String
  )

  case class PlayerRecord(
    id: Long,
    playerId: String,
    nickname: String,
    killing: Int,
    killed: Int,
    score: Double,
    startTime: Long,
    endTime: Long
  )

  case class PlayerAmountRsp(
                              playerAmount: Int,
                              playerAmountToday: Int,
                              errCode: Int = 0,
                              msg: String = "ok"
                         ) extends CommonRsp

  case class PlayerByTimeAmountRsp(
                              playerAmount: Int,
                              errCode: Int = 0,
                              msg: String = "ok"
                            ) extends CommonRsp

  case class PlayerRecordRsp(
    data: List[PlayerRecord],
    playerAmount: Int,
    errCode: Int = 0,
    msg: String = "ok"
  ) extends CommonRsp

  case class PageReq(
    page: Int
  )

  case class PageTimeReq(
    page: Int,
    time: String
  )

  case class TimeReq(
                          time: String
                        )

}
