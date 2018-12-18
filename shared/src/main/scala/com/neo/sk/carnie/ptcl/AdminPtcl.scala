package com.neo.sk.carnie.ptcl

/**
  * User: Jason
  * Date: 2018/12/17
  * Time: 17:22
  */
object AdminPtcl {

  trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  final case class ErrorRsp(
    errCode: Int,
    msg: String
  ) extends CommonRsp

  final case class SuccessRsp(
    errCode: Int = 0,
    msg: String = "ok"
  ) extends CommonRsp

  case class LoginReq(
    id: String,
    passWord: String
  )
}
