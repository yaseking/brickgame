package com.neo.sk.carnie.core

import akka.actor.typed.Behavior
import com.neo.sk.carnie.paperClient.Protocol
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.utils.essf.RecordGame
import org.seekloud.byteobject.MiddleBufferInJvm
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

/**
  * Created by dry on 2018/10/19.
  */
object GameRecorder {

  sealed trait Command

  final case class RecordData(event:(List[Protocol.GameEvent],Option[Protocol.Snapshot])) extends Command
  final case object SaveDate extends Command
  final case object Save extends Command

  private final case object BehaviorChangeKey
  private final case object SaveDateKey
  private final val saveTime = 1.minute

  final case class SwitchBehavior(
                                   name: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error")
                                 ) extends Command

  case class TimeOut(msg:String) extends Command

  private val log = LoggerFactory.getLogger(this.getClass)

  def create():Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      log.info(s"${ctx.self.path} is starting..")
      implicit val stashBuffer: StashBuffer[GameRecorder.Command] = StashBuffer[Command](Int.MaxValue)
      implicit val middleBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(10 * 4096)
      Behaviors.withTimers[Command] { implicit timer =>
        timer.startSingleTimer(SaveDateKey, Save, saveTime)
        idle()
      }
    }
  }

  def idle()(implicit stashBuffer:StashBuffer[Command], timer:TimerScheduler[Command], middleBuffer: MiddleBufferInJvm):Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case RecordData(event) =>  //记录数据
          Behaviors.same

        case Save =>
          log.info(s"${ctx.self.path} work get msg save")
          timer.startSingleTimer(SaveDateKey, Save, saveTime)
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
  }


  private[this] def switchBehavior(ctx: ActorContext[Command],
                                   behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None,timeOut: TimeOut  = TimeOut("busy time error"))
                                  (implicit stashBuffer: StashBuffer[Command],
                                   timer:TimerScheduler[Command]) = {
    //log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey,timeOut,_))
    stashBuffer.unstashAll(ctx,behavior)
  }

}
