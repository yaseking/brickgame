package org.seekloud.brickgame.core

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import org.seekloud.brickgame.paperClient.Protocol
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import org.seekloud.brickgame.core.RoomActor.UserDead
import org.seekloud.brickgame.paperClient.Protocol.SendPingPacket
import org.seekloud.brickgame.paperClient.WsSourceProtocol
import org.seekloud.brickgame.ptcl.RoomApiProtocol.{CommonRsp, PlayerIdName, RecordFrameInfo}
import org.seekloud.brickgame.common.AppSettings


/**
  * Created by dry on 2018/10/12.
  **/
object RoomManager {
  private val log = LoggerFactory.getLogger(this.getClass)

  private val roomMap = mutable.HashMap[Int, mutable.HashSet[Int]]() //roomId->Set(userId)
  private val limitNum = AppSettings.limitNum

  trait Command

  trait UserAction extends Command

  case class UserActionOnServer(id: Int, action: Protocol.UserAction) extends Command

  case class Join(id: Int, name: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command

  case class Left(id: Int, name: String) extends Command

  case class WatcherLeft(roomId: Int, playerId: String) extends Command

  case class FindRoomId(pid: String, reply: ActorRef[Option[Int]]) extends Command

  case class FindPlayerList(roomId: Int, reply: ActorRef[List[PlayerIdName]]) extends Command

  case class ReturnRoomMap(reply: ActorRef[mutable.HashMap[Int, (Int, Option[String], mutable.HashSet[(String, String)])]]) extends Command

  case class FindAllRoom(reply: ActorRef[List[Int]]) extends Command

  case class FindAllRoom4Client(reply: ActorRef[List[String]]) extends Command

  case class JudgePlaying(userId: String, reply: ActorRef[Boolean]) extends Command

  case class JudgePlaying4Watch(roomId: Int, userId: String, reply: ActorRef[Boolean]) extends Command

  case object CompleteMsgFront extends Command

  case class FailMsgFront(ex: Throwable) extends Command

  private case class TimeOut(msg: String) extends Command

  private case class ChildDead[U](roomId: Int, name: String, childRef: ActorRef[U]) extends Command

  case class BotsJoinRoom(roomId: Int, bots: List[(String, String)]) extends Command

  private case object UnKnowAction extends Command

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        val roomIdGenerator = new AtomicInteger(1000)
        idle(roomIdGenerator)
      }
    }
  }

  def idle(roomIdGenerator: AtomicInteger)(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case msg@Join(id, name, subscriber) =>
          log.info(s"got $msg")
          if (roomMap.nonEmpty && roomMap.exists(r => r._2.size < limitNum)) {
            val rooms = roomMap.filter(r => r._2.size < limitNum)
            val roomId = rooms.head._1
            roomMap.put(roomId, roomMap(roomId) + id)
            getRoomActor(ctx, roomId) ! RoomActor.JoinRoom(id, name, subscriber)
          } else {
            val roomId = roomIdGenerator.getAndIncrement()
            roomMap.put(roomId, mutable.HashSet(id))
            getRoomActor(ctx, roomId) ! RoomActor.JoinRoom(id, name, subscriber)
          }
          Behaviors.same

        case UserDead(roomId, mode, users) =>
          try {
            getRoomActor(ctx, roomId) ! RoomActor.UserDead(roomId, mode, users)
          } catch {
            case e: Exception =>
              log.error(s"user dead error:$e")
          }
          Behaviors.same

        case msg@Left(id, _) =>
          log.info(s"got $msg")
          try {
            val roomId = roomMap.filter(r => r._2.contains(id)).head._1
            roomMap.update(roomId, roomMap(roomId) - id)
            getRoomActor(ctx, roomId) ! RoomActor.LeftRoom(id)
            if(roomMap.get(roomId).isEmpty) {
              roomMap.remove(roomId)
            }
          } catch {
            case e: Exception =>
              log.error(s"$msg got error: $e")
          }

          Behaviors.same

        case m@UserActionOnServer(id, action) =>
//          log.info(s"got msg: $m")
          if (roomMap.exists(r => r._2.contains(id))) {
            val roomId = roomMap.filter(r => r._2.contains(id)).head._1
            getRoomActor(ctx, roomId) ! RoomActor.UserActionOnServer(id, action)
          }
          Behaviors.same

        case ChildDead(roomId, child, childRef) =>
          log.debug(s"roomManager 不再监管room:$child,$childRef")
//          ctx.unwatch(childRef)
          roomMap.remove(roomId)
          Behaviors.same

        case FindAllRoom(reply) =>
          log.info(s"got all room")
          reply ! roomMap.keySet.toList
          Behaviors.same

        case unknown =>
          log.debug(s"${ctx.self.path} receive a msg unknown:$unknown")
          Behaviors.unhandled
      }
    }
  }

  private def sink(actor: ActorRef[Command], id: Int, name: String) = ActorSink.actorRef[Command](
    ref = actor,
    onCompleteMessage = Left(id, name),
    onFailureMessage = FailMsgFront.apply
  )

  def joinGame(actor: ActorRef[RoomManager.Command], userId: Int, name: String): Flow[Protocol.UserAction, WsSourceProtocol.WsMsgSource, Any] = {
    val in = Flow[Protocol.UserAction]
      .map {
        case action@Protocol.Key(_, _) => UserActionOnServer(userId, action)
        case action@Protocol.SendPingPacket(_) => UserActionOnServer(userId, action)
        case action@Protocol.NeedToSync => UserActionOnServer(userId, action)
        case action@Protocol.PressSpace => UserActionOnServer(userId, action)
        case action@Protocol.InitAction => UserActionOnServer(userId, action)
        case action@Protocol.SendExpression(_) => UserActionOnServer(userId, action)
        case _ => UnKnowAction
      }
      .to(sink(actor, userId, name))

    val out =
      ActorSource.actorRef[WsSourceProtocol.WsMsgSource](
        completionMatcher = {
          case WsSourceProtocol.CompleteMsgServer ⇒
        },
        failureMatcher = {
          case WsSourceProtocol.FailMsgServer(e) ⇒ e
        },
        bufferSize = 64,
        overflowStrategy = OverflowStrategy.dropHead
      ).mapMaterializedValue{outActor => actor ! Join(userId, name, outActor)}

    Flow.fromSinkAndSource(in, out)
  }

//  private def getRoomActor(ctx: ActorContext[Command], roomId: Int, mode: Int) = {
//    val childName = s"room_$roomId-mode_$mode"
//    ctx.child(childName).getOrElse {
//      val actor = ctx.spawn(RoomActor.create(roomId, mode), childName)
////      ctx.watchWith(actor, ChildDead(roomId, childName, actor))
//      actor
//
//    }.upcast[RoomActor.Command]
//  }

  private def getRoomActor(ctx: ActorContext[Command], roomId: Int) = {
    val childName = s"room_$roomId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(RoomActor.create(roomId), childName)
//      ctx.watchWith(actor, ChildDead(roomId, childName, actor))
      actor

    }.upcast[RoomActor.Command]
  }

}
