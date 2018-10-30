package com.neo.sk.carnie.controller

import com.neo.sk.carnie.protocol.Protocol4Agent.{LoginRsp, UrlData}
import com.neo.sk.carnie.utils.HttpUtil
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

object Api4GameAgent extends HttpUtil{

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  def getLoginRspFromEs() = {
    val methodName = "GET"
    val url = "http://flowdev.neoap.com/esheep/api/gameAgent/login"
    getRequestSend(methodName,url,Nil,"UTF-8").map{
      case Right(r) =>
        decode[LoginRsp](r) match {
          case Right(rsp) =>
            println(s"rsp: $rsp")
            Right(UrlData(rsp.data.wsUrl,rsp.data.scanUrl.replaceFirst("data:image/png;base64,","")))
          case Left(e) =>
            Left(s"error:$e")
        }
      case Left(e) =>
        log.info(s"$e")
        Left("error")
    }
  }

  def main(args: Array[String]): Unit = {
    getLoginRspFromEs()
  }
}
