package com.neo.sk.carnie.paperClient

import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.slf4j.LoggerFactory
import com.neo.sk.carnie.paperClient.Protocol._

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:29 PM
  */


trait PlayGround {


  def joinGame(id: Long, name: String): Flow[Protocol.UserAction, Protocol.GameMessage, Any]

  def syncData()

}


object PlayGround {

  val border = Point(BorderSize.w, BorderSize.h)

  private val log = LoggerFactory.getLogger(this.getClass)

  val roomIdGen = new AtomicInteger(100)

  private val limitNum = 8

  private val winStandard = (BorderSize.w - 2) * (BorderSize.h - 2) * 0.1

  def create(system: ActorSystem)(implicit executor: ExecutionContext): PlayGround = {

    val ground = system.actorOf(Props(new Actor {
      var subscribers = Map.empty[Long, ActorRef]

      var userMap = Map.empty[Long, (Int, String)] //(userId, (roomId, name))

      var roomMap = Map.empty[Int, (Int, GridOnServer)] //(roomId, (roomNum, grid))

      var lastSyncDataMap = Map.empty[Int, GridDataSync] //(roomId, (roomNum, grid))

      var tickCount = 0l

      override def receive: Receive = {
        case r@Join(id, name, subscriber) =>
          log.info(s"got $r")
          val roomId = if (roomMap.isEmpty) {
            val grid = new GridOnServer(border)
            val newRoomId = roomIdGen.get()
            roomMap += (newRoomId -> (0, grid))
            newRoomId
          } else {
            if (roomMap.exists(_._2._1 < limitNum)) {
              roomMap.filter(_._2._1 < limitNum).head._1
            } else {
              val grid = new GridOnServer(border)
              val newRoomId = roomMap.maxBy(_._1)._1 + 1
              roomMap += (newRoomId -> (0, grid))
              newRoomId
            }
          }
          userMap += (id -> (roomId, name))
          roomMap += (roomId -> (roomMap(roomId)._1 + 1, roomMap(roomId)._2))
          context.watch(subscriber)
          subscribers += (id -> subscriber)
          roomMap(roomId)._2.addSnake(id, roomId, name)
          dispatchTo(id, Protocol.Id(id))
          val gridData = roomMap(roomId)._2.getGridData
          dispatch(gridData, roomId)

        case r@Left(id, _) =>
          log.info(s"got $r")
          if (userMap.get(id).nonEmpty) {
            val roomId = userMap(id)._1
            val newUserNum = roomMap(roomId)._1 - 1
            roomMap(roomId)._2.removeSnake(id)
            if (newUserNum <= 0) roomMap -= roomId else roomMap += (roomId -> (newUserNum, roomMap(roomId)._2))
            userMap -= id
            subscribers.get(id).foreach(context.unwatch)
            subscribers -= id
            //            dispatch(Protocol.SnakeLeft(id, name), roomId)
          }

        case userAction: UserAction => userAction match {
          case r@Key(id, keyCode, frameCount, actionId) =>
            val roomId = userMap(id)._1
            if (keyCode == KeyEvent.VK_SPACE) {
              roomMap(roomId)._2.addSnake(id, roomId, userMap.getOrElse(id, (0, "Unknown"))._2)
            } else {
              val grid = roomMap(roomId)._2
              val realFrame = if (frameCount >= grid.frameCount) frameCount else grid.frameCount
              grid.addActionWithFrame(id, keyCode, realFrame)
              dispatch(Protocol.SnakeAction(id, keyCode, realFrame, actionId), roomId)
            }

          case SendPingPacket(id, createTime) =>
            dispatchTo(id, Protocol.ReceivePingPacket(createTime))

          case NeedToSync(id) =>
            val roomId = userMap(id)._1
            dispatchTo(id, roomMap(roomId)._2.getGridData)

          case _ =>
        }

        case r@Terminated(actor) =>
          log.warn(s"got $r")
          subscribers.find(_._2.equals(actor)).foreach { case (id, _) =>
            log.debug(s"got Terminated id = $id")
            if (userMap.get(id).nonEmpty) {
              val roomId = userMap(id)._1
              userMap -= id
              subscribers -= id
              roomMap(roomId)._2.removeSnake(id).foreach(s => dispatch(Protocol.SnakeLeft(id, s.name), roomId))
              val newUserNum = roomMap(roomId)._1 - 1
              if (newUserNum <= 0) roomMap -= roomId else roomMap += (roomId -> (newUserNum, roomMap(roomId)._2))
            }
          }

        case Sync =>
          tickCount += 1
          roomMap.foreach { r =>
            val userInRoom = userMap.filter(_._2._1 == r._1).keys
            if (userInRoom.nonEmpty) {
              val shouldNewSnake = if(tickCount % 20 == 5) true else false
              val grid = r._2._2
              val finishFields = grid.updateInService(shouldNewSnake)
              val newData = grid.getGridData
              newData.killHistory.foreach { i =>
                if (i.frameCount + 1 == newData.frameCount) dispatch(Protocol.SomeOneKilled(i.killedId, userMap(i.killedId)._2, i.killerName), userMap(i.killedId)._1)
              }
              if (shouldNewSnake) {
                dispatch(newData, r._1)
              }else if (finishFields.nonEmpty) {
                val finishUsers = finishFields.map(_._1)
                finishUsers.foreach(u => dispatchTo(u, newData))
                val newField = finishFields.map { f =>
                  FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
                    ScanByColumn(y.toInt, Tool.findContinuous(target.map(_.x.toInt).toArray.sorted))
                  }.toList)
                }
                userInRoom.filterNot(finishUsers.contains(_)).foreach(u => dispatchTo(u, NewFieldInfo(grid.frameCount, newField)))
              }
              if(tickCount % 10 == 3) dispatch(Protocol.Ranks(r._2._2.currentRank, r._2._2.historyRankList), r._1)
              if (r._2._2.currentRank.nonEmpty && r._2._2.currentRank.head.area >= winStandard) {
                r._2._2.cleanData()
                dispatch(Protocol.SomeOneWin(userMap(r._2._2.currentRank.head.id)._2), r._1)
              }
            }
          }

        case x =>
          log.warn(s"got unknown msg: $x")
      }

      def dispatchTo(id: Long, gameOutPut: Protocol.GameMessage): Unit = {
        subscribers.get(id).foreach { ref => ref ! gameOutPut }
      }

      def dispatch(gameOutPut: Protocol.GameMessage, roomId: Long) = {
        val user = userMap.filter(_._2._1 == roomId).keys.toList
        subscribers.foreach { case (id, ref) if user.contains(id) => ref ! gameOutPut case _ => }
      }
    }
    ), "ground")

    import concurrent.duration._
    system.scheduler.schedule(3 seconds, Protocol.frameRate millis, ground, Sync) // sync tick

    def playInSink(id: Long, name: String): Sink[UserAction, NotUsed] = Sink.actorRef[UserAction](ground, Left(id, name))

    new PlayGround {
      override def joinGame(id: Long, name: String): Flow[UserAction, Protocol.GameMessage, Any] = {
        val in =
          Flow[UserAction]
            .map { s => s }
            .to(playInSink(id, name))

        val out =
          Source.actorRef[Protocol.GameMessage](3, OverflowStrategy.dropHead)
            .mapMaterializedValue(outActor => ground ! Join(id, name, outActor))

        Flow.fromSinkAndSource(in, out)
      }

      override def syncData(): Unit = ground ! Sync
    }

  }


  private case class Join(id: Long, name: String, subscriber: ActorRef)

  private case class Left(id: Long, name: String)

  private case object Sync

}