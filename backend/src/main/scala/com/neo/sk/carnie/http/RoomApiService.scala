package com.neo.sk.carnie.http

import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Route
import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import scala.concurrent.duration._
import akka.http.scaladsl.unmarshalling

import scala.concurrent.Future
import akka.actor.typed.scaladsl.AskPattern._
import io.circe.generic.auto._
import com.neo.sk.carnie.ptcl.RoomApiProtocol._
import com.neo.sk.carnie.core.RoomManager
import com.neo.sk.utils.CirceSupport
import com.neo.sk.carnie.Boot.{scheduler,executor,timeout}


/**
  * Created by dry on 2018/10/18.
  **/
trait RoomApiService extends  ServiceUtils with CirceSupport {

  private val log = LoggerFactory.getLogger(this.getClass)

  val roomManager: akka.actor.typed.ActorRef[RoomManager.Command]


  private val getRoomId = (path("getRoomId") & post & pathEndOrSingleSlash) {
    entity(as[Either[ErrorRsp, PlayerIdInfo]]){
      case Right(req) =>
      dealFutureResult {
        val msg:Future[Int] = roomManager ? (RoomManager.FindRoomId(req.playerId, _))
        msg.map{
          case rid => complete(RoomIdRsp(RoomIdInfo(rid)))
          case _ =>
            log.info("get roomId error")
            complete(ErrorRsp(100010,"get roomId error"))
        }
      }
      case Left(error) =>
        log.warn(s"some error: $error")
        complete(ErrorRsp(110000,"parse error"))
    }
  }

  private val getRoomPlayerList = (path("getRoomPlayerList") & post & pathEndOrSingleSlash) {
    entity(as[Either[ErrorRsp, RoomIdInfo]]){
      case Right(req) =>
      val msg:Future[List[(Long,String)]] = roomManager ? (RoomManager.FindPlayerList(req.roomId, _))
      dealFutureResult{
        msg.map{
          case plist => complete(PlayerListRsp(PlayerInfo(plist)))
          case _ =>
            log.info("get playerList error")
            complete(ErrorRsp(100011,"get playerList error"))
        }
      }
      case Left(error) =>
        log.warn(s"some error: $error")
        complete(ErrorRsp(110000,"parse error"))
    }
  }

  private val getRoomList = (path("getRoomList") & get & pathEndOrSingleSlash) {
    dealFutureResult{
      val msg:Future[List[Int]] = roomManager ? (RoomManager.FindAllRoom(_))
      msg.map{
        case allroom => complete(RoomListRsp(RoomListInfo(allroom)))
        case _ =>
          log.info("get all room error")
          complete(ErrorRsp(100000,"get all room error"))
      }
    }
  }





  val roomApiRoutes: Route = pathPrefix("roomApi") {
    getRoomId ~ getRoomPlayerList ~ getRoomList
  }


}
