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

  private val playGame = (path("playGame") & get & pathEndOrSingleSlash) {
    parameter(
      'playerId.as[String],
      'playerName.as[String],
      'roomId.as[Int].?,
      'accessCode.as[String]
    ) {
      case (playerId, playerName, roomId, accessCode) =>
        val gameId = AppSettings.esheepGameId
        dealFutureResult{
          val msg: Future[String] = tokenActor ? AskForToken
          msg.map {token =>
              dealFutureResult{
                EsheepClient.verifyAccessCode(gameId, accessCode, token).map {
                  case Right(rsp) =>
                    if(rsp.playerId == playerId){
                      getFromResource("html/index.html")
                    } else {
                      complete(ErrorRsp(120001, "Some errors happened in verifyAccessCode."))
                    }
                  case Left(e) =>
                    log.error(s"playGame error. fail to verifyAccessCode err: $e")
//                    getFromResource("html/netSnake.html")
                    complete(ErrorRsp(120002, "Some errors happened in parse verifyAccessCode."))
                }
              }
          }
        }
    }
  }

  //fixme: for test
//  private val playGame = (path("playGame") & get & pathEndOrSingleSlash) {
//    parameter(
//      'playerId.as[String],
//      'playerName.as[String]
//    ) {
//      case (playerId, playerName) =>
//        getFromResource("html/index.html")
//    }
//  }

  private val watchGame = (path("watchGame") & get & pathEndOrSingleSlash) {
    log.info("success to render watchGame page.")
    getFromResource("html/index.html")
  }

  private val watchRecord = (path("watchRecord") & get & pathEndOrSingleSlash) {
    parameter(
      'recordId.as[Long],
      'playerId.as[String], //
      'frame.as[Int],
      'accessCode.as[String]
    ) {
      case (recordId, playerId, frame, accessCode) =>
        log.info("success to render watchRecord page.")
        getFromResource("html/index.html")
    }
  }

  private val getBotList = (path("getBotList") & post & pathEndOrSingleSlash) {
    entity(as[Either[Error,BotListReq]]) {
      case Right(v) =>
        val msg: Future[String] = tokenActor ? AskForToken
        dealFutureResult{
          msg.map{token =>
            dealFutureResult{
              EsheepClient.getBotList(v.userId, v.lastId, 10, token).map {
                case Right(r) =>
                  complete(r)
                case Left(e) =>
                  log.debug(s"Some errors happened in getBotList: $e")
                  complete(ErrorRsp(120003, "Some errors happened in getBotList."))
              }
            }
          }
        }
      case Left(e) =>
        log.debug(s"getBotList errs: $e")
        complete(ErrorRsp(120004, s"getBotList errs: $e"))
    }
  }

  val esheepRoute: Route = playGame ~ watchRecord ~ watchGame ~ getBotList

}
