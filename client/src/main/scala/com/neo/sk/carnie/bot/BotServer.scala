package com.neo.sk.carnie.bot

import akka.actor.typed.ActorRef
import io.grpc.{Server, ServerBuilder}
import org.seekloud.esheepapi.pb.api._
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc.EsheepAgent
import com.neo.sk.carnie.actor.BotActor
import org.seekloud.esheepapi.pb.actions.Move
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.carnie.paperClient.{Protocol, Score}
import com.neo.sk.carnie.common.BotAppSetting
import org.seekloud.esheepapi.pb.observations.{ImgData, LayeredObservation}
import com.neo.sk.carnie.Boot.{executor, scheduler, timeout}
import com.neo.sk.carnie.actor.BotActor.{GetFrame, Reincarnation}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by dry on 2018/11/29.
  **/


object BotServer {

  def build(port: Int, executionContext: ExecutionContext, botActor:  ActorRef[BotActor.Command], botName: String): Server = {

    val service = new BotServer(botActor:  ActorRef[BotActor.Command])

    ServerBuilder.forPort(port).addService(
      EsheepAgentGrpc.bindService(service, executionContext)
    ).build

  }
}

class BotServer(botActor: ActorRef[BotActor.Command]) extends EsheepAgent {

  private var state: State = State.unknown

  override def createRoom(request: CreateRoomReq): Future[CreateRoomRsp] = {
    println(s"!!!!!createRoom Called by [$request")
    if (request.credit.nonEmpty && request.credit.get.apiToken == BotAppSetting.apiToken) {
      state = State.init_game
      val rstF: Future[String] = botActor ?
        (BotActor.CreateRoom(request.credit.get.apiToken, request.password, _))
      rstF.map {
        case "error" =>
          CreateRoomRsp(errCode = 10005, state = State.unknown, msg = "create room error")
        case roomId =>
          state = State.in_game
          CreateRoomRsp(roomId, state = state, msg = "ok")
      }.recover {
        case e: Exception =>
          CreateRoomRsp(errCode = 10001, state = state, msg = s"internal error:$e")
      }
    } else Future.successful(CreateRoomRsp(errCode = 10003, state = State.unknown, msg = "apiToken error"))
  }

  override def joinRoom(request: JoinRoomReq): Future[SimpleRsp] = {
    println(s"joinRoom Called by [$request")
    if (request.credit.nonEmpty && request.credit.get.apiToken == BotAppSetting.apiToken) {
      val rstF: Future[SimpleRsp] = botActor ?
        (BotActor.JoinRoom(request.roomId, request.credit.get.apiToken, _))
      rstF.map {rsp =>
        rsp.errCode match {
          case 0 =>
            state = State.in_game
            rsp.copy(state = state)
          case _ => rsp
        }
      }.recover {
        case e: Exception =>
          SimpleRsp(errCode = 10001, state = state, msg = s"internal error:$e")
      }
    } else Future.successful(SimpleRsp(errCode = 10003, state = State.unknown, msg = "apiToken error"))
  }

  override def leaveRoom(request: Credit): Future[SimpleRsp] = {
    println(s"leaveRoom Called by [$request")
    if (request.apiToken == BotAppSetting.apiToken) {
      botActor ! BotActor.LeaveRoom
      state = State.ended
      Future.successful(SimpleRsp(state = state, msg = "ok"))
    } else Future.successful(SimpleRsp(errCode = 10003, state = State.unknown, msg = "apiToken error"))

  }

  override def actionSpace(request: Credit): Future[ActionSpaceRsp] = {
    println(s"actionSpace Called by [$request")
    if (request.apiToken == BotAppSetting.apiToken) {
      val rsp = ActionSpaceRsp(Seq(Move.up, Move.down, Move.left, Move.right), state = state)
      Future.successful(rsp)
    } else Future.successful(ActionSpaceRsp(errCode = 10003, state = State.unknown, msg = "apiToken error"))

  }

  override def action(request: ActionReq): Future[ActionRsp] = {
//    println(s"action Called by [$request")
    if (request.credit.nonEmpty & request.credit.get.apiToken == BotAppSetting.apiToken) {
      val rstF: Future[Long] = botActor ? (BotActor.Action(request.move, _))
      rstF.map {
        case -1L => ActionRsp(errCode = 10002, state = state, msg = "action error")
        case -2L =>
          state = State.killed
          ActionRsp(errCode = 10004, state = state, msg = "not in_game state")
        case frame => ActionRsp(frame, state = state)
      }.recover {
        case e: Exception =>
          ActionRsp(errCode = 10001, state = state, msg = s"internal error:$e")
      }
    } else Future.successful(ActionRsp(errCode = 10003, state = State.unknown, msg = "apiToken error"))

  }

  override def observation(request: Credit): Future[ObservationRsp] = {
//    println(s"observation Called by [$request")
    if (request.apiToken == BotAppSetting.apiToken) {
      if (state == State.in_game) {
        val rstF: Future[(Option[ImgData], Option[LayeredObservation], Int, Boolean)]  = botActor ? BotActor.ReturnObservation
        rstF.map {rst =>
          if (rst._4) {//in game
            state = State.in_game
            ObservationRsp(rst._2, rst._1, rst._3, state = state, msg = "ok")

          } else { //killed
            state = State.killed
            ObservationRsp(errCode = 10004, state = state, msg = s"not in_game state")
          }
        }.recover {
          case e: Exception =>
            ObservationRsp(errCode = 10001, state = state, msg = s"internal error:$e")
        }
      } else Future.successful(ObservationRsp(errCode = 10004, state = state, msg = s"not in_game state"))

    } else Future.successful(ObservationRsp(errCode = 10003, state = State.unknown, msg = "apiToken error"))
  }

  override def observationWithInfo(request: Credit): Future[ObservationWithInfoRsp] = {
    if (request.apiToken == BotAppSetting.apiToken) {
      if (state == State.in_game) {
        val rstF: Future[(Option[ImgData], Option[LayeredObservation], Score, Int, Boolean)]  = botActor ? BotActor.ReturnObservationWithInfo
        rstF.map {rst =>
          if (rst._5) {//in game
            state = State.in_game
            ObservationWithInfoRsp(rst._2, rst._1, rst._3.area, rst._3.k, frameIndex = rst._4, state = state, msg = "ok")

          } else { //killed
            state = State.killed
            ObservationWithInfoRsp(errCode = 10004, state = state, msg = s"not in_game state")
          }
        }.recover {
          case e: Exception =>
            ObservationWithInfoRsp(errCode = 10001, state = state, msg = s"internal error:$e")
        }
      } else Future.successful(ObservationWithInfoRsp(errCode = 10004, state = state, msg = s"not in_game state"))

    } else Future.successful(ObservationWithInfoRsp(errCode = 10003, state = State.unknown, msg = "apiToken error"))
  }

  override def inform(request: Credit): Future[InformRsp] = {
    println(s"inform Called by [$request")
    if (request.apiToken == BotAppSetting.apiToken) {
      val rstF: Future[(Score, Long)] = botActor ? BotActor.ReturnInform
      rstF.map { rst =>
        InformRsp(rst._1.area, rst._1.k, frameIndex = rst._2, state = state)
      }.recover {
        case e: Exception =>
          InformRsp(errCode = 10001, state = state, msg = s"internal error:$e")
      }
    } else Future.successful(InformRsp(errCode = 10003, state = State.unknown, msg = "apiToken error"))
  }

  override def reincarnation(request: Credit): Future[SimpleRsp] = {
    println(s"reincarnation Called by [$request")
    if(request.apiToken == BotAppSetting.apiToken) {
      val rstF: Future[SimpleRsp] = botActor ? Reincarnation
      rstF.map {rsp =>
        if(rsp.errCode == 0) {
          state = State.in_game
          rsp
        }
        else SimpleRsp(errCode = 10006, state = State.unknown, msg = "reincarnation error")
      }
    } else Future.successful(SimpleRsp(errCode = 10003, state = State.unknown, msg = "apiToken error"))
  }

  override def systemInfo(request: Credit): Future[SystemInfoRsp] = {
    println(s"systemInfo Called by [$request")
    if (request.apiToken == BotAppSetting.apiToken) {
      Future.successful(SystemInfoRsp(framePeriod = Protocol.frameRate1, state = state))
    } else Future.successful(SystemInfoRsp(errCode = 10003, state = State.unknown, msg = "apiToken error"))
  }

  override def currentFrame(request: Credit): Future[CurrentFrameRsp] = {
    if (request.apiToken == BotAppSetting.apiToken) {
      val rstF: Future[Int] = botActor ? GetFrame
      rstF.map { rsp =>
        CurrentFrameRsp(frame = rsp, state = state)
      }
    } else Future.successful(CurrentFrameRsp(errCode = 10003, state = State.unknown, msg = "apiToken error"))
  }


}
