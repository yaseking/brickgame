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
import com.neo.sk.carnie.Boot.{executor, scheduler}
import com.neo.sk.carnie.common.AppSettings
import com.neo.sk.carnie.models.dao.RecordDAO
import com.neo.sk.carnie.core.TokenActor.AskForToken
import com.neo.sk.carnie.ptcl.RoomApiProtocol
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

//  private val createRoom = (path("createRoom") & post & pathEndOrSingleSlash) {
//    entity(as[Either[Error, CreateRoomInfo]]) {
//      case Right(r) =>
//        roomManager ! RoomManager.CreateRoom(r.mode, r.pwd)
//        complete(SuccessRsp())
//      case Left(e) =>
//        log.debug(s"got errMsg: $e")
//        complete(ErrorRsp(100020, s"createRoom error: $e"))
//    }
//  }

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

  // TODO:
  private val getRoomPlayerList = (path("getRoomPlayerList") & post & pathEndOrSingleSlash) {
    dealPostReq[RoomIdReq] { req =>
      val msg: Future[List[PlayerIdName]] = roomManager ? (RoomManager.FindPlayerList(req.roomId, _))
      msg.map { plist =>
        if(plist.nonEmpty){
//          log.info(s"plist:$plist")
          complete(PlayerListRsp(PlayerInfo(plist)))
        }
         else{
          log.info("get player list error: this room doesn't exist")
          complete(ErrorRsp(100001, "get player list error: this room doesn't exist"))
        }

      }
    }
  }

  private val verifyPwd = (path("verifyPwd") & post & pathEndOrSingleSlash) {//todo 鉴权
    entity(as[Either[Error, PwdReq]]) {
      case Right(req) =>
        val msg: Future[Boolean] = roomManager ? (RoomManager.VerifyPwd(req.roomId, req.pwd, _))
        dealFutureResult(
          msg.map {
            case true =>
              log.info("pwd is right.")
              complete(SuccessRsp())
            case _ =>
              log.info("pwd is wrong.")
              complete(ErrorRsp(100031, "pwd is wrong"))
          }
        )
      case Left(e) =>
        log.debug("Some errs happened in verifyPwd.")
        complete(ErrorRsp(100032, s"Some errs in verifyPwd: $e"))
    }
//    dealPostReq[PwdReq] { req =>
//      val msg: Future[Boolean] = roomManager ? (RoomManager.VerifyPwd(req.roomId, req.pwd, _))
//      msg.map {
//        case true =>
//          log.info("pwd is right.")
//          complete(SuccessRsp())
//        case _ =>
//          log.info("pwd is wrong.")
//          complete(ErrorRsp(100031, "pwd is wrong"))
//      }
//    }
  }

  private val getRoomList = (path("getRoomList") & post & pathEndOrSingleSlash) {
    dealPostReqWithoutData {
      val msg: Future[List[Int]] = roomManager ? RoomManager.FindAllRoom
      msg.map {
        allRoom =>
          if (allRoom.nonEmpty){
            log.info("prepare to return roomList.")
            complete(RoomListRsp(RoomListInfo(allRoom)))
          }
          else {
            log.info("get all room error,there are no rooms")
            complete(ErrorRsp(100000, "get all room error,there are no rooms"))
          }
      }
    }
  }

  private val getRoomList4Client = (path("getRoomList4Client") & post & pathEndOrSingleSlash) {
    dealPostReqWithoutData {
      val msg: Future[List[String]] = roomManager ? RoomManager.FindAllRoom4Client
      msg.map {
        allRoom =>
          if (allRoom.nonEmpty){
            log.info("prepare to return roomList.")
            complete(RoomApiProtocol.RoomListRsp4Client(RoomListInfo4Client(allRoom)))
          }
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
        val data = recordL.groupBy(_._1).map { record => //可能会造成每页显示不到10条录像
          val userList = record._2.map(i => (i._2.userId,i._2.nickname))
          recordInfo(record._1.recordId, record._1.roomId, record._1.startTime, record._1.endTime, userList.length, userList)
        }

        complete(RecordListRsp(data.toList.sortBy(_.recordId)))

        //        recordL.map{ r =>
        //          val record = r._1
        //          if(r._2.nonEmpty) {
        //            val userList = r._2.get
        //            recordInfo(record.recordId, record.roomId, record.startTime, record.endTime, userList.length, userList)
        //          }
        //          else recordInfo(record.recordId, record.roomId, record.startTime, record.endTime, userList.length, userList)}
        //        complete(RecordListRsp(recordL.toList.map(_._1).distinct.sortWith((a, b) => a.recordId > b.recordId).map { r =>
        //          val userList = recordL.map(i => i._2).distinct.filter(_.recordId == r.recordId).map(_.userId)
        //          recordInfo(r.recordId, r.roomId, r.startTime, r.endTime, userList.length, userList)
        //        }))
      }
    }
  }

  private val getRecordListByTime = (path("getRecordListByTime") & post & pathEndOrSingleSlash) {
    dealPostReq[RecordByTimeReq] { req =>
      RecordDAO.getRecordListByTime(req.startTime, req.endTime, req.lastRecordId, req.count).map { recordL =>
        val data = recordL.groupBy(_._1).take(req.count).map { case (record, res) =>
          val userList = res.filter(_._2.nonEmpty).map(i => (i._2.get.userId,i._2.get.nickname))
          recordInfo(record.recordId, record.roomId, record.startTime, record.endTime, userList.length, userList)
        }
        complete(RecordListRsp(data.toList.sortBy(_.recordId)))
//      RecordDAO.getRecordListByTime(req.startTime, req.endTime, req.lastRecordId, req.count).map { recordL =>
//        complete(RecordListRsp(recordL.toList.map(_._1).distinct.sortWith((a, b) => a.recordId > b.recordId).take(req.count).map { r =>
//          val userList = recordL.map(i => i._2).distinct.filter(_.recordId == r.recordId).map(_.userId)
//          recordInfo(r.recordId, r.roomId, r.startTime, r.endTime, userList.length, userList)
//        }))
      }
    }
  }

  private val getRecordListByPlayer = (path("getRecordListByPlayer") & post & pathEndOrSingleSlash) {
    dealPostReq[RecordByPlayerReq] { req =>
      RecordDAO.getRecordListByPlayer(req.playerId, req.lastRecordId, req.count).map { recordL =>
        val data = recordL.groupBy(_._1).take(req.count).map { case (record, res) =>
          val userList = res.filter(_._2.nonEmpty).map(i => (i._2.get.userId,i._2.get.nickname))
          recordInfo(record.recordId, record.roomId, record.startTime, record.endTime, userList.length, userList)
        }
        complete(RecordListRsp(data.toList.sortBy(_.recordId)))
//        complete(RecordListRsp(data.toList.sortBy(_.recordId)))
//      RecordDAO.getRecordListByPlayer(req.playerId, req.lastRecordId, req.count).map { recordL =>
//        complete(RecordListRsp(recordL.toList.filter(_._2.userId == req.playerId).map(_._1).distinct.sortWith((a, b) => a.recordId > b.recordId).take(req.count).map { r =>
//          val userList = recordL.map(i => i._2).distinct.filter(_.recordId == r.recordId).map(_.userId)
//          recordInfo(r.recordId, r.roomId, r.startTime, r.endTime, userList.length, userList)
//        }))
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
            val frameBias = metaDataDecode(info.simulatorMetadata).right.get.initFrame
            val frameCount = info.frameCount
            val playerList = userMapDecode(replay.getMutableInfo(AppSettings.essfMapKeyName).getOrElse(Array[Byte]())).right.get.m
            val playerInfo = playerList.map { ls =>
              val existTime = ls._2.map { f => ExistTime(f.joinFrame - frameBias, f.leftFrame -  frameBias) }
              RecordPlayerInfo(ls._1.id, ls._1.name, existTime)
            }
            complete(RecordPlayerInfoRsp(RecordPlayerList(frameCount, playerInfo)))
          } catch {
            case e: Exception =>
              log.debug(s"get record player list error: $e")
              complete(ErrorRsp(100001,"文件不存在或已损坏"))
          }

        case None =>
          complete(ErrorRsp(111000, s"can not find record-${req.recordId}"))
      }
    }
  }


  val roomApiRoutes: Route = {
    getRoomId ~ getRoomList ~ getRecordList ~ getRecordListByTime ~ getRoomList4Client ~ verifyPwd ~
      getRecordListByPlayer ~ downloadRecord ~ getRecordFrame ~ getRecordPlayerList ~ getRoomPlayerList
  }


}
