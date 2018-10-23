package com.neo.sk.carnie.http

import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.neo.sk.carnie.ptcl.RoomApiProtocol._
import com.neo.sk.carnie.core.RoomManager
import com.neo.sk.utils.CirceSupport
import com.neo.sk.carnie.Boot.{executor, scheduler, timeout}
import com.neo.sk.carnie.models.dao.RecordDAO
import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import io.circe.generic.auto._

import scala.concurrent.Future
import akka.actor.typed.scaladsl.AskPattern._
import io.circe.Error

import scala.collection.mutable


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
          val msg: Future[Option[(Int, mutable.HashSet[(String, String)])]] = roomManager ? (RoomManager.FindRoomId(req.playerId, _))
          msg.map {
            case Some(rid) => complete(RoomIdRsp(RoomIdInfo(rid._1)))
            case _ =>
              log.info("this player doesn't exist")
              complete(ErrorRsp(100010, "get roomId error:this player doesn't exist"))
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
        val msg: Future[Option[List[(String, String)]]] = roomManager ? (RoomManager.FindPlayerList(req.roomId, _))
        dealFutureResult {
          msg.map {
            case Some(plist) =>
              complete(PlayerListRsp(PlayerInfo(plist.map(p => PlayerIdName(p._1,p._2)))))
            case None =>
              log.info("get player list error")
              complete(ErrorRsp(100001, "get player list error"))
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

  private val getRecordList = (path("getRecordList") & post & pathEndOrSingleSlash) {
    entity(as[Either[Error, RecordListReq]]) {
      case Right(req) =>
        dealFutureResult{
          RecordDAO.getRecordList(req.lastRecordId,req.count).map{recordL =>
            complete(RecordListRsp(records(recordL.toList.map(_._1).distinct.map{ r =>
              val userList = recordL.map(i => i._2).distinct.filter(_.recordId == r.recordId).map(_.userId)
              recordInfo(r.recordId,r.roomId,r.startTime,r.endTime,userList.length,userList)
            })))
          }
        }

      case Left(error) =>
        log.warn(s"some error: $error")
        complete(ErrorRsp(110000, "parse error"))
    }
  }

  private val getRecordListByTime = (path("getRecordListByTime") & post & pathEndOrSingleSlash) {
    entity(as[Either[Error, RecordByTimeReq]]) {
      case Right(req) =>
        dealFutureResult{
          RecordDAO.getRecordListByTime(req.startTime,req.endTime,req.lastRecordId,req.count).map{recordL =>
            complete(RecordListRsp(records(recordL.toList.map{ r =>
              val userList = recordL.map(i => i._2).filter(_.recordId == r._1.recordId).map(_.userId)
              recordInfo(r._1.recordId,r._1.roomId,r._1.startTime,r._1.endTime,userList.length,userList)
            })))
          }
        }
      case Left(error) =>
        log.warn(s"some error: $error")
        complete(ErrorRsp(110000, "parse error"))
    }
  }

  private val getRecordListByPlayer = (path("getRecordListByPlayer") & post & pathEndOrSingleSlash) {
    entity(as[Either[Error, RecordByPlayerReq]]) {
      case Right(req) =>
        dealFutureResult{
          RecordDAO.getRecordListByPlayer(req.playerId,req.lastRecordId,req.count).map{recordL =>
            complete(RecordListRsp(records(recordL.toList.map{ r =>
              val userList = recordL.map(i => i._2).filter(_.recordId == r._1.recordId).map(_.userId)
              recordInfo(r._1.recordId,r._1.roomId,r._1.startTime,r._1.endTime,userList.length,userList)
            })))
          }
        }
      case Left(error) =>
        log.warn(s"some error: $error")
        complete(ErrorRsp(110000, "parse error"))
    }
  }



  val roomApiRoutes: Route = pathPrefix("roomApi") {
    getRoomId ~ getRoomPlayerList ~ getRoomList ~ getRecordList ~ getRecordListByTime ~ getRecordListByPlayer
  }


}
