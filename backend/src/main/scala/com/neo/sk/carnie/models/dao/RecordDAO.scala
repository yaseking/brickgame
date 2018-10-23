package com.neo.sk.carnie.models.dao

import scala.concurrent.Future
import com.neo.sk.carnie.models.SlickTables._
import com.neo.sk.utils.DBUtil.db
import slick.jdbc.PostgresProfile.api._
import com.neo.sk.carnie.Boot.executor

/**
  * Created by dry on 2018/10/23.
  **/
object RecordDAO {

  def saveGameRecorder(roomId: Int, startTime: Long, endTime: Long, filePath: String): Future[Long] = {
    db.run{
      tGameRecord.returning(tGameRecord.map(_.recordId)) += rGameRecord(-1l, roomId, startTime, endTime, filePath)
    }
  }

  def saveUserInGame(users: Set[rUserInRecord]) = {
    db.run(tUserInRecord ++= users)
  }

  def getRecordById(id:Long)={
    db.run(tGameRecord.filter(_.recordId===id).result.headOption)
  }

}
