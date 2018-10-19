package com.neo.sk.carnie.http

import com.neo.sk.carnie.utils.CirceSupport
import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.neo.sk.carnie.common._
import com.neo.sk.carnie.utils.EsheepClient
import com.neo.sk.carnie.Boot.executor

trait EsheepService extends ServiceUtils with CirceSupport with PlayerService{

  private val log = LoggerFactory.getLogger(this.getClass)

  private val expireTime = 10*60*1000l

  private val playGame = (path("playGame") & get & pathEndOrSingleSlash) {
    parameter(
      'playerId.as[Long],
      'playerName.as[String],
      'roomId.as[Int].?,
      'accessCode.as[String],
      'appId.as[String],
      'secureKey.as[String],
    ) {
      case (playerId, playerName, roomId, accessCode, appId, secureKey) =>
        if(AppSettings.appSecureMap.contains(appId) && (AppSettings.appSecureMap(appId) == secureKey)){
          val gameId = AppSettings.esheepGameId
          dealFutureResult{
            EsheepClient.verifyAccessCode(gameId, accessCode).map {
              case Right(rsp) =>
                if(rsp.playerId == playerId && rsp.nickName == playerName){
                  //join Game
                  webSocketChatFlow(playerName)
                  complete()
                } else {
                  complete()
                }
              case Left(e) =>
                log.error(s"playGame error. fail to verifyAccessCode err: $e")
                complete()
            }
          }
        } else {
          complete()
        }
    }
  }

//  private val inputBatRecord = (path("inputBatRecord") & post & pathEndOrSingleSlash) {
//
//  }

  val esheepRoute: Route = pathPrefix("game") {
    playGame
  }

}
