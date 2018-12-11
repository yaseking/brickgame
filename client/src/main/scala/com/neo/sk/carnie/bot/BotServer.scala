package com.neo.sk.carnie.bot

import akka.actor.typed.ActorRef
import io.grpc.{Server, ServerBuilder}
import org.seekloud.esheepapi.pb.api._
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc.EsheepAgent
import com.neo.sk.carnie.actor.BotActor
import org.seekloud.esheepapi.pb.actions.Move
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.carnie.paperClient.Score

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by dry on 2018/11/29.
  **/


object BotServer {

  def build(port: Int, executionContext: ExecutionContext, botActor:  ActorRef[BotActor.Command]): Server = {

    val service = new BotServer(botActor:  ActorRef[BotActor.Command])

    ServerBuilder.forPort(port).addService(
      EsheepAgentGrpc.bindService(service, executionContext)
    ).build

  }
}

class BotServer(botActor: ActorRef[BotActor.Command]) extends EsheepAgent {

  private var state: State = State.unknown

  override def createRoom(request: Credit): Future[CreateRoomRsp] = {
    println(s"createRoom Called by [$request")
    botActor ! BotActor.CreateRoom(request.playerId, request.apiToken)
    state = State.init_game
    Future.successful(CreateRoomRsp(errCode = 101, state = state, msg = "ok"))
  }

  override def joinRoom(request: JoinRoomReq): Future[SimpleRsp] = {
    println(s"joinRoom Called by [$request")
    if (request.credit.nonEmpty) {
      botActor ! BotActor.JoinRoom(request.roomId, request.credit.get.playerId, request.credit.get.apiToken)
    }
    state = State.in_game
    Future.successful(SimpleRsp(state = state, msg = "ok"))
//    Future.successful(SimpleRsp(errCode = 102, state = state, msg = "ok"))
  }

  override def leaveRoom(request: Credit): Future[SimpleRsp] = {
    println(s"leaveRoom Called by [$request")
    botActor ! BotActor.LeaveRoom(request.playerId)
    state = State.ended
    Future.successful(SimpleRsp(state = state, msg = "ok"))
//    Future.successful(SimpleRsp(errCode = 103, state = state, msg = "ok"))
  }

  override def actionSpace(request: Credit): Future[ActionSpaceRsp] = {
    println(s"actionSpace Called by [$request")
    val rsp = ActionSpaceRsp(Seq(Move.up, Move.down, Move.left, Move.right), state = state)
    Future.successful(rsp)
  }

  override def action(request: ActionReq): Future[ActionRsp] = {
    println(s"action Called by [$request")
    val rstF: Future[Int] = botActor ? (BotActor.Action(request.move, _))
    rstF.map {
      case -1 => ActionRsp(errCode = 10002, state = state, msg = "action error")
      case frame => ActionRsp(frame, state = state)
    }.recover {
      case e: Exception =>
        ActionRsp(errCode = 10001, state = state, msg = s"internal error:$e")
    }
  }

  override def observation(request: Credit): Future[ObservationRsp] = {
    println(s"observation Called by [$request")
    val rsp = ObservationRsp()
    Future.successful(rsp)
  }

  override def inform(request: Credit): Future[InformRsp] = {
    println(s"inform Called by [$request")
    val rstF: Future[(Score, Int)] = botActor ? BotActor.ReturnInform
    rstF.map { rst =>
      val health = state match {
        case State.in_game => 1
        case _ => 0
      }
      InformRsp(rst._1.area, rst._1.k, health, rst._2,state = state)
    }.recover {
      case e: Exception =>
        InformRsp(errCode = 10001, state = state, msg = s"internal error:$e")
    }
  }

}
