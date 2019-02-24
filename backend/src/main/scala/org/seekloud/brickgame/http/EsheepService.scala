package org.seekloud.brickgame.http

import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Route}
import akka.http.scaladsl.model.headers.{CacheDirective, `Cache-Control`}
import io.circe.Error
import org.seekloud.brickgame.ptcl.AdminPtcl
import io.circe.generic.auto._
import org.seekloud.brickgame.models.{PlayerInfo, PlayerInfoRepo}
import org.seekloud.brickgame.ptcl.RoomApiProtocol.{ErrorRsp, SuccessRsp}
import org.seekloud.utils.{CirceSupport, SessionSupport}
import org.seekloud.brickgame.Boot.{executor, scheduler}

import scala.concurrent.Future
trait EsheepService extends ServiceUtils with CirceSupport with SessionSupport{

  private val log = LoggerFactory.getLogger(this.getClass)

  private val cacheSeconds = 24 * 60 * 60


//  private val playGame = (path("playGame") & get & pathEndOrSingleSlash) {
//    parameter(
//      'playerId.as[String],
//      'playerName.as[String],
//      'roomId.as[Int].?,
//      'accessCode.as[String]
//    ) {
//      case (playerId, playerName, roomId, accessCode) =>
//        val gameId = AppSettings.esheepGameId
//        dealFutureResult{
//          val msg: Future[String] = tokenActor ? AskForToken
//          msg.map {token =>
//              dealFutureResult{
//                EsheepClient.verifyAccessCode(gameId, accessCode, token).map {
//                  case Right(rsp) =>
//                    if(rsp.playerId == playerId){
//                      addCacheControlHeadersWithFilter(`public`, `max-age`(cacheSeconds)) {
//                        getFromResource("html/index.html")
//                      }
//                    } else {
//                      complete(ErrorRsp(120001, "Some errors happened in verifyAccessCode."))
//                    }
//                  case Left(e) =>
//                    log.error(s"playGame error. fail to verifyAccessCode err: $e")
//                    complete(ErrorRsp(120002, "Some errors happened in parse verifyAccessCode."))
//                }
//              }
//          }
//        }
//    }
//  }

  private val login = (path("login") & post & pathEndOrSingleSlash) { //todo
    entity(as[Either[Error, AdminPtcl.LoginReq]]) {
      case Right(req) =>
        dealFutureResult(
          PlayerInfoRepo.getPlayerByName(req.id).map {
            case Some(user) =>
              if(user.password==req.passWord) {
                if(user.state)
                  complete(SuccessRsp())
                else
                  complete(ErrorRsp(140010, "Forbidden account."))
              } else {
                complete(ErrorRsp(140005, "Wrong password."))
              }

            case None =>
              complete(ErrorRsp(140015, "Wrong username."))
          }
        )

      case Left(e) =>
        complete(ErrorRsp(140001, s"Some errors happened in userLogin：$e"))
    }
  }

  private val register = (path("register") & post & pathEndOrSingleSlash) {
    entity(as[Either[Error, AdminPtcl.LoginReq]]) {
      case Right(req) =>
        dealFutureResult(
          PlayerInfoRepo.getPlayerByName(req.id).map {
            case Some(_) =>
              complete(ErrorRsp(140020, "Username has existed."))

            case None =>
              PlayerInfoRepo.updatePlayerInfo(PlayerInfo(-1, req.id, req.passWord, true))
              complete(SuccessRsp())
          }
        )

      case Left(e) =>
        complete(ErrorRsp(140001, s"Some errors happened in userRegister：$e"))
    }
  }

  //fixme: for test
  private val playGame = (path("playGame") & get & pathEndOrSingleSlash) {
    getFromResource("html/index.html")
  }

//  private val watchGame = (path("watchGame") & get & pathEndOrSingleSlash) {
//    log.info("success to render watchGame page.")
//    getFromResource("html/index.html")
//  }

//  private val getBotList = (path("getBotList") & post & pathEndOrSingleSlash) {
//    entity(as[Either[Error,BotListReq]]) {
//      case Right(v) =>
//        val msg: Future[String] = tokenActor ? AskForToken
//        dealFutureResult{
//          msg.map{token =>
//            dealFutureResult{
//              EsheepClient.getBotList(v.userId, v.lastId, 10, token).map {
//                case Right(r) =>
//                  complete(r)
//                case Left(e) =>
//                  log.debug(s"Some errors happened in getBotList: $e")
//                  complete(ErrorRsp(120003, "Some errors happened in getBotList."))
//              }
//            }
//          }
//        }
//      case Left(e) =>
//        log.debug(s"getBotList errs: $e")
//        complete(ErrorRsp(120004, s"getBotList errs: $e"))
//    }
//  }

  //只使用强制缓存,去除协商缓存的字段
  def addCacheControlHeadersWithFilter(first: CacheDirective, more: CacheDirective*): Directive0 = {
    mapResponseHeaders { headers =>
      `Cache-Control`(first, more: _*) +: headers.filterNot(h => h.name() == "Last-Modified" || h.name() == "ETag")
    }
  }

  val esheepRoute: Route = playGame ~ login ~ register

}
