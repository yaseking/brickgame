package com.neo.sk.carnie.http

import akka.http.scaladsl.server.Directives.{getFromResource, path}
import akka.http.scaladsl.server.Route
import com.neo.sk.utils.{CirceSupport, SessionSupport}
import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import com.neo.sk.carnie.ptcl.RoomApiProtocol._

import scala.collection.mutable
//import akka.http.scaladsl.model.{ContentTypes, DateTime, HttpEntity}
import akka.http.scaladsl.server.{Directive1, Route}
import com.neo.sk.carnie.core.RoomManager
import io.circe.generic.auto._
import io.circe.Error
import com.neo.sk.carnie.http.SessionBase.{AdminInfo, AdminSession}
import com.neo.sk.carnie.ptcl.RoomApiProtocol
import com.neo.sk.carnie.ptcl.RoomApiProtocol.RoomListInfo4Client
import akka.actor.typed.scaladsl.AskPattern._

import scala.concurrent.Future
import com.neo.sk.carnie.ptcl.AdminPtcl
import com.neo.sk.carnie.Boot.{executor, scheduler}
import com.neo.sk.carnie.common.AppSettings
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
  with RoomApiService
{

  private val log = LoggerFactory.getLogger(this.getClass)


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

  private val getRoomList = (path("getRoomList") & get & pathEndOrSingleSlash){
    adminAuth{ _ =>
      dealFutureResult {
        val msg: Future[List[String]] = roomManager ? RoomManager.FindAllRoom4Client
        msg.map {
          allRoom =>
            log.info("prepare to return roomList.")
            complete(RoomApiProtocol.RoomListRsp4Client(RoomListInfo4Client(allRoom)))
        }
      }
    }
  }

  private val getRoomPlayerList = (path("getRoomPlayerList") & get & pathEndOrSingleSlash) {
//    val msg: Future[List[PlayerIdName]] = roomManager ? (RoomManager.FindPlayerList(req.roomId, _))
    val msg: Future[mutable.HashMap[Int, (Int, Option[String], mutable.HashSet[(String, String)])]] = roomManager ? RoomManager.ReturnRoomMap
    dealFutureResult{
      msg.map { r =>
        val plist =r.map(i => i._1 -> i._2._3)
        if(plist.nonEmpty){
//          log.info(s"${req.roomId}$plist")
          complete(RoomMapRsp(RoomMapInfo(plist)))
        }
        else{
          log.info("get player list error: this room doesn't exist")
          complete(ErrorRsp(100001, "get player list error: this room doesn't exist"))
        }

      }
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

  val adminRoutes: Route = pathPrefix("admin"){
    pathEndOrSingleSlash {
      getFromResource("html/admin.html")
    } ~
    login ~ logout ~ getRoomList ~ getRoomPlayerList
  }
}
