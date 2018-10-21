package com.neo.sk.carnie.utils

import com.neo.sk.carnie.common.{AppSettings, KeyData}
import com.neo.sk.carnie.protocol.EsheepProtocol._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import com.neo.sk.carnie.Boot.executor
import org.slf4j.LoggerFactory
import com.neo.sk.carnie.ptcl._

object EsheepClient extends HttpUtil with CirceSupport {

  private val log = LoggerFactory.getLogger("Esheep")

  val domain = "10.1.29.250:30374"
  private val baseUrl = AppSettings.esheepProtocol + "://" + domain + "/" + AppSettings.esheepUrl
  private val appId = AppSettings.esheepAppId
  private val secureKey = AppSettings.esheepSecureKey

  def getTokenRequest(gameId: Long, gsKey: String) = {
    val esheepUrl = baseUrl + "/api/gameServer/gsKey2Token"
    val sn = appId + System.currentTimeMillis().toString
    val sendData = GetToken(gameId, gsKey).asJson.noSpaces
    val (timestamp, nonce, signature) = SecureUtil.generateSignatureParameters(List(appId, sn, sendData), secureKey)

    val params = SendDataReq(appId, sn, timestamp, nonce, signature, sendData).asJson.noSpaces

    postJsonRequestSend(s"postUrl: $esheepUrl", esheepUrl, Nil, params, isLog = false).map {
      case Right(str) =>
        decode[GetTokenRsp](str) match {
          case Right(rsp) =>
            if(rsp.errCode==0) {
//              println(s"before gsToken: ${KeyData.token}")
              KeyData.token = rsp.data.token
//              println(s"after gsToken: ${KeyData.token}")
              Right(rsp.data)
            } else {
              log.error(s"getTokenRequest error $esheepUrl rsp.error: ${rsp.msg}")
              Left("error")
            }
          case Left(err) =>
            log.error(s"getTokenRequest error $esheepUrl parse.error $err")
            Left("error")
        }
      case Left(e) =>
        log.error(s"getTokenRequest error $esheepUrl failed: $e")
        Left("error")
    }

  }

  def verifyAccessCode(gameId: Long, accessCode: String) = {
    val token = KeyData.token
    println(s"token: $token")
    val esheepUrl = baseUrl + s"/api/gameServer/verifyAccessCode?token=$token"
    val sn = appId + System.currentTimeMillis().toString
    val sendData = VerifyAccCode(gameId, accessCode).asJson.noSpaces
    val (timestamp, nonce, signature) = SecureUtil.generateSignatureParameters(List(appId, sn, sendData), secureKey)

    val params = SendDataReq(appId, sn, timestamp, nonce, signature, sendData).asJson.noSpaces

    postJsonRequestSend(s"postUrl: $esheepUrl", esheepUrl, Nil, params, isLog = false).map {
      case Right(str) =>
        println(s"str: $str")
        decode[VerifyAccCodeRsp](str) match {
          case Right(rsp) =>
            if(rsp.errCode==0){
              println(s"nickName: ${rsp.data.playerInfo.nickName}")
              Right(rsp.data.playerInfo)
            } else {
              log.error(s"verifyAccessCode error $esheepUrl rsp.error ${rsp.msg}")
              Left("error")
            }
          case Left(e) =>
            log.error(s"verifyAccessCode error $esheepUrl parse.error $e")
            Left("error")
        }
      case Left(e) =>
        log.error(s"verifyAccessCode error $esheepUrl failed: $e")
        Left("error")
    }
  }

  def inputBatRecord(
                      playerId: String,
                      nickname: String,
                      killing: Int,
                      killed: Int,
                      score: Int,
                      gameExtent: String = "",
                      startTime: Long,
                      endTime: Long
                    ) = {
    val token = KeyData.token
    val gameId = AppSettings.esheepGameId
    println(s"token: $token")
    val esheepUrl = baseUrl + s"/api/gameServer/addPlayerRecord?token=$token"
    val sn = appId + System.currentTimeMillis().toString
    val sendData = InputRecord(PlayerRecord(playerId, gameId, nickname, killing, killed, score, gameExtent, startTime, endTime)).asJson.noSpaces
    val (timestamp, nonce, signature) = SecureUtil.generateSignatureParameters(List(appId, sn, sendData), secureKey)

    val params = SendDataReq(appId, sn, timestamp, nonce, signature, sendData).asJson.noSpaces

    postJsonRequestSend(s"postUrl: $esheepUrl", esheepUrl, Nil, params, isLog = false).map {
      case Right(str) =>
        println(s"str: $str")
        decode[ErrorRsp](str) match {
          case Right(rsp) =>
            if(rsp.errCode==0) {
              Right(rsp)
            } else {
              log.error(s"inputBatRecord error $esheepUrl rsp.error${rsp.msg}")
              Left("error.")
            }
          case Left(e) =>
            log.error(s"inputBatRecord error $esheepUrl parse.error$e")
            Left("error.")
        }
      case Left(e) =>
        log.error(s"inputBatRecord error $esheepUrl rsp.error$e")
        Left("error.")
    }

  }

  def main(args: Array[String]): Unit = {
    val gameId = AppSettings.esheepGameId
    val gsKey = AppSettings.esheepGsKey
    getTokenRequest(gameId, gsKey)
    Thread.sleep(5000)
//    verifyAccessCode(gameId, "1234456asdf")
    inputBatRecord("1", "asdtest", 1, 1, 10, "", 1L, 2L)
  }

}
