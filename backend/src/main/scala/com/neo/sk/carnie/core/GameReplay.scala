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
import org.seekloud.essf.io.{FrameInputStream, FrameOutputStream}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.util.{Failure, Success}
import com.neo.sk.carnie.Boot.executor
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
        RecordDAO.getRecordById(recordId).map {
          case Some(r)=>
            val replay=initInput(r.filePath)
            val info=replay.init()
            try{
              ctx.self ! SwitchBehavior("work",
                work(
                  replay,
                  metaDataDecode(info.simulatorMetadata).right.get,
                  // todo mutableInfo
                  userMapDecode(replay.getMutableInfo(AppSettings.essfMapKeyName).getOrElse(Array[Byte]())).right.get.m
                ))
            }catch {
              case e:Throwable=>
                log.error("error---"+e.getMessage)
            }
          case None=>
            log.debug(s"record--$recordId didn't exist!!")
        }
        switchBehavior(ctx,"busy",busy())
      }
    }
  }

  def work(fileReader:FrameInputStream,
           metaData:GameInformation,
           //           initState:TankGameEvent.TankGameSnapshot,
           userMap:List[(EssfMapKey,EssfMapJoinLeftInfo)],
           userOpt:Option[ActorRef[TankGameEvent.WsMsgSource]]=None
          )(
            implicit stashBuffer:StashBuffer[Command],
            timer:TimerScheduler[Command],
            sendBuffer: MiddleBufferInJvm
          ):Behavior[Command]={
    Behaviors.receive[Command]{(ctx,msg)=>
      msg match {
        case msg:InitReplay=>
          log.info("start new replay!")
          timer.cancel(GameLoopKey)
          timer.cancel(BehaviorWaitKey)
          userMap.find(_._1.userId == msg.userId) match {
            case Some(u)=>
              dispatchTo(msg.userActor,YourInfo(u._1.userId,u._1.tankId,u._1.name,metaData.tankConfig))
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
                timer.startPeriodicTimer(GameLoopKey, GameLoop, 100.millis)
                work(fileReader,metaData,userMap,Some(msg.userActor))
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
  def dispatchTo(subscriber: ActorRef[WsSourceProtocol.WsMsgSource], msg: Protocol.ReplayMessage)(implicit sendBuffer: MiddleBufferInJvm)= {
    //    subscriber ! ReplayFrameData(msg.asInstanceOf[TankGameEvent.WsMsgServer].fillMiddleBuffer(sendBuffer).result())
    subscriber ! ReplayFrameData(List(msg).fillMiddleBuffer(sendBuffer).result())
  }

  def dispatchByteTo(subscriber: ActorRef[WsSourceProtocol.WsMsgSource], msg:FrameData)(implicit sendBuffer: MiddleBufferInJvm) = {
    //    subscriber ! ReplayFrameData(replayEventDecode(msg.eventsData).fillMiddleBuffer(sendBuffer).result())
    //    msg.stateData.foreach(s=>subscriber ! ReplayFrameData(replayStateDecode(s).fillMiddleBuffer(sendBuffer).result()))
    subscriber ! ReplayFrameData(msg.eventsData)
    msg.stateData.foreach(s=>subscriber ! ReplayFrameData(s))
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

}
