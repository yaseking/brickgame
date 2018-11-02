package com.neo.sk.utils

import com.neo.sk.carnie.common.AppSettings
import com.neo.sk.carnie.protocol.EsheepProtocol._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import com.neo.sk.carnie.Boot.executor
import com.neo.sk.carnie.Boot.{tokenActor, scheduler, timeout}
import com.neo.sk.carnie.core.TokenActor.AskForToken
import akka.actor.typed.scaladsl.AskPattern._
import org.slf4j.LoggerFactory
import com.neo.sk.carnie.ptcl._

import scala.concurrent.Future

object EsheepClient extends HttpUtil with CirceSupport {

  private val log = LoggerFactory.getLogger(this.getClass)

  val domain = AppSettings.esheepDomain
  private val baseUrl = AppSettings.esheepProtocol + "://" + domain + "/" + AppSettings.esheepUrl

  def getTokenRequest(gameId: Long, gsKey: String) = {
    val esheepUrl = baseUrl + "/api/gameServer/gsKey2Token"
    val sendData = GetToken(gameId, gsKey).asJson.noSpaces

    postJsonRequestSend(s"postUrl: $esheepUrl", esheepUrl, Nil, sendData).map {
      case Right(str) =>
        decode[GetTokenRsp](str) match {
          case Right(rsp) =>
            if(rsp.errCode==0) {
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

  def verifyAccessCode(gameId: Long, accessCode: String, token: String) = {
//    println(s"got token: $token")
    val esheepUrl = baseUrl + s"/api/gameServer/verifyAccessCode?token=$token"
    val sendData = VerifyAccCode(gameId, accessCode).asJson.noSpaces

    log.info("Start verifyAccessCode!")
    postJsonRequestSend(s"postUrl: $esheepUrl", esheepUrl, Nil, sendData).map {
      case Right(str) =>
//        println(s"str in verifyAccessCode: $str")
        decode[VerifyAccCodeRsp](str) match {
          case Right(rsp) =>
            if(rsp.errCode==0){
              Right(rsp.data)
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
                      endTime: Long,
                      token: String
                    ) = {
    println("start inputBatRecord!")
//    val token = KeyData.token
    val gameId = AppSettings.esheepGameId
    val esheepUrl = baseUrl + s"/api/gameServer/addPlayerRecord?token=$token"
    val sendData = InputRecord(PlayerRecord(playerId, gameId, nickname, killing, killed, score, gameExtent, startTime, endTime)).asJson.noSpaces

    postJsonRequestSend(s"postUrl: $esheepUrl", esheepUrl, Nil, sendData).map {
      case Right(str) =>
        decode[ErrorRsp](str) match {
          case Right(rsp) =>
            if(rsp.errCode==0) {
              println("finish inputBatRecord!")
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
    import com.neo.sk.utils.SecureUtil._
    val appId = AppSettings.esheepGameId.toString
    val sn = appId + System.currentTimeMillis().toString
    val data = RoomApiProtocol.RecordByPlayerReq("user10013",200,5).asJson.noSpaces
    val (timestamp, nonce, signature) = SecureUtil.generateSignatureParameters(List(appId, sn, data), AppSettings.esheepGsKey)
    val params = PostEnvelope(appId, sn, timestamp, nonce, data,signature).asJson.noSpaces

//    val a = genPostEnvelope(AppSettings.esheepAppId,sn,data,AppSettings.esheepSecureKey)
    println(params)
//    val gsKey = AppSettings.esheepGsKey
//    val msg: Future[String] = tokenActor ? AskForToken
//    msg.map {token =>
//      println(s"token: $token")
//      verifyAccessCode(gameId, "1234456asdf", token)
//    }
    //    getTokenRequest(gameId, gsKey)
//    inputBatRecord("1", "asdtest", 1, 1, 10, "", 1L, 2L)
  }

}
