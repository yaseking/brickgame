package com.neo.sk.carnie.paperClient

/**
  * Created by hongruying on 2018/7/11
  */
object WsSourceProtocol {
  trait WsMsgSource

  case object CompleteMsgServer extends WsMsgSource
  case class FailMsgServer(ex: Exception) extends WsMsgSource

}
