package com.neo.sk.carnie.core

import akka.actor.typed.{Behavior, PostStop}
import com.neo.sk.carnie.paperClient.Protocol
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.carnie.common.AppSettings
import com.neo.sk.carnie.models.SlickTables
import com.neo.sk.carnie.models.dao.RecordDAO
import com.neo.sk.carnie.paperClient.Protocol._
import com.neo.sk.utils.essf.RecordGame.getRecorder
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.byteobject.ByteObject._
import org.seekloud.essf.io.FrameOutputStream
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.util.{Failure, Success}
import com.neo.sk.carnie.Boot.executor

/**
  * Created by dry on 2018/10/19.
  */
object GameRecorder {

  sealed trait Command

  final case class RecordData(frame: Long, event: (List[Protocol.GameEvent], Protocol.Snapshot)) extends Command

  final case object SaveDate extends Command

  final case object SaveInFile extends Command

  private final case object BehaviorChangeKey

  private final case object SaveDateKey

  private final val saveTime = 10.minute

  private val maxRecordNum = 100

  final case class SwitchBehavior(
                                   name: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error")
                                 ) extends Command

  case class TimeOut(msg: String) extends Command

  private val log = LoggerFactory.getLogger(this.getClass)

  private[this] def getFileName(roomId: Int, startTime: Long) = s"carnie_${roomId}_$startTime"

  def create(roomId: Int, initState: Snapshot, gameInfo: GameInformation): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      log.info(s"${ctx.self.path} is starting..")
      implicit val stashBuffer: StashBuffer[GameRecorder.Command] = StashBuffer[Command](Int.MaxValue)
      implicit val middleBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(10 * 4096)
      Behaviors.withTimers[Command] { implicit timer =>
        timer.startSingleTimer(SaveDateKey, SaveInFile, saveTime)
        val recorder: FrameOutputStream = getRecorder(getFileName(roomId, gameInfo.startTime), gameInfo.index, gameInfo, Some(initState))
        idle(recorder, gameInfo, lastFrame = gameInfo.initFrame)
      }
    }
  }

  def idle(recorder: FrameOutputStream,
           gameInfo: GameInformation,
           essfMap: mutable.HashMap[UserBaseInfo,List[UserJoinLeft]] = mutable.HashMap[UserBaseInfo,List[UserJoinLeft]](),
           userMap: mutable.HashMap[String, String] = mutable.HashMap[String, String](),
           userHistoryMap: mutable.HashMap[String, String] = mutable.HashMap[String, String](),
           eventRecorder: List[(List[Protocol.GameEvent], Option[Protocol.Snapshot])] = Nil,
           lastFrame: Long,
           tickCount: Long = 1
          )(implicit stashBuffer: StashBuffer[Command],
            timer: TimerScheduler[Command],
            middleBuffer: MiddleBufferInJvm): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case RecordData(frame, event) => //记录数据
          val snapshot =
            if(event._1.exists{
//              case JoinEvent(_, info) =>
//                println(s"add joinEvent: $info")
//                true
//              case LeftEvent(_, _) => true
//              case SpaceEvent(_) => true
//              case _ => false
              case Protocol.DirectionEvent(_,_) => false
              case Protocol.EncloseEvent(_) => false
              case Protocol.RankEvent(_) => false
              case _ => true
            } || tickCount % 50 == 0) Some(event._2) else None //是否做快照

//          log.debug(s"${event._1.exists{case Protocol.DirectionEvent(_,_) => false case Protocol.EncloseEvent(_) => false case _ => true}}")
//          log.debug(s"快照::tickcount:$tickCount, snapshot:$snapshot")

          event._1.foreach {
            case Protocol.JoinEvent(id, info) =>
              userMap.put(id, info.get.name)
              userHistoryMap.put(id, info.get.name)
              if(essfMap.get(UserBaseInfo(id, info.get.name)).nonEmpty) {
                essfMap.put(UserBaseInfo(id, info.get.name), essfMap(UserBaseInfo(id, info.get.name)) ::: List(UserJoinLeft(frame, -1l)))
              } else {
                essfMap.put(UserBaseInfo(id, info.get.name), List(UserJoinLeft(frame, -1l)))
              }

            case Protocol.LeftEvent(id, nickName) =>
              userMap.put(id, nickName)
              essfMap.get(UserBaseInfo(id, nickName)) match {
                case Some(joinOrLeftInfo) =>
                  if(joinOrLeftInfo.lengthCompare(1) == 0)
                  essfMap.put(UserBaseInfo(id, nickName), List(UserJoinLeft(joinOrLeftInfo.head.joinFrame, frame)))
                  else {
                    val join = joinOrLeftInfo.filter(_.leftFrame == -1l).head.joinFrame
                    List(UserJoinLeft(joinOrLeftInfo.head.joinFrame, frame)).filterNot(_.leftFrame == -1l) ::: List(UserJoinLeft(join, frame))
                  }
                case None => log.warn(s"get ${UserBaseInfo(id, nickName)} from essfMap error..")
              }


            case _ =>
          }

          var newEventRecorder =  (event._1, snapshot) :: eventRecorder

          if (newEventRecorder.lengthCompare(maxRecordNum) > 0) { //每一百帧写入一次
            newEventRecorder.reverse.foreach {
              case (events, Some(state)) if events.nonEmpty =>
                recorder.writeFrame(events.fillMiddleBuffer(middleBuffer).result(), Some(state.fillMiddleBuffer(middleBuffer).result()))
              case (events, None) if events.nonEmpty => recorder.writeFrame(events.fillMiddleBuffer(middleBuffer).result())
              case _ => recorder.writeEmptyFrame()
            }
            newEventRecorder = Nil
          }
          idle(recorder, gameInfo, essfMap, userMap, userHistoryMap, newEventRecorder, frame, tickCount + 1)

        case SaveInFile =>
          log.info(s"${ctx.self.path} work get msg save")
          timer.startSingleTimer(SaveDateKey, SaveInFile, saveTime)
          switchBehavior(ctx, "save", save(recorder, gameInfo, essfMap, userMap, userHistoryMap, lastFrame))

        case _ =>
          Behaviors.unhandled
      }
    }.receiveSignal{
      case (ctx, PostStop) =>
        timer.cancelAll()
        log.info(s"${ctx.self.path} stopping....")
        val mapInfo = essfMap.map{ essf=>
          val newJoinLeft = essf._2.map {
            case UserJoinLeft(joinFrame, -1l) => UserJoinLeft(joinFrame, lastFrame)
            case other => other
          }
          (essf._1, newJoinLeft)
        }.toList
        recorder.putMutableInfo(AppSettings.essfMapKeyName, Protocol.EssfMapInfo(mapInfo).fillMiddleBuffer(middleBuffer).result())
        eventRecorder.reverse.foreach {
          case (events, Some(state)) if events.nonEmpty =>
            recorder.writeFrame(events.fillMiddleBuffer(middleBuffer).result(), Some(state.fillMiddleBuffer(middleBuffer).result()))
          case (events, None) if events.nonEmpty => recorder.writeFrame(events.fillMiddleBuffer(middleBuffer).result())
          case _ => recorder.writeEmptyFrame()
        }
        recorder.finish()
        val filePath =  AppSettings.gameDataDirectoryPath + getFileName(gameInfo.roomId, gameInfo.startTime) + s"_${gameInfo.index}"
        RecordDAO.saveGameRecorder(gameInfo.roomId, gameInfo.startTime, System.currentTimeMillis(), filePath).onComplete{
          case Success(recordId) =>
            val usersInRoom = userHistoryMap.map(u => SlickTables.rUserInRecord(u._1, recordId, gameInfo.roomId)).toSet
            RecordDAO.saveUserInGame(usersInRoom).onComplete{
              case Success(_) =>

              case Failure(e) =>
                log.warn(s"save the detail of UserInGame in db fail...$e while PostStop")
            }

          case Failure(e) =>
            log.warn(s"save the detail of GameRecorder in db fail...$e while PostStop")
        }
        Behaviors.stopped
    }
  }

  def save(recorder: FrameOutputStream,
           gameInfo: GameInformation,
           essfMap: mutable.HashMap[UserBaseInfo,List[UserJoinLeft]] = mutable.HashMap[UserBaseInfo,List[UserJoinLeft]](),
           userMap: mutable.HashMap[String, String] = mutable.HashMap[String, String](),
           userHistoryMap: mutable.HashMap[String, String] = mutable.HashMap[String, String](),
           lastFrame: Long,
          )(implicit stashBuffer: StashBuffer[Command],
            timer: TimerScheduler[Command],
            middleBuffer: MiddleBufferInJvm): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SaveInFile =>
          val mapInfo = essfMap.map{ essf=>
            val newJoinLeft = essf._2.map {
              case UserJoinLeft(joinFrame, -1l) => UserJoinLeft(joinFrame, lastFrame)
              case other => other
            }
            (essf._1, newJoinLeft)
          }.toList
          recorder.putMutableInfo(AppSettings.essfMapKeyName, Protocol.EssfMapInfo(mapInfo).fillMiddleBuffer(middleBuffer).result())
          recorder.finish()

          val filePath =  AppSettings.gameDataDirectoryPath + getFileName(gameInfo.roomId, gameInfo.startTime) + s"_${gameInfo.index}"
          RecordDAO.saveGameRecorder(gameInfo.roomId, gameInfo.startTime, System.currentTimeMillis(), filePath).onComplete{
            case Success(recordId) =>
              val usersInRoom = userHistoryMap.map(u => SlickTables.rUserInRecord(u._1, recordId, gameInfo.roomId)).toSet
              RecordDAO.saveUserInGame(usersInRoom).onComplete{
                case Success(_) =>
                  ctx.self ! SwitchBehavior("resetRecord", resetRecord(gameInfo, userMap, userHistoryMap))

                case Failure(_) =>
                  log.warn("save the detail of UserInGame in db fail...")
                  ctx.self ! SwitchBehavior("resetRecord", resetRecord(gameInfo, userMap, userHistoryMap))
              }

            case Failure(e) =>
              log.warn("save the detail of GameRecorder in db fail...")
              ctx.self ! SwitchBehavior("resetRecord", resetRecord(gameInfo, userMap, userHistoryMap))
          }
          switchBehavior(ctx,"busy", busy())

        case _ =>
          Behaviors.unhandled
      }
    }
  }

  def resetRecord(gameInfo: GameInformation,
                  userMap: mutable.HashMap[String, String] = mutable.HashMap[String, String](),
                  userHistoryMap: mutable.HashMap[String, String] = mutable.HashMap[String, String]()
                 )(implicit stashBuffer: StashBuffer[Command],
                                             timer: TimerScheduler[Command],
                                             middleBuffer: MiddleBufferInJvm): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case RecordData(frame, event) => //新的文件初始化
          val newUserMap = userMap
          val newGameInfo = GameInformation(gameInfo.roomId, System.currentTimeMillis(), gameInfo.index + 1, frame)
          val recorder: FrameOutputStream = getRecorder(getFileName(gameInfo.roomId, newGameInfo.startTime), newGameInfo.index, gameInfo, Some(event._2))
          val newEventRecorder = List((event._1, Some(event._2)))
          val newEssfMap = mutable.HashMap.empty[UserBaseInfo, List[UserJoinLeft]]

          newUserMap.foreach { user =>
            newEssfMap.put(UserBaseInfo(user._1, user._2), List(UserJoinLeft(frame, -1L)))
          }
          switchBehavior(ctx, "idle", idle(recorder, newGameInfo, newEssfMap, newUserMap, newUserMap, newEventRecorder, frame))

        case _ =>
          Behaviors.unhandled

      }
    }
  }

  private def busy()(
    implicit stashBuffer:StashBuffer[Command],
    timer:TimerScheduler[Command]
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, behavior,durationOpt,timeOut) =>
          switchBehavior(ctx,name,behavior,durationOpt,timeOut)

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path} is time out when busy,msg=$m")
          Behaviors.stopped

        case unknownMsg =>
          stashBuffer.stash(unknownMsg)
          Behavior.same
      }
    }


  private[this] def switchBehavior(ctx: ActorContext[Command],
                                   behaviorName: String, behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error"))
                                  (implicit stashBuffer: StashBuffer[Command],
                                   timer: TimerScheduler[Command]) = {
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }

}
