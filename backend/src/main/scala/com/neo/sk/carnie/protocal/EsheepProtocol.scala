package com.neo.sk.carnie.protocal

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
                      gsToken: String,
                      expireTime: Int
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
                       playerId: Long,
                       nickName: String
                       )

  case class PlayerInfoData(
                           playerInfo: PlayerInfo
                           )

  case class VerifyAccCodeRsp(
                             data: PlayerInfoData,
                             errCode: Int,
                             msg: String
                             )

}
