package com.neo.sk.carnie.http

import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.neo.sk.carnie.common._
import com.neo.sk.carnie.ptcl._
import io.circe.generic.auto._
import com.neo.sk.carnie.Boot.{executor, scheduler, timeout}
import com.neo.sk.carnie.core.TokenActor
import com.neo.sk.carnie.core.TokenActor.AskForToken
import akka.actor.typed.scaladsl.AskPattern._
import io.circe.Error
import com.neo.sk.carnie.ptcl.EsheepPtcl._
import com.neo.sk.utils.{CirceSupport, EsheepClient}

import scala.concurrent.Future

trait EsheepService extends ServiceUtils with CirceSupport {

  private val log = LoggerFactory.getLogger(this.getClass)

  val tokenActor: akka.actor.typed.ActorRef[TokenActor.Command]

//  private val expireTime = 10*60*1000l

//  private val playGame = (path("playGame") & get & pathEndOrSingleSlash) {
//    parameter(
//      'playerId.as[Long],
//      'playerName.as[String],
//      'roomId.as[Int].?,
//      'accessCode.as[String],
//      'appId.as[String],
//      'secureKey.as[String]
//    ) {
//      case (playerId, playerName, roomId, accessCode, appId, secureKey) =>
//        if(AppSettings.appSecureMap.contains(appId) && (AppSettings.appSecureMap(appId) == secureKey)){
////          println("lalala")
//          val gameId = AppSettings.esheepGameId
//          dealFutureResult{
//            EsheepClient.verifyAccessCode(gameId, accessCode).map {
//              case Right(rsp) =>
//                if(rsp.playerId == playerId && rsp.nickName == playerName){
//                  //join Game
//                  complete(SuccessRsp())
//                } else {
//                  complete(ErrorRsp(120001, "Some errors happened in verifyAccessCode."))
//                }
//              case Left(e) =>
//                log.error(s"playGame error. fail to verifyAccessCode err: $e")
//                webSocketChatFlow(playerName)
//                complete(ErrorRsp(120002, "Some errors happened in parse verifyAccessCode."))
//            }
//          }
//        } else {
//          complete(ErrorRsp(120003, "Wrong player applies to playGame."))
//        }
//    }
//  }

  private val playGame = (path("playGame") & post & pathEndOrSingleSlash) {
    entity(as[Either[Error, PlayerMsg]]) {
      case Right(req) =>
        val playerMsg = req.playerMsg
        val appId = if (playerMsg.contains("appId")) playerMsg("appId") else ""
        val secureKey = if (playerMsg.contains("secureKey")) playerMsg("secureKey") else ""
        val accessCode = if (playerMsg.contains("accessCode")) playerMsg("accessCode") else ""
        val playerId = if (playerMsg.contains("playerId")) playerMsg("playerId") else "unKnown"
        val playerName = if (playerMsg.contains("playerName")) playerMsg("playerName") else ""
        if (AppSettings.appSecureMap.contains(appId) && (AppSettings.appSecureMap(appId) == secureKey)) {
          dealFutureResult {
            val msg: Future[String] = tokenActor ? AskForToken
            msg.map { token =>
              val gameId = AppSettings.esheepGameId
              dealFutureResult {
                println("start verifyAccessCode!")
                EsheepClient.verifyAccessCode(gameId, accessCode, token).map {
                  case Right(rsp) =>
                    if (rsp.playerId == playerId && rsp.nickName == playerName) {
                      //join Game
                      complete(SuccessRsp())
                    } else {
                      println("end verifyAccessCode!")
                      complete(ErrorRsp(120001, "Some errors happened in verifyAccessCode."))
                    }
                  case Left(e) =>
                    log.error(s"playGame error. fail to verifyAccessCode err: $e")
                    complete(ErrorRsp(120002, "Some errors happened in parse verifyAccessCode."))
                }
              }
            }
          }

        } else {
          complete(ErrorRsp(120003, "Wrong player applies to playGame."))
        }
      case Left(_) =>
        complete(ErrorRsp(120004, "Wrong player applies to playGame."))
    }
  }

//  private val inputBatRecord = (path("inputBatRecord") & post & pathEndOrSingleSlash) {
//
//  }

  val esheepRoute: Route = pathPrefix("esheep") {
    playGame
  }

}
