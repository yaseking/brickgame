package com.neo.sk.carnie.core

import akka.actor.typed.{ActorRef, Behavior, PostStop}
import com.neo.sk.carnie.paperClient.{Protocol, WsSourceProtocol}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.carnie.common.AppSettings
import com.neo.sk.carnie.models.SlickTables
import com.neo.sk.carnie.models.dao.RecordDAO
import com.neo.sk.carnie.paperClient.Protocol._
import com.neo.sk.utils.essf.RecordGame.getRecorder
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.byteobject.ByteObject._
import org.seekloud.essf.io.{FrameData, FrameInputStream, FrameOutputStream}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.util.{Failure, Success}
import com.neo.sk.carnie.Boot.executor
import com.neo.sk.carnie.core.RoomManager.{Command, FailMsgFront, Left}
import com.neo.sk.utils.essf.RecallGame._

/**
  * Created by dry on 2018/10/19.
  */
object GameReplay {
  sealed trait Command

  private val log = LoggerFactory.getLogger(this.getClass)

  private val waitTime=10.minutes

  case class TimeOut(msg: String) extends Command

  private final case object BehaviorChangeKey
  private final case object BehaviorWaitKey
  private final case object GameLoopKey
  case object GameLoop extends Command

  case class Left() extends Command

  final case class SwitchBehavior(
                                   name: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error")
                                 ) extends Command

  private[this] def switchBehavior(ctx: ActorContext[Command],
                                   behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None,timeOut: TimeOut  = TimeOut("busy time error"))
                                  (implicit stashBuffer: StashBuffer[Command],
                                   timer:TimerScheduler[Command]) = {
    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }

  case class InitReplay(subscriber: ActorRef[WsSourceProtocol.WsMsgSource], userId:String, f:Int) extends Command


  def create(recordId:Long): Behavior[Command] = {
    Behaviors.setup[Command]{ ctx=>
      log.info(s"${ctx.self.path} is starting..")
      implicit val stashBuffer = StashBuffer[Command](Int.MaxValue)
      implicit val sendBuffer = new MiddleBufferInJvm(81920)
      Behaviors.withTimers[Command] { implicit timer =>
        //todo test
        val replay=initInput("/Users/pro/SKProjects/carnie/backend/gameDataDirectoryPath/carnie_1000_1540518380133_0")
        val info=replay.init()
        try{
          println(s"test1")
          println(s"test2:${metaDataDecode(info.simulatorMetadata).right.get}")
          println(s"test3:${replay.getMutableInfo(AppSettings.essfMapKeyName)}")
          println(s"test4:${userMapDecode(replay.getMutableInfo(AppSettings.essfMapKeyName).getOrElse(Array[Byte]())).right.get.m}")
          ctx.self ! SwitchBehavior("work",
            work(
              replay,
              metaDataDecode(info.simulatorMetadata).right.get,
              // todo mutableInfo
              userMapDecode(replay.getMutableInfo(AppSettings.essfMapKeyName).getOrElse(Array[Byte]())).right.get.m
            ))
        }catch {
          case e:Throwable=>
            log.error("error init game replay---"+e.getMessage)
        }
//        RecordDAO.getRecordById(recordId).map {
//          case Some(r)=>
//            val replay=initInput(r.filePath)
//            val info=replay.init()
//            try{
//              ctx.self ! SwitchBehavior("work",
//                work(
//                  replay,
//                  metaDataDecode(info.simulatorMetadata).right.get,
//                  // todo mutableInfo
//                  userMapDecode(replay.getMutableInfo(KeyData.essfMapKeyName).getOrElse(Array[Byte]())).right.get.m
//                ))
//            }catch {
//              case e:Throwable=>
//                log.error("error---"+e.getMessage)
//            }
//          case None=>
//            log.debug(s"record--$recordId didn't exist!!")
//        }
        switchBehavior(ctx,"busy",busy())
      }
    }
  }

  def work(fileReader:FrameInputStream,
           metaData:GameInformation,
           //           initState:TankGameEvent.TankGameSnapshot,
           userMap:List[((Protocol.UserBaseInfo, Protocol.UserJoinLeft))],
           userOpt:Option[ActorRef[WsSourceProtocol.WsMsgSource]]=None
          )(
            implicit stashBuffer:StashBuffer[Command],
            timer:TimerScheduler[Command],
            sendBuffer: MiddleBufferInJvm
          ):Behavior[Command]={
    Behaviors.receive[Command]{(ctx,msg)=>
      msg match {
        case msg:InitReplay=>
          log.info("start new replay!")
          print(s"msg.userId:::::${msg.userId}")
          timer.cancel(GameLoopKey)
          timer.cancel(BehaviorWaitKey)
          userMap.find(_._1.id == msg.userId) match {
            case Some(u)=>
              //todo dispatch gameInformation
              dispatchTo(msg.subscriber, Protocol.Id(msg.userId))
              log.info(s" set replay from frame=${msg.f}")
              //fixme 跳转帧数goto失效
              //              fileReader.reset()
              //              for(i <- 1 to msg.f){
              //                if(fileReader.hasMoreFrame){
              //                  fileReader.readFrame()
              //                }
              //              }
              fileReader.gotoSnapshot(msg.f)
              log.info(s"replay from frame=${fileReader.getFramePosition}")
              if(fileReader.hasMoreFrame){
                timer.startPeriodicTimer(GameLoopKey, GameLoop, 150.millis)
                work(fileReader,metaData,userMap,Some(msg.subscriber))
              }else{
                timer.startSingleTimer(BehaviorWaitKey,TimeOut("wait time out"),waitTime)
                Behaviors.same
              }
            case None=>
              timer.startSingleTimer(BehaviorWaitKey,TimeOut("wait time out"),waitTime)
              Behaviors.same
          }


        case GameLoop=>
          if(fileReader.hasMoreFrame){
            userOpt.foreach(u=>
              fileReader.readFrame().foreach { f =>
                dispatchByteTo(u, f)
              }
            )
            Behaviors.same
          }else{
            timer.cancel(GameLoopKey)
            timer.startSingleTimer(BehaviorWaitKey,TimeOut("wait time out"),waitTime)
            Behaviors.same
          }

//        case msg:GetRecordFrameMsg=>
//          msg.replyTo ! GetRecordFrameRsp(RecordFrameInfo(fileReader.getFramePosition))
//          Behaviors.same
//
//        case msg:GetUserInRecordMsg=>
//          val data=userMap.map{r=>PlayerInfo(r._1.userId,r._1.name)}
//          msg.replyTo ! GetUserInRecordRsp(PlayerList(data))
//          Behaviors.same

        case msg:TimeOut=>
          Behaviors.stopped

        case unKnowMsg =>
          stashBuffer.stash(unKnowMsg)
          Behavior.same
      }
    }
  }

  import org.seekloud.byteobject.ByteObject._
  def dispatchTo(subscriber: ActorRef[WsSourceProtocol.WsMsgSource], msg: Protocol.GameMessage)(implicit sendBuffer: MiddleBufferInJvm)= {
    //    subscriber ! ReplayFrameData(msg.asInstanceOf[TankGameEvent.WsMsgServer].fillMiddleBuffer(sendBuffer).result())
    subscriber ! msg
  }

  def dispatchByteTo(subscriber: ActorRef[WsSourceProtocol.WsMsgSource], msg:FrameData)(implicit sendBuffer: MiddleBufferInJvm) = {
    //    subscriber ! ReplayFrameData(replayEventDecode(msg.eventsData).fillMiddleBuffer(sendBuffer).result())
    //    msg.stateData.foreach(s=>subscriber ! ReplayFrameData(replayStateDecode(s).fillMiddleBuffer(sendBuffer).result()))
    val events = replayEventDecode(msg.eventsData)
    val state = if(msg.stateData.isEmpty) None else Some(replayStateDecode(msg.stateData.get))
    subscriber ! ReplayFrameData(msg.frameIndex, events, state)
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
          log.debug(s"${ctx.self.path} is time out when busy,msg=${m}")
          Behaviors.stopped

        case unknowMsg =>
          stashBuffer.stash(unknowMsg)
          Behavior.same
      }
    }


}
