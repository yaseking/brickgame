package com.neo.sk.carnie.http

import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.neo.sk.carnie.ptcl.RoomApiProtocol._
import com.neo.sk.carnie.core.RoomManager
import com.neo.sk.utils.CirceSupport
import com.neo.sk.carnie.Boot.{executor, scheduler, timeout}
import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import io.circe.generic.auto._

import scala.concurrent.Future
import akka.actor.typed.scaladsl.AskPattern._
import io.circe.Error


/**
  * Created by dry on 2018/10/18.
  **/
trait RoomApiService extends ServiceUtils with CirceSupport {

  private val log = LoggerFactory.getLogger(this.getClass)

  val roomManager: akka.actor.typed.ActorRef[RoomManager.Command]


  private val getRoomId = (path("getRoomId") & post & pathEndOrSingleSlash) {
    entity(as[Either[Error, PlayerIdInfo]]) {
      case Right(req) =>
        dealFutureResult {
          val msg: Future[Option[Int]] = roomManager ? (RoomManager.FindRoomId(req.playerId, _))
          msg.map {
            case Some(rid) => complete(RoomIdRsp(RoomIdInfo(rid)))
            case _ =>
              log.info("get roomId error")
              complete(ErrorRsp(100010, "get roomId error"))
          }
        }

      case Left(error) =>
        log.warn(s"some error: $error")
        complete(ErrorRsp(110000, "parse error"))
    }
  }

  private val getRoomPlayerList = (path("getRoomPlayerList") & post & pathEndOrSingleSlash) {
    entity(as[Either[Error, RoomIdReq]]) {
      case Right(req) =>
        val msg: Future[List[(String, String)]] = roomManager ? (RoomManager.FindPlayerList(req.roomId, _))
        dealFutureResult {
          msg.map { plist =>
            if (plist.nonEmpty)
              complete(PlayerListRsp(PlayerInfo(plist)))
            else {
              log.info("get playerlist error")
              complete(ErrorRsp(100001, "get playerlist error"))
            }
          }
        }

      case Left(error) =>
        log.warn(s"some error: $error")
        complete(ErrorRsp(110000, "parse error"))
    }
  }

  private val getRoomList = (path("getRoomList") & get & pathEndOrSingleSlash) {
    dealFutureResult {
      val msg: Future[List[Int]] = roomManager ? (RoomManager.FindAllRoom(_))
      msg.map {
        allroom =>
          if (allroom.nonEmpty)
            complete(RoomListRsp(RoomListInfo(allroom)))
          else {
            log.info("get all room error")
            complete(ErrorRsp(100000, "get all room error"))
          }

      }
    }
  }


  val roomApiRoutes: Route = pathPrefix("roomApi") {
    getRoomId ~ getRoomPlayerList ~ getRoomList
  }


}
