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

  sealed trait WsData

  case class Ws4AgentRsp(
                          data: UserInfo,
                          errCode: Int,
                          msg: String
                        ) extends WsData

  case object HeartBeat extends WsData

  case class UserInfo(
                       userId: Long,
                       nickname: String,
                       token: String,
                       tokenExpireTime: Long
                     )

}
