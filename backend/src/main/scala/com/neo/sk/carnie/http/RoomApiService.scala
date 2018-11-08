package com.neo.sk.carnie.http

import java.io.File
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentTypes, DateTime, HttpEntity}
import akka.http.scaladsl.server.{Directive1, Route}
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.HttpEntity
import com.neo.sk.carnie.ptcl.RoomApiProtocol._
import com.neo.sk.carnie.core.RoomManager
import com.neo.sk.utils.CirceSupport
import com.neo.sk.carnie.Boot.{scheduler, executor}
import com.neo.sk.carnie.common.AppSettings
import com.neo.sk.carnie.models.dao.RecordDAO
import com.neo.sk.carnie.core.TokenActor.AskForToken
import io.circe.generic.auto._
import org.slf4j.LoggerFactory
import scala.concurrent.Future
import io.circe.Error
import scala.collection.mutable
import com.neo.sk.utils.essf.RecallGame._


/**
  * Created by dry on 2018/10/18.
  **/
trait RoomApiService extends ServiceUtils with CirceSupport with PlayerService with EsheepService {

  private val log = LoggerFactory.getLogger("com.neo.sk.carnie.http.RoomApiService")

  val roomManager: akka.actor.typed.ActorRef[RoomManager.Command]

  private val getRoomId = (path("getRoomId") & post & pathEndOrSingleSlash) {
    dealPostReq[PlayerIdInfo] { req =>
      val msg: Future[Option[Int]] = roomManager ? (RoomManager.FindRoomId(req.playerId, _))
      msg.map {
        case Some(rid) => complete(RoomIdRsp(RoomIdInfo(rid)))
        case _ =>
          log.info("this player doesn't exist")
          complete(ErrorRsp(100010, "get roomId error:this player doesn't exist"))
      }
    }
  }

  private val getRoomPlayerList = (path("getRoomPlayerList") & post & pathEndOrSingleSlash) {
    dealPostReq[RoomIdReq] { req =>
      val msg: Future[List[(String, String)]] = roomManager ? (RoomManager.FindPlayerList(req.roomId, _))
      msg.map { plist =>
        if(plist.nonEmpty){
          log.info(s"plist:$plist")
          complete(PlayerListRsp(PlayerInfo(plist.map(p => PlayerIdName(p._1, p._2)))))
        }
         else{
          log.info("get player list error: this room doesn't exist")
          complete(ErrorRsp(100001, "get player list error: this room doesn't exist"))
        }

      }
    }
  }


  private val getRoomList = (path("getRoomList") & post & pathEndOrSingleSlash) {
    dealPostReqWithoutData {
      val msg: Future[List[Int]] = roomManager ? RoomManager.FindAllRoom
      msg.map {
        allRoom =>
          if (allRoom.nonEmpty)
            complete(RoomListRsp(RoomListInfo(allRoom)))
          else {
            log.info("get all room error,there are no rooms")
            complete(ErrorRsp(100000, "get all room error,there are no rooms"))
          }
      }
    }
  }

  private val getRecordList = (path("getRecordList") & post & pathEndOrSingleSlash) {
    dealPostReq[RecordListReq] { req =>
      RecordDAO.getRecordList(req.lastRecordId, req.count).map { recordL =>
        complete(RecordListRsp(recordL.toList.map(_._1).distinct.map { r =>
          val userList = recordL.map(i => i._2).distinct.filter(_.recordId == r.recordId).map(_.userId)
          recordInfo(r.recordId, r.roomId, r.startTime, r.endTime, userList.length, userList)
        }))
      }
    }
  }

  private val getRecordListByTime = (path("getRecordListByTime") & post & pathEndOrSingleSlash) {
    dealPostReq[RecordByTimeReq] { req =>
      RecordDAO.getRecordListByTime(req.startTime, req.endTime, req.lastRecordId, req.count).map { recordL =>
        complete(RecordListRsp(recordL.toList.map(_._1).distinct.sortWith((a, b) => a.recordId > b.recordId).take(req.count).map { r =>
          val userList = recordL.map(i => i._2).distinct.filter(_.recordId == r.recordId).map(_.userId)
          recordInfo(r.recordId, r.roomId, r.startTime, r.endTime, userList.length, userList)
        }))
      }
    }
  }

  private val getRecordListByPlayer = (path("getRecordListByPlayer") & post & pathEndOrSingleSlash) {
    dealPostReq[RecordByPlayerReq] { req =>
      RecordDAO.getRecordListByPlayer(req.playerId, req.lastRecordId, req.count).map { recordL =>
        complete(RecordListRsp(recordL.toList.filter(_._2.userId == req.playerId).map(_._1).distinct.sortWith((a, b) => a.recordId > b.recordId).take(req.count).map { r =>
          val userList = recordL.map(i => i._2).distinct.filter(_.recordId == r.recordId).map(_.userId)
          recordInfo(r.recordId, r.roomId, r.startTime, r.endTime, userList.length, userList)
        }))
      }
    }
  }

  private val downloadRecord = (path("downloadRecord") & post & pathEndOrSingleSlash) {
    parameter(
      'token.as[String]
    ) {
      token =>
        dealFutureResult {
          val msg: Future[String] = tokenActor ? AskForToken
          msg.map {
            nowToken =>
              if (token == nowToken) {
                entity(as[Either[Error, RecordReq]]) {
                  case Right(req) =>
                    dealFutureResult {
                      RecordDAO.getRecordPath(req.recordId).map {
                        case Some(file) =>
                          val f = new File(file)
                          println(s"getFile $file")
                          if (f.exists()) {
                            val responseEntity = HttpEntity(
                              ContentTypes.`application/octet-stream`,
                              f.length,
                              FileIO.fromPath(f.toPath, chunkSize = 262144))
                            complete(responseEntity)
                          }
                          else
                            complete(ErrorRsp(1001011, "record doesn't exist."))
                        case None =>
                          complete(ErrorRsp(1001011, "record id failed."))
                      }
                    }

                  case Left(error) =>
                    log.warn(s"some error: $error")
                    complete(ErrorRsp(0, ""))
                }
              }
              else {
                log.warn("token error")
                complete(ErrorRsp(1100111, "token error"))
              }
          }
        }

    }
  }

  private val getRecordFrame = (path("getRecordFrame") & post & pathEndOrSingleSlash) {
    dealPostReq[RecordInfoReq] { req =>
      val rstF: Future[CommonRsp] = roomManager ? (RoomManager.GetRecordFrame(req.recordId, req.playerId, _))
      rstF.map {
        case rsp: RecordFrameRsp =>
          complete(rsp)
        case _ =>
          complete(ErrorRsp(100001, "录像不存在或已损坏"))
      }.recover{
        case e:Exception =>
          log.debug(s"获取游戏录像失败，recover error:$e")
          complete(ErrorRsp(100001, "录像不存在或已损坏"))
      }
      }
    }


  private val getRecordPlayerList = (path("getRecordPlayerList") & post & pathEndOrSingleSlash) {
    dealPostReq[RecordInfoReq] { req =>
      RecordDAO.getRecordById(req.recordId).map {
        case Some(r) =>
          try{
            val replay = initInput(r.filePath)
            val info = replay.init()
            val frameCount = info.frameCount
            val playerList = userMapDecode(replay.getMutableInfo(AppSettings.essfMapKeyName).getOrElse(Array[Byte]())).right.get.m
            val playerInfo = playerList.map { ls =>
              val existTime = ls._2.map { f => ExistTime(f.joinFrame, f.leftFrame) }
              RecordPlayerInfo(ls._1.id, ls._1.name, existTime)
            }
            complete(RecordPlayerInfoRsp(RecordPlayerList(frameCount, playerInfo)))
          } catch {
            case e: Exception =>
              println(s"get record player list error: $e")
              complete(ErrorRsp(100001,"文件不存在或已损坏"))
          }

        case None =>
          complete(ErrorRsp(111000, s"can not find record-${req.recordId}"))
      }
    }
  }

  val roomApiRoutes: Route = {
    getRoomId ~ getRoomPlayerList ~ getRoomList ~ getRecordList ~ getRecordListByTime ~
      getRecordListByPlayer ~ downloadRecord ~ getRecordFrame ~ getRecordPlayerList
  }


}
