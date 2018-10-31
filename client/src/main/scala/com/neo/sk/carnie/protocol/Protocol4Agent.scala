package com.neo.sk.carnie.protocol

object Protocol4Agent {

  case class UrlData(
                      wsUrl: String,
                      scanUrl: String
                    )

  case class LoginRsp(
                     data: UrlData,
                     errCode: Int,
                     msg: String
                     )

  case class GameServerInfo(
                           ip: String,
                           port: Int,
                           domain: String
                           )

  case class LinkGameAgentData(
                              accessCode: String,
                              gsPrimaryInfo: GameServerInfo
                              )

  case class LinkGameAgentRsp(
                             data: LinkGameAgentData,
                             errCode: Int,
                             msg: String
                             )

  case class LinkGameAgentReq(
                             gameId: Long,
                             playerId: String
                             )

  case class WsRsp(
                    Ws4AgentRsp: Ws4AgentResponse
                  )

  case class Ws4AgentResponse(
                  data: WsData,
                  errCode: Int,
                  msg: String
                  )

  case class WsData(
                     userId: Long,
                     nickname: String,
                     token: String,
                     tokenExpireTime: Int
                   )

}
