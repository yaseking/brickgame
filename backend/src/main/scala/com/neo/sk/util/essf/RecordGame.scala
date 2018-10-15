package com.neo.sk.util.essf

import java.io.File

import com.neo.sk.carnie.common.AppSettings
import com.neo.sk.util.MiddleBufferInJvm
import org.seekloud.essf.io.FrameOutputStream
import com.neo.sk.carnie.paperClient.Protocol._
import com.neo.sk.util.byteObject.ByteObject._



/**
  * Created by haoshuhan on 2018/10/11.
  */
object RecordGame {
  val utf8 = "utf-8"

  def getRecorder(fileName:String, index:Int, gameInformation: GameInformation, initStateOpt:Option[State] = None): FrameOutputStream = {
    val middleBuffer = new MiddleBufferInJvm(10 * 4096)
    val name = "carnie"
    val version = "0.1"
    val dir = new File(AppSettings.gameDataDirectoryPath)
    if(!dir.exists()){
      dir.mkdir()
    }
    val file = AppSettings.gameDataDirectoryPath + fileName + s"_$index"
    val gameInformationBytes = gameInformation.fillMiddleBuffer(middleBuffer).result()
    val initStateBytes = initStateOpt.map{
      case t: State =>
        t.fillMiddleBuffer(middleBuffer).result()
    }.getOrElse(Array[Byte]())
    val recorder = new FrameOutputStream(file)
    recorder.init(name,version,gameInformationBytes,initStateBytes)
    recorder
  }

}
