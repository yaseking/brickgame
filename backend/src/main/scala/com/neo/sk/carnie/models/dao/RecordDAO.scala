package com.neo.sk.carnie.models.dao

import com.neo.sk.utils.DBUtil.db
import slick.jdbc.PostgresProfile.api._
import com.neo.sk.carnie.models.SlickTables._
import com.neo.sk.carnie.Boot.executor
import com.neo.sk.carnie.models.SlickTables
import net.sf.ehcache.search.aggregator.Count

import scala.concurrent.Future

/**
  * User: Jason
  * Date: 2018/10/22
  * Time: 14:49
  */
object RecordDAO {
  def getRecordList(lastRecord: Long,count: Int)= {
    if(lastRecord == 0L){
      val action = {
        tGameRecord.sortBy(_.recordId.desc).take(count) join tUserInRecord on { (game, user) =>
          game.recordId === user.recordId
        }
      }.result
      db.run(action)
    }
    else{
      val action = {
        tGameRecord.filter(_.recordId < lastRecord).sortBy(_.recordId.desc).take(count) join tUserInRecord on { (game, user) =>
          game.recordId === user.recordId
        }
      }.result
      db.run(action)
    }
  }

  def getRecordListByTime(startTime: Long,endTime: Long,lastRecord: Long,count: Int) = {
    if(lastRecord == 0L){
      val action = {
        tGameRecord.sortBy(_.recordId.desc).take(count) join tUserInRecord on { (game, user) =>
          (game.recordId === user.recordId) && game.startTime >= startTime && game.endTime <= endTime
        }
      }.result
      db.run(action)
    }
    else{
      val action = {
        tGameRecord.filter(_.recordId < lastRecord).sortBy(_.recordId.desc).take(count) join tUserInRecord on { (game, user) =>
          game.recordId === user.recordId && game.startTime >= startTime && game.endTime <= endTime
        }
      }.result
      db.run(action)
    }

  }

  def getRecordListByPlayer(playerId: String,lastRecord: Long,count: Int) = {
    if(lastRecord == 0L){
      val action = {
        tGameRecord.sortBy(_.recordId.desc).take(count) join tUserInRecord on { (game, user) =>
          game.recordId === user.recordId
        }
      }.result
      db.run(action)
    }
    else{
      val action = {
        tGameRecord.filter(_.recordId < lastRecord).sortBy(_.recordId.desc).take(count) join tUserInRecord on { (game, user) =>
          game.recordId === user.recordId
        }
      }.result
      db.run(action)
    }
  }

  def getRecordPath(recordId: Long) = db.run(
    tGameRecord.filter(_.recordId === recordId).map(_.filePath).result.headOption
  )

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
