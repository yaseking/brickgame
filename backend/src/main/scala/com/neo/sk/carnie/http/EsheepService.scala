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
//                    println(s"rsp: $rsp")
                    if(rsp.playerId == playerId){
                      getFromResource("html/netSnake.html")
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

  private val watchGame = (path("watchGame") & get) {
    log.info("success to render watchGame page.")
    getFromResource("html/netSnake.html")
//    parameter(
//      'roomId.as[String],
//      'playerId.as[String].?,
//      'accessCode.as[String]
//    ) {
//      case (roomId, playerId, accessCode) =>
//        val gameId = AppSettings.esheepGameId
//        dealFutureResult{
//          val msg: Future[String] = tokenActor ? AskForToken
//          msg.map {token =>
//            dealFutureResult{
//              EsheepClient.verifyAccessCode(gameId, accessCode, token).map {
//                case Right(data) =>
//                  log.info(s"userId: ${data.playerId}, nickname: ${data.nickname}")
//                  getFromResource("html/netSnake.html")
//                case Left(e) =>
//                  log.error(s"watchGame error. fail to verifyAccessCode err: $e")
//                  complete(ErrorRsp(120003, "Some errors happened in parse verifyAccessCode."))
//              }
//            }
//          }
//        }
//    }
  }

  private val watchRecord = (path("watchRecord") & get & pathEndOrSingleSlash) {
    parameter(
      'recordId.as[Long],
      'playerId.as[String], //
      'frame.as[Int],
      'accessCode.as[String]
    ) {
      case (recordId, playerId, frame, accessCode) =>
//        val gameId = AppSettings.esheepGameId
//        dealFutureResult{
//          val msg: Future[String] = tokenActor ? AskForToken
//          msg.map {token =>
//            dealFutureResult{
//              EsheepClient.verifyAccessCode(gameId, accessCode, token).map {
//                case Right(rsp) =>
//                  //                    println(s"rsp: $rsp")
////                  if(rsp.playerId == playerId){
                    getFromResource("html/netSnake.html")
////                  } else {
////                    complete(ErrorRsp(120004, "Some errors happened in verifyAccessCode."))
////                  }
//                case Left(e) =>
//                  log.error(s"playGame error. fail to verifyAccessCode err: $e")
////                  getFromResource("html/netSnake.html")
//                  complete(ErrorRsp(120005, "Some errors happened in parse verifyAccessCode."))
//              }
//            }
//          }
//        }
    }
  }

  val esheepRoute: Route = playGame ~ watchRecord ~ watchGame

}
