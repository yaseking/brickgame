package com.neo.sk.carnie.core

import java.awt.event.KeyEvent
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import com.neo.sk.carnie.paperClient.Protocol._
import org.slf4j.LoggerFactory
import com.neo.sk.carnie.paperClient._
import com.neo.sk.carnie.Boot.roomManager
import com.neo.sk.carnie.core.GameRecorder.RecordData
import com.neo.sk.carnie.common.AppSettings
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import concurrent.duration._
import com.neo.sk.carnie.Boot.{executor, scheduler, timeout, tokenActor}
import com.neo.sk.carnie.core.TokenActor.AskForToken
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.carnie.core.BotActor.{BackToGame, BotDead, KillBot}
import com.neo.sk.carnie.models.dao.PlayerRecordDAO
import com.neo.sk.carnie.protocol.EsheepProtocol.PlayerRecord
import com.neo.sk.utils.EsheepClient
import scala.concurrent.Future
import scala.util.Random

/**
  * Created by dry on 2018/10/12.
  **/
object RoomActor {

  private val log = LoggerFactory.getLogger(this.getClass)
  val border = Point(BorderSize.w, BorderSize.h)

  private val upperLimit = AppSettings.upperLimit.toFloat

  private val lowerLimit = AppSettings.lowerLimit.toFloat

  private val fullSize = (BorderSize.w - 2) * (BorderSize.h - 2)

  private val classify = 5

  private final case object SyncKey

  sealed trait Command

  case class UserActionOnServer(id: String, action: Protocol.UserAction) extends Command

  case class JoinRoom(id: String, name: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource], img: Int) extends Command

  case class JoinRoom4Bot(id: String, name: String, botActor: ActorRef[BotActor.Command], img: Int) extends Command

  case class LeftRoom(id: String, name: String) extends Command

  case class UserDead(roomId: Int, mode: Int, users: List[(String, Int, Int)]) extends Command with RoomManager.Command // (id, kill, area)

  private case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  case class WatchGame(playerId: String, userId: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command

  case class WatcherLeftRoom(userId: String) extends Command

  final case class UserLeft[U](actorRef: ActorRef[U]) extends Command

  private case object Sync extends Command

  case class UserInfo(name: String, startTime: Long, group: Long, img: Int)

  final case class SwitchBehavior(
                                   name: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error")
                                 ) extends Command

  case class TimeOut(msg: String) extends Command

  def create(roomId: Int, mode: Int): Behavior[Command] = {
    log.debug(s"Room Actor-$roomId start...")
    Behaviors.setup[Command] { ctx =>
      Behaviors.withTimers[Command] {
        implicit timer =>
          val grid = new GridOnServer(border)
          val winStandard = fullSize * 0.1//upperLimit
          //            implicit val sendBuffer = new MiddleBufferInJvm(81920)
          val frameRate = mode match {
            case 2 => Protocol.frameRate2
            case _ => Protocol.frameRate1
          }
          log.info(s"frameRate: $frameRate")
          val botsList = AppSettings.botMap.map{b =>
            val id = "bot_"+roomId + b._1
            getBotActor(ctx, id) ! BotActor.InitInfo(b._2, mode, grid, ctx.self)
            (id, b._2)
          }.toList
          roomManager ! RoomManager.BotsJoinRoom(roomId, botsList)
          timer.startPeriodicTimer(SyncKey, Sync, frameRate millis)
          idle(0L, roomId, mode, grid, tickCount = 0l, winStandard = winStandard)
      }
    }
  }

  def idle( index: Long,
            roomId: Int,
            mode: Int,
            grid: GridOnServer,
            userMap: mutable.HashMap[String, UserInfo] = mutable.HashMap[String, UserInfo](),
            userGroup: mutable.HashMap[Long, Set[String]] = mutable.HashMap[Long, Set[String]](),
            userDeadList: mutable.Set[String] = mutable.Set.empty[String],
            watcherMap: mutable.HashMap[String, (String, Long)] = mutable.HashMap[String, (String, Long)](), //(watchId, (playerId, GroupId))
            subscribersMap: mutable.HashMap[String, ActorRef[WsSourceProtocol.WsMsgSource]] = mutable.HashMap[String, ActorRef[WsSourceProtocol.WsMsgSource]](),
            tickCount: Long,
            gameEvent: mutable.ArrayBuffer[(Long, GameEvent)] = mutable.ArrayBuffer[(Long, GameEvent)](),
            winStandard: Double,
            firstComeList: List[String] = List.empty[String],
//            headImgList: mutable.HashMap[String, Int] = mutable.HashMap.empty[String, Int],
            botMap: mutable.HashMap[String, ActorRef[BotActor.Command]] = mutable.HashMap[String, ActorRef[BotActor.Command]]()
          )(
            implicit timer: TimerScheduler[Command]
          ): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case m@JoinRoom(id, name, subscriber, img) =>
          log.info(s"got JoinRoom $m")
          userMap.put(id, UserInfo(name, System.currentTimeMillis(), index%classify, img))
          subscribersMap.put(id, subscriber)
          log.debug(s"subscribersMap: $subscribersMap")
//          ctx.watchWith(subscriber, UserLeft(subscriber))
          grid.addSnake(id, roomId, name, img)
          dispatchTo(subscribersMap, id, Protocol.Id(id))
          userGroup.get(index%classify) match {
            case Some(s)=> userGroup.update(index%classify, s + id)
            case None => userGroup.put(index%classify, Set(id))
          }
          gameEvent += ((grid.frameCount, JoinEvent(id, name)))
//          headImgList.put(id, img)
          if(userMap.size > AppSettings.minPlayerNum && botMap.nonEmpty){
            val killBot = botMap.head
            val name = userMap.getOrElse(killBot._1, UserInfo("", -1L, -1L, 0)).name
            botMap.-=(killBot._1)
            userMap.-=(killBot._1)
            getBotActor(ctx, killBot._1) ! BotActor.KillBot
            ctx.self ! LeftRoom(killBot._1, name)
          }
          idle(index + 1, roomId, mode, grid, userMap, userGroup, userDeadList, watcherMap, subscribersMap, tickCount, gameEvent, winStandard, id::firstComeList, botMap)

        case JoinRoom4Bot(id, name, botActor, img) =>
          userMap.put(id, UserInfo(name, System.currentTimeMillis(), index % classify, img))
          botMap.put(id, botActor)
          grid.addSnake(id, roomId, name, img)
          dispatchTo(subscribersMap, id, Protocol.Id(id))
          userGroup.get(index % classify) match {
            case Some(s) => userGroup.update(index % classify, s + id)
            case None => userGroup.put(index % classify, Set(id))
          }
          gameEvent += ((grid.frameCount, JoinEvent(id, name)))
//          headImgList.put(id, img)
          idle(index + 1, roomId, mode, grid, userMap, userGroup, userDeadList, watcherMap, subscribersMap, tickCount, gameEvent, winStandard, id :: firstComeList, botMap)

        case m@WatchGame(playerId, userId, subscriber) =>
          log.info(s"got: $m")
          val truePlayerId = if (playerId == "unknown") userMap.head._1 else playerId
          watcherMap.put(userId, (truePlayerId, index%classify))
          subscribersMap.put(userId, subscriber)
          ctx.watchWith(subscriber, WatcherLeftRoom(userId))
          dispatchTo(subscribersMap, userId, Protocol.Id4Watcher(truePlayerId, userId))
          val img = if(userMap.get(playerId).nonEmpty) userMap.get(playerId).head.img else 0
          dispatchTo(subscribersMap, userId, Protocol.StartWatching(mode, img))
          val gridData = grid.getGridData
          dispatch(subscribersMap, gridData)
          userGroup.get(index%classify) match {
            case Some(s) => userGroup.update(index % classify, s + userId)
            case None => userGroup.put(index % classify, Set(userId))
          }
          idle(index + 1, roomId, mode, grid, userMap, userGroup, userDeadList, watcherMap, subscribersMap, tickCount, gameEvent, winStandard, firstComeList, botMap)

        case UserDead(_, _, users) =>
          users.foreach { u =>
            val id = u._1
            if(userMap.get(id).nonEmpty) {
              val name = userMap(id).name
              val startTime = userMap(id).startTime
              grid.cleanSnakeTurnPoint(id)
              gameEvent += ((grid.frameCount, LeftEvent(id, name)))
//              log.debug(s"user $id dead:::::")
              val endTime = System.currentTimeMillis()
              dispatchTo(subscribersMap, id, Protocol.DeadPage(id, u._2, u._3, startTime, endTime))
              watcherMap.filter(_._2._1==id).foreach(user => dispatchTo(subscribersMap, user._1, Protocol.DeadPage(id, u._2, u._3, startTime, endTime)))
              log.debug(s"watchMap: ${watcherMap.filter(_._2._1==id)}, watchedId: $id")
              //上传战绩
              if(subscribersMap.get(id).nonEmpty){ //bot的战绩不上传
                val msgFuture: Future[String] = tokenActor ? AskForToken
                msgFuture.map { token =>
                  EsheepClient.inputBatRecord(id, name, u._2, 1, u._3.toFloat*100 / fullSize, "", startTime, endTime, token)
                }
                PlayerRecordDAO.addPlayerRecord(id, name, u._2, 1, u._3.toFloat*100 / fullSize, startTime, endTime)
              } else getBotActor(ctx, id) ! BotDead //bot死亡消息发送
            }
            userDeadList += id
          }
          Behaviors.same

        case LeftRoom(id, name) =>
          log.debug(s"LeftRoom:::$id")
          if(userDeadList.contains(id)) userDeadList -= id
          grid.removeSnake(id)
          userMap.filter(_._1 == id).foreach{ u =>
            userGroup.get(u._2.group) match {
              case Some(s) => userGroup.update(u._2.group,s - id)
              case None => userGroup.put(u._2.group, Set.empty)
            }
          }
          subscribersMap.get(id).foreach(r => ctx.unwatch(r))
          userMap.remove(id)
          subscribersMap.remove(id)
          dispatch(subscribersMap, Protocol.UserLeft(id))
          watcherMap.filter(_._2._1 == id).keySet.foreach { i =>
            subscribersMap.remove(i)
          }
//          if (headImgList.contains(id)) headImgList.remove(id)
          if (!userDeadList.contains(id)) gameEvent += ((grid.frameCount, LeftEvent(id, name))) else userDeadList -= id
          if(userMap.size < AppSettings.minPlayerNum){
            println("1:" + userMap.size + "--" + "2:" + botMap.size)
            if(botMap.size == userMap.size){
              botMap.foreach(b => getBotActor(ctx, b._1) ! KillBot)
              Behaviors.stopped
            } else {
              if(!AppSettings.botMap.forall(b => botMap.keys.toList.contains("bot_" + roomId + b._1))) {
                val newBot = AppSettings.botMap.filterNot(b => botMap.keys.toList.contains("bot_" + roomId + b._1)).head
                getBotActor(ctx, newBot._1) ! BotActor.InitInfo(newBot._2, mode, grid, ctx.self)
              }
              Behaviors.same
            }
          } else Behaviors.same


        case WatcherLeftRoom(uid) =>
          log.debug(s"WatcherLeftRoom:::$uid")
          subscribersMap.get(uid).foreach(r => ctx.unwatch(r))//
          subscribersMap.remove(uid)
          watcherMap.remove(uid)
          Behaviors.same

        case UserLeft(actor) =>
//          ctx.unwatch(actor)
          log.debug(s"UserLeft:::")
          val subscribersOpt = subscribersMap.find(_._2.equals(actor))
          if(subscribersOpt.nonEmpty){
            val (id, _) = subscribersOpt.get
            log.debug(s"got Terminated id = $id")
            //            if(userDeadList.contains(id)) userDeadList -= id
            val name = userMap.get(id).head.name
            userMap.filter(_._1 == id).foreach{ u =>
              userGroup.get(u._2.group) match {
                case Some(s) => userGroup.update(u._2.group,s - id)
                case None => userGroup.put(u._2.group, Set.empty)
              }
            }
//            if (headImgList.contains(id)) headImgList.remove(id)
            subscribersMap.remove(id)
            userMap.remove(id)
            grid.cleanSnakeTurnPoint(id)
            grid.removeSnake(id).foreach { s => dispatch(subscribersMap, Protocol.SnakeLeft(id, s.name)) }
            roomManager ! RoomManager.UserLeft(id)
            gameEvent += ((grid.frameCount, LeftEvent(id, name)))
            if (!userDeadList.contains(id)) gameEvent += ((grid.frameCount, LeftEvent(id, name))) else userDeadList -= id
          }
          if (userMap.isEmpty) Behaviors.stopped else Behaviors.same

        case UserActionOnServer(id, action) =>
          action match {
            case Key(_, keyCode, frameCount, actionId) =>
              if (keyCode == KeyEvent.VK_SPACE && userDeadList.contains(id)) {
                val info = userMap.getOrElse(id, UserInfo("", -1L, -1L, 0))
                userMap.put(id, UserInfo(info.name, System.currentTimeMillis(), info.group, info.img))
//                log.debug(s"recv space from id ====$id")
//                if (headImgList.contains(id)) {
                grid.addSnake(id, roomId, info.name, info.img)
//                } else {
//                  log.error(s"can not find headImg of $id")
//                  grid.addSnake(id, roomId, userMap.getOrElse(id, UserInfo("", -1L, -1L)).name, new Random().nextInt(6))
//                }
                gameEvent += ((grid.frameCount, JoinEvent(id, userMap(id).name)))
                watcherMap.filter(_._2._1 == id).foreach { w =>
                  log.info(s"send reStart to ${w._1}")
                  dispatchTo(subscribersMap, w._1, Protocol.ReStartGame)
                }
                gameEvent += ((grid.frameCount, SpaceEvent(id)))
                userDeadList -= id
              } else {
                val realFrame = if (frameCount >= grid.frameCount) frameCount else grid.frameCount
                grid.addActionWithFrame(id, keyCode, realFrame)
                dispatch(subscribersMap, Protocol.SnakeAction(id, keyCode, realFrame, actionId))
              }
            case SendPingPacket(_, createTime) =>
              dispatchTo(subscribersMap, id, Protocol.ReceivePingPacket(createTime))

            case NeedToSync(_) =>
              dispatchTo(subscribersMap, id, grid.getGridData)

            case _ =>
          }
          Behaviors.same


        case Sync =>
          val frame = grid.frameCount //即将执行改帧的数据
          val shouldNewSnake = if (grid.waitingListState) true else false
          val shouldSync = if (tickCount % 20 == 1) true else false
//          val waitingSnakesList = grid.waitingJoinList
          val finishFields = grid.updateInService(shouldNewSnake, roomId, mode) //frame帧的数据执行完毕
          val newData = grid.getGridData
          var newField: List[FieldByColumn] = Nil
//          val killedSkData = grid.getKilledSkData
          grid.killHistory.map(k => Kill(k._1, k._2._1, k._2._2, k._2._3)).toList.foreach {
            i =>
              if (i.frameCount + 1 == newData.frameCount) {
                dispatch(subscribersMap, Protocol.SomeOneKilled(i.killedId, userMap(i.killedId).name, i.killerName))
                gameEvent += ((grid.frameCount, Protocol.SomeOneKilled(i.killedId, userMap(i.killedId).name, i.killerName)))
              }
          }

          if(grid.newInfo.nonEmpty) {
            newField = grid.newInfo.map(n => (n._1, n._3)).map { f =>
              if (f._1.take(3) == "bot") getBotActor(ctx, f._1) ! BackToGame
              FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
                ScanByColumn(y.toInt, Tool.findContinuous(target.map(_.x.toInt).toArray.sorted))//read
              }.toList)
            }
            dispatch(subscribersMap, NewSnakeInfo(grid.frameCount, grid.newInfo.map(_._2), newField))
            grid.newInfo = Nil
          }

          firstComeList.foreach { id =>
            dispatchTo(subscribersMap, id, newData)
          }

          //错峰发送
          if (shouldSync) {
            val chooseGroup = (tickCount % 100) / 20
            userGroup.get(chooseGroup).foreach {g =>
              if (g.nonEmpty) {
                dispatch(subscribersMap.filter(s => g.contains(s._1)), newData)
              }
            }
          }

          if (finishFields.nonEmpty) { //发送圈地数据
            newField = finishFields.map { f =>
              FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
                ScanByColumn(y.toInt, Tool.findContinuous(target.map(_.x.toInt).toArray.sorted))//read
              }.toList)
            }

            userMap.foreach(u => dispatchTo(subscribersMap, u._1, NewFieldInfo(grid.frameCount, newField)))
            watcherMap.foreach(u => dispatchTo(subscribersMap, u._1, NewFieldInfo(grid.frameCount, newField)))
          }

          if (grid.currentRank.nonEmpty && grid.currentRank.head.area >= winStandard) { //判断是否胜利
            log.info(s"currentRank: ${grid.currentRank}")
            log.debug("winwinwinwin!!")
            val finalData = grid.getGridData
            grid.cleanData()
            userMap.foreach{ u =>
              if(u._1 == grid.currentRank.head.id)
                dispatchTo(subscribersMap,u._1,Protocol.WinData(grid.currentRank.head.area,None))
              else
                dispatchTo(subscribersMap,u._1,Protocol.WinData(grid.currentRank.head.area,grid.currentRank.filter(_.id == u._1).map(_.area).headOption))

            }
            dispatch(subscribersMap,Protocol.SomeOneWin(userMap(grid.currentRank.head.id).name, finalData))
            dispatchTo(subscribersMap, grid.currentRank.head.id, Protocol.WinnerBestScore(grid.currentRank.head.area))
            gameEvent += ((grid.frameCount, Protocol.SomeOneWin(userMap(grid.currentRank.head.id).name, finalData)))
            userMap.foreach { u =>
              if (!userDeadList.contains(u._1)) {
                gameEvent += ((grid.frameCount, LeftEvent(u._1, u._2.name)))
                userDeadList += u._1
                if(u._1.take(3) == "bot") {
                  botMap.-=(u._1)
                  getBotActor(ctx, u._1) ! BotDead
                }
              }
            }
          }

          val count = tickCount % 10
           if(count % 2 == 0) {
             userGroup.get(count / 2).foreach { g =>
               if (g.nonEmpty) {
                 dispatch(subscribersMap.filter(s => g.contains(s._1)), Protocol.Ranks(grid.currentRank))
               }
             }
           }
//          if (tickCount % 10 == 3) dispatch(subscribersMap, Protocol.Ranks(grid.currentRank))
          val newWinStandard = if (grid.currentRank.nonEmpty) { //胜利条件的跳转
            val maxSize = grid.currentRank.head.area
            if ((maxSize + fullSize * 0.1) < winStandard) fullSize * Math.max(upperLimit - userMap.size * 0.05, lowerLimit) else winStandard
          } else winStandard

          //for gameRecorder...
          val actionEvent = grid.getDirectionEvent(frame)
          val joinOrLeftEvent = gameEvent.filter(_._1 == frame)

          val baseEvent = if (tickCount % 10 == 3) RankEvent(grid.currentRank) :: (actionEvent ::: joinOrLeftEvent.map(_._2).toList) else actionEvent ::: joinOrLeftEvent.map(_._2).toList
          gameEvent --= joinOrLeftEvent
          val snapshot = Snapshot(newData.snakes, newData.bodyDetails, newData.fieldDetails)
          //          val snapshot = Snapshot(newData.snakes, newData.bodyDetails, newData.fieldDetails, newData.killHistory)
          val recordData = if (finishFields.nonEmpty) RecordData(frame, (EncloseEvent(newField) :: baseEvent, snapshot)) else RecordData(frame, (baseEvent, snapshot))
          if (grid.snakes.nonEmpty || ctx.child("gameRecorder").nonEmpty) getGameRecorder(ctx, roomId, grid, mode) ! recordData
          idle(index, roomId, mode, grid, userMap, userGroup, userDeadList, watcherMap, subscribersMap, tickCount + 1, gameEvent, newWinStandard, botMap = botMap)

        case ChildDead(child, childRef) =>
          log.debug(s"roomActor 不再监管 gameRecorder:$child,$childRef")
          ctx.unwatch(childRef)
          Behaviors.same

        case _ =>
          log.warn(s"${ctx.self.path} recv a unknow msg=$msg")
          Behaviors.unhandled
      }
    }

  }

  def dispatchTo(subscribers: mutable.HashMap[String, ActorRef[WsSourceProtocol.WsMsgSource]], id: String, gameOutPut: Protocol.GameMessage): Unit = {
    subscribers.get(id).foreach {
      _ ! gameOutPut
    }
  }

  def dispatch(subscribers: mutable.HashMap[String, ActorRef[WsSourceProtocol.WsMsgSource]], gameOutPut: Protocol.GameMessage): Unit = {
    subscribers.values.foreach {
      _ ! gameOutPut
    }
  }

  private def getGameRecorder(ctx: ActorContext[Command], roomId: Int, grid: GridOnServer, mode: Int): ActorRef[GameRecorder.Command] = {
    val childName = "gameRecorder"
    ctx.child(childName).getOrElse {
      val newData = grid.getGridData
      val actor = ctx.spawn(GameRecorder.create(roomId, Snapshot(newData.snakes, newData.bodyDetails, newData.fieldDetails),
        GameInformation(roomId, System.currentTimeMillis(), 0, grid.frameCount, mode)), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.upcast[GameRecorder.Command]
  }

  private def getBotActor(ctx: ActorContext[Command], botId: String) = {
    val childName = botId
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(BotActor.create(botId), childName)
      actor
    }.upcast[BotActor.Command]
  }


}
