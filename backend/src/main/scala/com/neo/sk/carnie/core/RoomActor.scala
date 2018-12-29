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

  private val decreaseRate = AppSettings.decreaseRate

  private val fullSize = (BorderSize.w - 2) * (BorderSize.h - 2)

  private val maxWaitingTime4Restart = 3000


  //  private val classify = 5

  private final case object SyncKey

  sealed trait Command

  case class UserActionOnServer(id: String, action: Protocol.UserAction) extends Command

  case class JoinRoom(id: String, name: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource], img: Int) extends Command

  case class JoinRoom4Bot(id: String, name: String, botActor: ActorRef[BotActor.Command], img: Int) extends Command

  case class LeftRoom(id: String, name: String) extends Command

  case class UserDead(roomId: Int, mode: Int, users: List[(String, Short, Short)]) extends Command with RoomManager.Command // (id, kill, area)

  private case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  case class WatchGame(playerId: String, userId: String, subscriber: ActorRef[WsSourceProtocol.WsMsgSource]) extends Command

  case class WatcherLeftRoom(userId: String) extends Command

  final case class UserLeft[U](actorRef: ActorRef[U]) extends Command

  private case object Sync extends Command

  case class UserInfo(name: String, startTime: Long, joinFrame: Long, img: Int)

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
          idle(roomId, mode, grid, tickCount = 0l, winStandard = winStandard)
      }
    }
  }

  def idle( roomId: Int,
            mode: Int,
            grid: GridOnServer,
            userMap: mutable.HashMap[String, UserInfo] = mutable.HashMap[String, UserInfo](),
//            userGroup: mutable.HashMap[Long, Set[String]] = mutable.HashMap[Long, Set[String]](),
            userDeadList: mutable.HashMap[String, Long] = mutable.HashMap.empty[String, Long],
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
          userMap.put(id, UserInfo(name, System.currentTimeMillis(), tickCount, img))
          subscribersMap.put(id, subscriber)
          log.debug(s"subscribersMap: $subscribersMap")
//          ctx.watchWith(subscriber, UserLeft(subscriber))
          grid.addSnake(id, roomId, name, img)
          dispatchTo(subscribersMap, id, Protocol.Id(id))
//          userGroup.get(index%classify) match {
//            case Some(s)=> userGroup.update(index%classify, s + id)
//            case None => userGroup.put(index%classify, Set(id))
//          }
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
          idle(roomId, mode, grid, userMap, userDeadList, watcherMap, subscribersMap, tickCount, gameEvent, winStandard, id::firstComeList, botMap)

        case JoinRoom4Bot(id, name, botActor, img) =>
          userMap.put(id, UserInfo(name, System.currentTimeMillis(), -1L, img))
          botMap.put(id, botActor)
          grid.addSnake(id, roomId, name, img)
          dispatchTo(subscribersMap, id, Protocol.Id(id))
//          userGroup.get(index % classify) match {
//            case Some(s) => userGroup.update(index % classify, s + id)
//            case None => userGroup.put(index % classify, Set(id))
//          }
          gameEvent += ((grid.frameCount, JoinEvent(id, name)))
//          headImgList.put(id, img)
          idle(roomId, mode, grid, userMap, userDeadList, watcherMap, subscribersMap, tickCount, gameEvent, winStandard, firstComeList, botMap)

        case m@WatchGame(playerId, userId, subscriber) =>
          log.info(s"got: $m")
          val truePlayerId = if (playerId == "unknown") userMap.head._1 else playerId
          watcherMap.put(userId, (truePlayerId, tickCount))
          subscribersMap.put(userId, subscriber)
          ctx.watchWith(subscriber, WatcherLeftRoom(userId))
          dispatchTo(subscribersMap, userId, Protocol.Id4Watcher(truePlayerId, userId))
          val img = if(userMap.get(playerId).nonEmpty) userMap.get(playerId).head.img else 0
          dispatchTo(subscribersMap, userId, Protocol.StartWatching(mode, img))
          val gridData = grid.getGridData
          dispatch(subscribersMap, gridData)
//          userGroup.get(index%classify) match {
//            case Some(s) => userGroup.update(index % classify, s + userId)
//            case None => userGroup.put(index % classify, Set(userId))
//          }
          idle(roomId, mode, grid, userMap, userDeadList, watcherMap, subscribersMap, tickCount, gameEvent, winStandard, firstComeList, botMap)

        case UserDead(_, _, users) =>
          val frame = grid.frameCount
          val curTime = System.currentTimeMillis()
          users.foreach { u =>
            val id = u._1
            grid.killHistory.filter(k => k._2._3 + 1 == grid.frameCount).foreach { k => //弹幕的推送
              if (userMap.get(k._1).nonEmpty) {
                dispatch(subscribersMap.filter(s => userMap.getOrElse(s._1, UserInfo("", -1L, -1L, 0)).joinFrame != -1L),
                  Protocol.SomeOneKilled(k._1, userMap(k._1).name, k._2._2))
                gameEvent += ((grid.frameCount, Protocol.SomeOneKilled(k._1, userMap(k._1).name, k._2._2)))
              }
            }
            if(userMap.get(id).nonEmpty) {
              var killerId :Option[String] = None
              var killerName :Option[String] = None
              val name = userMap(id).name
              val startTime = userMap(id).startTime
              grid.cleanSnakeTurnPoint(id)
              if(grid.killHistory.filter(k => k._2._3 + 1 == frame).get(id).isDefined){
                killerId = Some(grid.killHistory.filter(k => k._2._3 + 1 == frame)(id)._1)
                killerName = Some(grid.killHistory.filter(k => k._2._3 + 1 == frame)(id)._2)
              }

              gameEvent += ((frame, LeftEvent(id, name)))
              gameEvent += ((frame, Protocol.UserDead(frame, id, name, killerName)))
//              log.debug(s"user $id dead:::::")
              val endTime = System.currentTimeMillis()
              dispatchTo(subscribersMap, id, Protocol.DeadPage(id, u._2, u._3, ((endTime - startTime) / 1000).toShort))
              dispatch(subscribersMap, Protocol.UserDead(frame, id, name, killerName))
              val info = userMap(id).copy(joinFrame = -1L) //死了之后不发消息
              userMap.update(id, info)
              watcherMap.filter(_._2._1==id).foreach { user =>
                dispatchTo(subscribersMap, user._1, Protocol.DeadPage(id, u._2, u._3, ((endTime - startTime) / 1000).toShort))
                dispatchTo(subscribersMap,user._1, Protocol.UserDead(frame, id, name, killerName))
                val watcherInfo = watcherMap(user._1).copy(_2 = -1L)
                watcherMap.update(user._1, watcherInfo)
              }
//              log.debug(s"watchMap: ${watcherMap.filter(_._2._1==id)}, watchedId: $id")
              //上传战绩
              if(subscribersMap.get(id).nonEmpty){ //bot的战绩不上传
                val msgFuture: Future[String] = tokenActor ? AskForToken
                msgFuture.map { token =>
                  EsheepClient.inputBatRecord(id, name, u._2, 1, u._3.toFloat*100 / fullSize, "", startTime, endTime, token)
                }
                PlayerRecordDAO.addPlayerRecord(id, name, u._2, 1, u._3.toFloat*100 / fullSize, startTime, endTime)
              } else getBotActor(ctx, id) ! BotDead //bot死亡消息发送
            }
            userDeadList += id -> curTime
          }
          Behaviors.same

        case LeftRoom(id, name) =>
          log.debug(s"LeftRoom:::$id")
          if(userDeadList.contains(id)) userDeadList -= id
          grid.removeSnake(id)
          grid.cleanSnakeTurnPoint(id)
//          userMap.filter(_._1 == id).foreach{ u =>
//            userGroup.get(u._2.group) match {
//              case Some(s) => userGroup.update(u._2.group,s - id)
//              case None => userGroup.put(u._2.group, Set.empty)
//            }
//          }
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
//            println("1:" + userMap.size + "--" + "2:" + botMap.size)
            if(botMap.size == userMap.size){
              botMap.foreach(b => getBotActor(ctx, b._1) ! KillBot)
              Behaviors.stopped
            } else {
//              log.debug(s"botMap:::$botMap")
              if(!AppSettings.botMap.forall(b => botMap.keys.toList.contains("bot_" + roomId + b._1))) {
                val newBot = AppSettings.botMap.filterNot(b => botMap.keys.toList.contains("bot_" + roomId + b._1)).head
                getBotActor(ctx, "bot_" + roomId + newBot._1) ! BotActor.InitInfo(newBot._2, mode, grid, ctx.self)
              }
              Behaviors.same
            }
          } else Behaviors.same


        case WatcherLeftRoom(uid) =>
          log.debug(s"WatcherLeftRoom:::$uid")
          subscribersMap.get(uid).foreach(r => ctx.unwatch(r))//
          subscribersMap.remove(uid)
          if(watcherMap.contains(uid)) {
//            val groupId = watcherMap(uid)._2
//            userGroup.get(groupId) match {
//              case Some(s) => userGroup.update(groupId,s - uid)
//              case None => userGroup.put(groupId, Set.empty)
//            }
            watcherMap.remove(uid)
          }
//          watcherMap.remove(uid)
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
          val curTime = System.currentTimeMillis()
          action match {
            case Key(keyCode, frameCount, actionId) =>
              val realFrame = grid.checkActionFrame(id, frameCount)
              //                if (frameCount >= grid.frameCount) frameCount else grid.frameCount
              //              else Math.max(grid.frameCount, grid.actionMap.keys.toList.sorted.headOption.getOrElse(-1l) + 1)
              grid.addActionWithFrame(id, keyCode, realFrame)
              dispatch(subscribersMap.filter(s => userMap.getOrElse(s._1, UserInfo("", -1L, -1L, 0)).joinFrame != -1L ||
                (userDeadList.contains(s._1) &&  curTime - userDeadList(s._1) <= maxWaitingTime4Restart)), //死亡时间小于3s继续发消息
                Protocol.SnakeAction(id, keyCode, realFrame, actionId))

            case SendPingPacket(createTime) =>
              dispatchTo(subscribersMap, id, Protocol.ReceivePingPacket(createTime))

            case NeedToSync =>
              dispatchTo(subscribersMap, id, grid.getGridData)

            case PressSpace =>
              if (userDeadList.contains(id)) {
                val info = userMap.getOrElse(id, UserInfo("", -1L, -1L, 0))
//                userMap.put(id, UserInfo(info.name, System.currentTimeMillis(), tickCount, info.img))
                grid.addSnake(id, roomId, info.name, info.img)
                gameEvent += ((grid.frameCount, JoinEvent(id, userMap(id).name)))
                watcherMap.filter(_._2._1 == id).foreach { w =>
                  log.info(s"send reStart to ${w._1}")
                  dispatchTo(subscribersMap, w._1, Protocol.ReStartGame)
                }
                gameEvent += ((grid.frameCount, SpaceEvent(id)))
//                userDeadList -= id
              }
            case _ =>
          }
          Behaviors.same


        case Sync =>
          val curTime = System.currentTimeMillis()
          val frame = grid.frameCount //即将执行改帧的数据
          val shouldNewSnake = if (grid.waitingListState) true else false
          val finishFields = grid.updateInService(shouldNewSnake, roomId, mode) //frame帧的数据执行完毕
          val newData = grid.getGridData
          var newField: List[FieldByColumn] = Nil

//          grid.killHistory.filter(k => k._2._3 + 1 == newData.frameCount).foreach { k => //弹幕的推送
//            if (userMap.get(k._1).nonEmpty) {
//              dispatch(subscribersMap.filter(s => userMap.getOrElse(s._1, UserInfo("", -1L, -1L, 0)).joinFrame != -1L),
//                Protocol.SomeOneKilled(k._1, userMap(k._1).name, k._2._2))
//              gameEvent += ((grid.frameCount, Protocol.SomeOneKilled(k._1, userMap(k._1).name, k._2._2)))
//            }
//          }

          if(grid.newInfo.nonEmpty) { //有新的蛇
            newField = grid.newInfo.map(n => (n._1, n._3)).map { f =>
              if (userDeadList.contains(f._1) && curTime - userDeadList(f._1) > maxWaitingTime4Restart)
                dispatchTo(subscribersMap, f._1, newData) //同步全量数据
              val info = userMap.getOrElse(f._1, UserInfo("", -1L, -1L, 0))
              userMap.put(f._1, UserInfo(info.name, System.currentTimeMillis(), tickCount, info.img))
              userDeadList -= f._1
              if (f._1.take(3) == "bot") getBotActor(ctx, f._1) ! BackToGame
              FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
                (y.toShort, Tool.findContinuous(target.map(_.x.toShort).toArray.sorted))//read
              }.toList.groupBy(_._2).map { case (r, target) =>
                ScanByColumn(Tool.findContinuous(target.map(_._1).toArray.sorted), r)
              }.toList)
            }
            dispatch(
              subscribersMap.filter(s => userMap.getOrElse(s._1, UserInfo("", -1L, -1L, 0)).joinFrame != -1L ||
                (userDeadList.contains(s._1) &&  curTime - userDeadList(s._1) <= maxWaitingTime4Restart)), //死亡时间小于3s继续发消息
              NewSnakeInfo(grid.frameCount, grid.newInfo.map(_._2), newField))
            grid.newInfo = Nil
          }

          firstComeList.foreach { id =>
            dispatchTo(subscribersMap, id, newData)
          }

          //错峰发送
          for((u, i) <- userMap) {
            val newDataNoField = Protocol.Data4TotalSync(newData.frameCount, newData.snakes, newData.bodyDetails, Nil)
            if (i.joinFrame != -1L && (tickCount - i.joinFrame) % 100 == 2 ||
              (userDeadList.contains(u) &&  curTime - userDeadList(u) <= maxWaitingTime4Restart))
              dispatchTo(subscribersMap, u, newDataNoField)
            if ((i.joinFrame != -1L && (tickCount - i.joinFrame) % 20 == 5 ||
              (userDeadList.contains(u) &&  curTime - userDeadList(u) <= maxWaitingTime4Restart)) && grid.currentRank.exists(_.id == u))
              dispatchTo(subscribersMap, u, Protocol.Ranks(grid.currentRank.take(5), grid.currentRank.filter(_.id == u).head,
                (grid.currentRank.indexOf(grid.currentRank.filter(_.id == u).head) + 1).toByte))
          }

          if (finishFields.nonEmpty) { //发送圈地数据
            newField = finishFields.map { f =>
              FieldByColumn(f._1, f._2.groupBy(_.y).map { case (y, target) =>
                (y.toShort, Tool.findContinuous(target.map(_.x.toShort).toArray.sorted))//read
              }.toList.groupBy(_._2).map { case (r, target) =>
                ScanByColumn(Tool.findContinuous(target.map(_._1).toArray.sorted), r)
              }.toList)
            }

            userMap.filter(s =>s._2.joinFrame != -1L ||
              (userDeadList.contains(s._1) &&  curTime - userDeadList(s._1) <= maxWaitingTime4Restart)).foreach(u =>
              dispatchTo(subscribersMap, u._1, NewFieldInfo(grid.frameCount, newField)))
            watcherMap.filter(w =>
              userMap.get(w._2._1) match {
                case None => false
                case Some(userInfo) if userDeadList.contains(w._2._1) && curTime - userDeadList(w._2._1) > maxWaitingTime4Restart => false
                case _ => true
              }
            ).foreach(u => dispatchTo(subscribersMap, u._1, NewFieldInfo(grid.frameCount, newField)))
          }

          if (grid.currentRank.nonEmpty && grid.currentRank.head.area >= winStandard) { //判断是否胜利
            log.info(s"win!! currentRank: ${grid.currentRank}")
            val finalData = grid.getGridData
            grid.cleanData()
            userMap.foreach { u =>
              if (u._1 == grid.currentRank.head.id)
                dispatchTo(subscribersMap, u._1, Protocol.WinData(grid.currentRank.head.area, None))
              else
                dispatchTo(subscribersMap, u._1, Protocol.WinData(grid.currentRank.head.area, grid.currentRank.filter(_.id == u._1).map(_.area).headOption))
            }
            dispatch(subscribersMap,Protocol.SomeOneWin(userMap(grid.currentRank.head.id).name))
            dispatchTo(subscribersMap, grid.currentRank.head.id, Protocol.WinnerBestScore(grid.currentRank.head.area))
            gameEvent += ((grid.frameCount, Protocol.SomeOneWin(userMap(grid.currentRank.head.id).name)))
            userMap.foreach { u =>
              if (!userDeadList.contains(u._1)) {
                gameEvent += ((grid.frameCount, LeftEvent(u._1, u._2.name)))
                userDeadList += u._1 -> curTime
                if(u._1.take(3) == "bot") {
                  botMap.-=(u._1)
                  getBotActor(ctx, u._1) ! BotDead
                }
              }
            }
          }

          val newWinStandard = if (grid.currentRank.nonEmpty) { //胜利条件的调整
            val maxSize = grid.currentRank.head.area
            if ((maxSize + fullSize * 0.1) < winStandard) fullSize * Math.max(upperLimit - userMap.size * decreaseRate, lowerLimit) else winStandard
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
          idle(roomId, mode, grid, userMap, userDeadList, watcherMap, subscribersMap, tickCount + 1, gameEvent, newWinStandard, botMap = botMap)

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
