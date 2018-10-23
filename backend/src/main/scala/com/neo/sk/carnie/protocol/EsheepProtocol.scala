package com.neo.sk.carnie.protocol

object EsheepProtocol {

  case class GetToken(
                        gameId: Long,
                        gsKey: String
                        )

  case class SendDataReq(
                          appId: String,
                          sn: String,
                          timestamp: String,
                          nonce: String,
                          signature: String,
                          data: String
                        )

  case class TokenData(
                      token: String,
                      expireTime: Long
                      )

  case class GetTokenRsp(
                        data: TokenData,
                        errCode: Int,
                        msg: String
                        )

  case class VerifyAccCode(
                          gameId: Long,
                          accessCode: String
                          )

  case class PlayerInfo(
                       playerId: String,
                       nickName: String
                       )

  case class VerifyAccCodeRsp(
                             data: PlayerInfo,
                             errCode: Int,
                             msg: String
                             )

  case class PlayerRecord(
                         playerId: String,
                         gameId: Long,
                         nickname: String,
                         killing: Int,
                         killed: Int,
                         score: Int,
                         gameExtent: String,
                         startTime: Long,
                         endTime: Long
                         )

  case class InputRecord(
                        playerRecord: PlayerRecord
                        )

}
