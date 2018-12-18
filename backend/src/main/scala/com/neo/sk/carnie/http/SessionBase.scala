package com.neo.sk.carnie.http

/**
  * User: Jason
  * Date: 2018/12/18
  * Time: 10:38
  */

import com.neo.sk.carnie.common.AppSettings
import com.neo.sk.carnie.ptcl.ErrorRsp
import com.neo.sk.utils.{CirceSupport, SessionSupport}
import org.slf4j.LoggerFactory

/**
  * Created by dry on 2018/4/28.
  **/
object SessionBase extends CirceSupport{

  val SessionTypeKey = "STKey"
  private val logger = LoggerFactory.getLogger(this.getClass)

  object AdminSessionKey {
    val SESSION_TYPE = "titans_adminSession"
    val aid = "titans_aid"
    val name = "titans_name"
    val loginTime = "titans_loginTime"
  }

  object UserSessionKey {
    val SESSION_TYPE = "titans_userSession"
    val uid = "titans_uid"
    val userName = "titans_userName"
    val timestamp = "titans_timestamp"
  }

  case class AdminInfo(
    aid: String,
    name: String
  )

  case class AdminSession(
    adminInfo: AdminInfo,
    time: Long
  ) {
    def toAdminSessionMap: Map[String, String] = {
      Map(
        SessionTypeKey -> AdminSessionKey.SESSION_TYPE,
        AdminSessionKey.aid -> adminInfo.aid.toString,
        AdminSessionKey.name -> adminInfo.name,
        AdminSessionKey.loginTime -> time.toString
      )
    }
  }

  case class UserInfo(
    uid: Long,
    name: String
  )

  case class UserSession(
    userInfo: UserInfo,
    time: Long
  ) {
    def toUserSessionMap: Map[String, String] = {
      Map(
        SessionTypeKey -> UserSessionKey.SESSION_TYPE,
        UserSessionKey.uid -> userInfo.uid.toString,
        UserSessionKey.userName -> userInfo.name,
        UserSessionKey.timestamp -> time.toString
      )
    }
  }

}

trait
SessionBase extends SessionSupport{

  import akka.http.scaladsl.server
  import akka.http.scaladsl.server.Directives.extractRequestContext
  import SessionBase._
  import akka.http.scaladsl.model.StatusCodes
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.server.{Directive, Directive1, RequestContext}
  import akka.http.scaladsl.server.directives.BasicDirectives
  import io.circe.parser._
  import io.circe.generic.auto._

  override val sessionEncoder = SessionSupport.PlaySessionEncoder
  override val sessionConfig = AppSettings.sessionConfig
  private val sessionTimeout = 24 * 60 * 60 * 1000
  private val log = LoggerFactory.getLogger(this.getClass)

  implicit class SessionTransformer(sessionMap: Map[String, String]) {
    def toAdminSession:Option[AdminSession] = {
      //      log.debug(s"toAdminSession: change map to session, ${sessionMap.mkString(",")}")
      try {
        if (sessionMap.get(SessionTypeKey).exists(_.equals(AdminSessionKey.SESSION_TYPE))) {
          if(sessionMap(AdminSessionKey.loginTime).toLong - System.currentTimeMillis() > sessionTimeout){
            None
          }else {
            Some(AdminSession(
              AdminInfo(
                sessionMap(AdminSessionKey.aid),
                sessionMap(AdminSessionKey.name)
              ),
              sessionMap(AdminSessionKey.loginTime).toLong
            ))
          }
        } else {
          log.debug("no session type in the session")
          None
        }
      } catch {
        case e: Exception =>
          e.printStackTrace()
          log.warn(s"toAdminSession: ${e.getMessage}")
          None
      }
    }

    def toUserSession:Option[UserSession] = {
      //      log.debug(s"toAdminSession: change map to session, ${sessionMap.mkString(",")}")
      try {
        if (sessionMap.get(SessionTypeKey).exists(_.equals(UserSessionKey.SESSION_TYPE))) {
          if(sessionMap(UserSessionKey.timestamp).toLong - System.currentTimeMillis() > sessionTimeout){
            None
          } else {
            Some(UserSession(
              UserInfo(
                sessionMap(UserSessionKey.uid).toLong,
                sessionMap(UserSessionKey.userName)
              ),
              sessionMap(UserSessionKey.timestamp).toLong
            ))
          }
        } else {
          log.debug("no session type in the session")
          None
        }
      } catch {
        case e: Exception =>
          e.printStackTrace()
          log.warn(s"toUserSession: ${e.getMessage}")
          None
      }
    }
  }

  protected val optionalAdminSession: Directive1[Option[AdminSession]] = optionalSession.flatMap {
    case Right(sessionMap) => BasicDirectives.provide(sessionMap.toAdminSession)
    case Left(error) =>
      logger.debug(error)
      BasicDirectives.provide(None)
  }

  protected val optionalUserSession: Directive1[Option[UserSession]] = optionalSession.flatMap {
    case Right(sessionMap) => BasicDirectives.provide(sessionMap.toUserSession)
    case Left(error) =>
      logger.debug(error)
      BasicDirectives.provide(None)
  }

  private def loggingAction: Directive[Tuple1[RequestContext]] = extractRequestContext.map { ctx =>
    logger.info(s"Access uri: ${ctx.request.uri} from ip ${ctx.request.uri.authority.host.address}.")
    ctx
  }

  def noSessionError(message:String = "no session") = ErrorRsp(1000102,s"$message")

  def adminAuth(f: AdminInfo => server.Route) = loggingAction { ctx =>
    optionalAdminSession {
      case Some(session) =>
        f(session.adminInfo)

      case None =>
        complete(noSessionError())
    }
  }

  def userAuth(f: UserInfo => server.Route) = loggingAction { ctx =>
    optionalUserSession {
      case Some(session) =>
        f(session.userInfo)

      case None =>
        redirect("/titans/user/page#/LoginPage", StatusCodes.SeeOther)
    }
  }

  def titansAuth(f: String => server.Route) = loggingAction { ctx =>
    optionalUserSession {
      case Some(session) =>
        f(session.userInfo.uid.toString)

      case None =>
        optionalAdminSession {
          case Some(_) =>
            f("ok")

          case None =>
            complete(noSessionError())
        }
    }
  }

}