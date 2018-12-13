package com.neo.sk.carnie.utils

import com.neo.sk.carnie.protocol.Protocol4Agent._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import com.neo.sk.carnie.Boot.executor
import com.neo.sk.carnie.common.AppSetting

object Api4GameAgent extends HttpUtil {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  def getLoginRspFromEs() = {
    val methodName = "GET"
    val url = "http://" + AppSetting.esheepDomain + "/esheep/api/gameAgent/login"
    log.info("start getLoginRspFromEs.")
    getRequestSend(methodName, url, Nil).map {
      case Right(r) =>
        decode[LoginRsp](r) match {
          case Right(rsp) =>
            log.info("end getLoginRspFromEs.")
            Right(UrlData(rsp.data.wsUrl, rsp.data.scanUrl.replaceFirst("data:image/png;base64,", "")))
          case Left(e) =>
            Left(s"error:$e")
        }
      case Left(e) =>
        log.info(s"$e")
        Left("error")
    }
  }

  //fixme 尚未添加bot玩家
  def linkGameAgent(gameId: Long, playerId: String, token: String) = {
    val data = LinkGameAgentReq(gameId, playerId).asJson.noSpaces
    val url = "http://" + AppSetting.esheepDomain + "/esheep/api/gameAgent/joinGame?token=" + token

    postJsonRequestSend("post", url, Nil, data).map {
      case Right(jsonStr) =>
        decode[LinkGameAgentRsp](jsonStr) match {
          case Right(res) =>
            Right(LinkGameAgentData(res.data.accessCode, res.data.gsPrimaryInfo))
          case Left(le) =>
            Left("decode error: " + le)
        }
      case Left(erStr) =>
        Left("get return error:" + erStr)
    }

  }

  def loginByMail(email:String, pwd:String) = {
    val data = LoginByMailReq(email, pwd).asJson.noSpaces
    val url = "http://" + AppSetting.esheepDomain + "/esheep/rambler/login"

    postJsonRequestSend(s"post:$url", url, Nil, data).map {
      case Right(jsonStr) =>
        decode[ESheepUserInfoRsp](jsonStr) match {
          case Right(res) =>
            if(res.errCode==0)
              Right(res)
            else {
              log.debug(s"loginByMail error: ${res.errCode} msg: ${res.msg}")
              Left(s"errCode: ${res.errCode}")
            }
          case Left(le) =>
            Left("decode error: " + le)
        }
      case Left(erStr) =>
        Left("get return error:" + erStr)
    }
  }

  def main(args: Array[String]): Unit = {
//    getLoginRspFromEs()
    loginByMail("test@neotel.com.cn","test123")
  }

}
