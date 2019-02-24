package org.seekloud.brickgame.http

import java.text.SimpleDateFormat

import akka.http.scaladsl.server.Directives.{getFromResource, path}
import org.seekloud.utils.{CirceSupport, SessionSupport}
import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import org.seekloud.brickgame.models.{ActiveUserRepo, PlayerInfoRepo}
import org.seekloud.brickgame.ptcl.AdminPtcl.{UserInfo, UserInfoRsp}
import org.seekloud.brickgame.ptcl.RoomApiProtocol._

import scala.collection.mutable
//import akka.http.scaladsl.model.{ContentTypes, DateTime, HttpEntity}
import akka.http.scaladsl.server.{Directive1, Route}
import org.seekloud.brickgame.core.RoomManager
import io.circe.generic.auto._
import io.circe.Error
import org.seekloud.brickgame.http.SessionBase.{AdminInfo, AdminSession}
import akka.actor.typed.scaladsl.AskPattern._

import scala.concurrent.Future
import org.seekloud.brickgame.ptcl.AdminPtcl
import org.seekloud.brickgame.Boot.{executor, scheduler, timeout}
import org.seekloud.brickgame.common.AppSettings
//import scala.util.{Failure, Success}

/**
  * User: Jason
  * Date: 2018/12/17
  * Time: 16:29
  */
trait AdminService extends ServiceUtils
  with CirceSupport
  with SessionBase
  with SessionSupport
{

  private val log = LoggerFactory.getLogger(this.getClass)

  val roomManager: akka.actor.typed.ActorRef[RoomManager.Command]

  private val login = (path("login") & post & pathEndOrSingleSlash){
    entity(as[Either[Error, AdminPtcl.LoginReq]]) {
      case Right(req) =>
        if(AppSettings.adminId == req.id && AppSettings.adminPassWord == req.passWord){
          setSession(
            AdminSession(AdminInfo(req.id, req.passWord), System.currentTimeMillis()).toAdminSessionMap
          ) { ctx =>
            ctx.complete(SuccessRsp())
          }
        }
        else {
          log.info("Administrator's account or password is wrong!")
          complete(ErrorRsp(140001, "Some errors happened in adminLogin!"))
        }
      case Left(e) =>
        complete(ErrorRsp(140001, s"Some errors happened in adminLogin：$e"))
    }
  }

  private val getRoomPlayerList = (path("getRoomPlayerList") & get & pathEndOrSingleSlash) {
    adminAuth {
      _ =>
        val msg: Future[(Int, Int)] = roomManager ? RoomManager.ReturnRoomMap
        dealFutureResult {
          msg.map { plist =>
            log.info(s"roomInfo: $plist")
            complete(RoomMapRsp(plist._1, plist._2))
          }
        }
    }
  }

  private val getActiveUserInfo = (path("getActiveUserInfo") & get & pathEndOrSingleSlash) {
    adminAuth {
      _ =>
        val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val date = dateFormat.format(System.currentTimeMillis())
        val start = date.take(11) + "00:00:00"
        val end = date.take(11) + "23:59:59"
        dealFutureResult(
          ActiveUserRepo.getRecords.map { p =>
            complete(AdminPtcl.PlayerAmountRsp(p.length,
              p.count(i => dateFormat.format(i.leaveTime) >= start && dateFormat.format(i.leaveTime) <= end)))
          }
        )
    }
  }

  private val logout = path("logout") {
    adminAuth {
      _ =>
        invalidateSession {
          complete(SuccessRsp())
        }
    }
  }

  private val forbidPlayer = (path("forbidPlayer") & post & pathEndOrSingleSlash) {
    adminAuth {
      _ =>
        entity(as[Either[Error, AdminPtcl.PlayerReq]]) {
          case Right(req) =>
            PlayerInfoRepo.forbidPlayer(req.name)
            complete(SuccessRsp())
          case Left(e) =>
            complete(ErrorRsp(140020, s"Some errors:$e happened in forbidPlayer."))
        }
    }
  }

  private val enablePlayer = (path("enablePlayer") & post & pathEndOrSingleSlash) {
    adminAuth {
      _ =>
        entity(as[Either[Error, AdminPtcl.PlayerReq]]) {
          case Right(req) =>
            PlayerInfoRepo.enablePlayer(req.name)
            complete(SuccessRsp())
          case Left(e) =>
            complete(ErrorRsp(140020, s"Some errors:$e happened in enablePlayer."))
        }
    }
  }

  private val showPlayerInfo = (path("showPlayerInfo") & get & pathEndOrSingleSlash) {
    adminAuth {
      _ =>
        dealFutureResult(
          PlayerInfoRepo.getAllPlayers.map {players =>
            complete(
              UserInfoRsp(players.map{p=>UserInfo(p.username, p.state)})
            )
          }
        )
    }
  }

  val adminRoutes: Route = pathPrefix("admin"){
    pathEndOrSingleSlash {
      getFromResource("html/admin.html")
    } ~
    login ~ logout ~ getRoomPlayerList ~ forbidPlayer ~ enablePlayer ~ showPlayerInfo ~ getActiveUserInfo
  }
}
