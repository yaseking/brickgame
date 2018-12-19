package com.neo.sk.carnie.models.dao

import com.neo.sk.utils.DBUtil.db
import slick.jdbc.PostgresProfile.api._
import com.neo.sk.carnie.models.SlickTables._
import com.neo.sk.carnie.Boot.executor

object PlayerRecordDAO {

  def addPlayerRecord(playerId:String, nickName:String, killing:Int, killed:Int, score:Float, startTime:Long, endTime:Long) =
    db.run{
      tPlayerRecord += rPlayerRecord(-1l, playerId, nickName, killing, killed, score, startTime, endTime)
    }
}
