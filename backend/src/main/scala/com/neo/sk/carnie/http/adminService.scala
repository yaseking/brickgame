package com.neo.sk.carnie.http

import akka.http.scaladsl.server.Directives.{getFromResource, path}
import akka.http.scaladsl.server.Route
import com.neo.sk.utils.{CirceSupport, SessionSupport}
import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentTypes, DateTime, HttpEntity}
import akka.http.scaladsl.server.{Directive1, Route}
import io.circe.generic.auto._
import io.circe.Error
import com.neo.sk.carnie.http.SessionBase.{AdminInfo, AdminSession}
//import akka.stream.scaladsl.{FileIO, Sink, Source}
//import akka.actor.typed.scaladsl.AskPattern._
//import akka.http.scaladsl.model.HttpEntity
import com.neo.sk.carnie.ptcl.AdminPtcl._
//import com.neo.sk.carnie.core.RoomManager
//import com.neo.sk.carnie.Boot.{executor, scheduler}
import com.neo.sk.carnie.common.AppSettings
//import com.neo.sk.carnie.models.dao.RecordDAO
//import scala.concurrent.Future
//import scala.util.{Failure, Success}

/**
  * User: Jason
  * Date: 2018/12/17
  * Time: 16:29
  */
trait adminService extends ServiceUtils
  with CirceSupport
  with SessionBase
  with SessionSupport
{

  private val log = LoggerFactory.getLogger(this.getClass)


  private val login = (path("login") & post & pathEndOrSingleSlash){
    entity(as[Either[Error, LoginReq]]) {
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

  val adminRoutes: Route = pathPrefix("admin"){
    pathEndOrSingleSlash {
      getFromResource("html/admin.html")
    } ~
    login
  }
}
