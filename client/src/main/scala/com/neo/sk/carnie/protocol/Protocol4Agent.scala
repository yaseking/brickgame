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
}
