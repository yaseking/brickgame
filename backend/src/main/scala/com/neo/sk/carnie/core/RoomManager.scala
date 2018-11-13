package com.neo.sk.carnie.core

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import com.neo.sk.carnie.paperClient.Protocol
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.neo.sk.carnie.core.RoomActor.UserDead
import com.neo.sk.carnie.paperClient.Protocol.SendPingPacket
import com.neo.sk.carnie.paperClient.WsSourceProtocol
import com.neo.sk.carnie.ptcl.RoomApiProtocol.{CommonRsp, PlayerIdName, RecordFrameInfo}


/**
  * Created by dry on 2018/10/12.
  **/
object RoomManager {
  private val log = LoggerFactory.getLogger(this.getClass)

  //  private val roomInUse = mutable.HashMap[Long,mutable.HashSet[(Long,Boolean)]]()//roomId->Set((uid,False))uid-->等待复活
  private val roomMap = mutable.HashMap[Int, mutable.HashSet[(String, String)]]() //roomId->Set((userId, name))
  private val limitNum = 15

  //  private val userMap = mutable.HashMap[Long, (Long, String)]() //(userId, (roomId, name))


  trait Command

  trait UserAction extends Command

  case class UserActionOnServer(id: String, action: Protocol.UserAction) extends Command

  case class Join(id: String, name: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command

  case class Left(id: String, name: String) extends Command

  case class WatcherLeft(roomId: Int, playerId: String) extends Command

  case class StartReplay(recordId: Long, playedId: String, frame: Int, subscriber: ActorRef[WsSourceProtocol.WsMsgSource], playerId: String) extends Command

  case class StopReplay(recordId: Long, playerId: String) extends Command

  case class FindRoomId(pid: String, reply: ActorRef[Option[Int]]) extends Command

  case class FindPlayerList(roomId: Int, reply: ActorRef[List[PlayerIdName]]) extends Command

  case class FindAllRoom(reply: ActorRef[List[Int]]) extends Command

  case class GetRecordFrame(recordId: Long, playerId: String, replyTo: ActorRef[CommonRsp]) extends Command

  case object CompleteMsgFront extends Command

  case class FailMsgFront(ex: Throwable) extends Command

  private case class TimeOut(msg: String) extends Command

  private case class ChildDead[U](roomId: Int, name: String, childRef: ActorRef[U]) extends Command

  case class LeftRoom(uid: String, tankId: Int, name: String, userOpt: Option[Long]) extends Command

  case class UserLeft(id: String) extends Command

  case class PreWatchGame(roomId: Int, playerId: String, userId: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command

  private case object UnKnowAction extends Command

  //  case class Key(id: Long, keyCode: Int, frameCount: Long, actionId: Int) extends UserAction
  //  case class TextInfo(msg: String) extends UserAction
  //  case class SendPingPacket(id: Long, createTime: Long) extends UserAction
  //  case class NeedToSync(id: Long) extends UserAction with Command


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
          if (roomMap.nonEmpty && roomMap.exists(_._2.size < limitNum)) {
            val roomId = roomMap.filter(_._2.size < limitNum).head._1
            roomMap.put(roomId, roomMap(roomId) += ((id, name)))
            getRoomActor(ctx, roomId) ! RoomActor.JoinRoom(id, name, subscriber)
          } else {
            val roomId = roomIdGenerator.getAndIncrement()
            roomMap.put(roomId, mutable.HashSet((id, name)))
            getRoomActor(ctx, roomId) ! RoomActor.JoinRoom(id, name, subscriber)
          }
          Behaviors.same

        case UserDead(id, name) =>
          val roomId = roomMap.filter(r => r._2.exists(u => u._1 == id)).head._1
          getRoomActor(ctx, roomId) ! UserDead(id, name)
          Behaviors.same

        case StartReplay(recordId, playedId, frame, subscriber, playerId) =>
          log.info(s"got $msg")
          getGameReplay(ctx, recordId, playerId) ! GameReplay.InitReplay(subscriber, playedId, frame)
          Behaviors.same

        case GetRecordFrame(recordId, playerId, replyTo) =>
          //          log.info(s"got $msg")
          getGameReplay(ctx, recordId, playerId) ! GameReplay.GetRecordFrame(playerId, replyTo)
          Behaviors.same

        case StopReplay(recordId, playerId) =>
          getGameReplay(ctx, recordId, playerId) ! GameReplay.StopReplay()
          Behaviors.same

        case m@PreWatchGame(roomId, playerId, userId, subscriber) =>
          log.info(s"got $m")
          val truePlayerId = if (playerId.contains("Set")) playerId.drop(4).dropRight(1) else playerId
          log.info(s"truePlayerId: $truePlayerId")
          getRoomActor(ctx, roomId) ! RoomActor.WatchGame(truePlayerId, userId, subscriber)
          Behaviors.same

        case msg@Left(id, name) =>
          log.info(s"got $msg")
          val roomId = roomMap.filter(r => r._2.exists(u => u._1 == id)).head._1
          roomMap.update(roomId, roomMap(roomId).-((id, name)))
          getRoomActor(ctx, roomId) ! RoomActor.LeftRoom(id, name)
          Behaviors.same

        case msg@WatcherLeft(roomId, userId) =>
          log.info(s"got $msg")
          getRoomActor(ctx, roomId) ! RoomActor.WatcherLeftRoom(userId)
          Behaviors.same

        case m@UserActionOnServer(id, action) =>
          action match {
            case SendPingPacket(_, createTime) => //

            case _ =>
//              log.debug(s"receive $m...roomMap:$roomMap")
          }
          if (roomMap.exists(r => r._2.exists(u => u._1 == id))) {
            val roomId = roomMap.filter(r => r._2.exists(u => u._1 == id)).head._1
            getRoomActor(ctx, roomId) ! RoomActor.UserActionOnServer(id, action)
          }
          Behaviors.same


        case ChildDead(roomId, child, childRef) =>
          log.debug(s"roomManager 不再监管room:$child,$childRef")
          ctx.unwatch(childRef)
          roomMap.remove(roomId)
          Behaviors.same

        case UserLeft(id) =>
          log.debug(s"got Terminated id = $id")
          val roomInfoOpt = roomMap.find(r => r._2.exists(u => u._1 == id))
          if (roomInfoOpt.nonEmpty) {
            val roomId = roomInfoOpt.get._1
            val filterUserInfo = roomMap(roomId).find(_._1 == id)
            if (filterUserInfo.nonEmpty) {
              roomMap.update(roomId, roomMap(roomId).-(filterUserInfo.get))
            }
          }
          Behaviors.same

        case FindRoomId(pid, reply) =>
          log.debug(s"got playerId = $pid")
          reply ! roomMap.find(r => r._2.exists(i => i._1 == pid)).map(_._1)
          Behaviors.same

        case FindPlayerList(roomId, reply) =>
          log.debug(s"${ctx.self.path} got roomId = $roomId")
          val roomInfo = roomMap.get(roomId)
          val replyMsg = if (roomInfo.nonEmpty) {
            roomInfo.get.toList.map { p => PlayerIdName(p._1, p._2) }
          } else Nil
          reply ! replyMsg
          Behaviors.same

        case FindAllRoom(reply) =>
          log.debug(s"got all room")
          reply ! roomMap.keySet.toList
          Behaviors.same

        case unknown =>
          log.debug(s"${ctx.self.path} receive a msg unknown:$unknown")
          Behaviors.unhandled
      }
    }
  }

  private def sink(actor: ActorRef[Command], id: String, name: String) = ActorSink.actorRef[Command](
    ref = actor,
    onCompleteMessage = Left(id, name),
    onFailureMessage = FailMsgFront.apply
  )

  private def sink4Replay(actor: ActorRef[Command], recordId: Long, playerId: String) = ActorSink.actorRef[Command](
    ref = actor,
    onCompleteMessage = StopReplay(recordId, playerId),
    onFailureMessage = FailMsgFront.apply
  )

  private def sink4WatchGame(actor: ActorRef[Command], roomId: Int, userId: String) = ActorSink.actorRef[Command](
    ref = actor,
    onCompleteMessage = WatcherLeft(roomId, userId),
    onFailureMessage = FailMsgFront.apply
  )

  def joinGame(actor: ActorRef[RoomManager.Command], userId: String, name: String): Flow[Protocol.UserAction, WsSourceProtocol.WsMsgSource, Any] = {
    val in = Flow[Protocol.UserAction]
      .map {
        case action@Protocol.Key(id, _, _, _) => UserActionOnServer(id, action)
        case action@Protocol.SendPingPacket(id, _) => UserActionOnServer(id, action)
        case action@Protocol.NeedToSync(id) => UserActionOnServer(id, action)
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
      ).mapMaterializedValue(outActor => actor ! Join(userId, name, outActor))

    Flow.fromSinkAndSource(in, out)
  }

  def watchGame(actor: ActorRef[RoomManager.Command], roomId: Int, playerId: String, userId: String): Flow[Protocol.UserAction, WsSourceProtocol.WsMsgSource, Any] = {
    val in = Flow[Protocol.UserAction]
      .map {
        case action@Protocol.Key(id, _, _, _) => UserActionOnServer(id, action)
        case action@Protocol.SendPingPacket(id, _) => UserActionOnServer(id, action)
        case action@Protocol.NeedToSync(id) => UserActionOnServer(id, action)
        case _ => UnKnowAction
      }
      .to(sink4WatchGame(actor, roomId, userId))

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
      ).mapMaterializedValue(outActor => actor ! PreWatchGame(roomId, playerId, userId, outActor))

    Flow.fromSinkAndSource(in, out)
  }

  def replayGame(actor: ActorRef[RoomManager.Command], recordId: Long, playedId: String, frame: Int, playerId: String): Flow[Protocol.UserAction, WsSourceProtocol.WsMsgSource, Any] = {
    val in = Flow[Protocol.UserAction]
      .map {
        case action@Protocol.Key(id, _, _, _) => UserActionOnServer(id, action)
        case action@Protocol.SendPingPacket(id, _) => UserActionOnServer(id, action)
        case action@Protocol.NeedToSync(id) => UserActionOnServer(id, action)
        case _ => UnKnowAction
      }
      .to(sink4Replay(actor, recordId, playerId))

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
      ).mapMaterializedValue(outActor => actor ! StartReplay(recordId, playedId, frame, outActor, playerId))

    Flow.fromSinkAndSource(in, out)
  }

  private def getRoomActor(ctx: ActorContext[Command], roomId: Int) = {
    val childName = s"room_$roomId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(RoomActor.create(roomId), childName)
      ctx.watchWith(actor, ChildDead(roomId, childName, actor))
      actor

    }.upcast[RoomActor.Command]
  }

  private def getGameReplay(ctx: ActorContext[Command], recordId:Long, playerId: String) = {
    val childName = s"gameReplay--$recordId--$playerId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(GameReplay.create(recordId, playerId), childName)
      actor
    }.upcast[GameReplay.Command]
  }
}
