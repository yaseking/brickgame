import sbt.Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._



name := "carnie"

val scalaV = "2.12.6"
//val scalaV = "2.11.8"

val projectName = "carnie"
val projectVersion = "2018.11.15"

val projectMainClass = "com.neo.sk.carnie.Boot"

def commonSettings = Seq(
  version := projectVersion,
  scalaVersion := scalaV,
  scalacOptions ++= Seq(
    //"-deprecation",
    "-feature"
  )
)

// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.{crossProject, CrossType}


lazy val shared = (crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure) in file("shared"))
  .settings(name := "shared")
  .settings(commonSettings: _*)

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

// Scala-Js frontend
lazy val frontend = (project in file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(name := "frontend")
  .settings(commonSettings: _*)
  .settings(
    inConfig(Compile)(
      Seq(
        fullOptJS,
        fastOptJS,
        packageJSDependencies,
        packageMinifiedJSDependencies
      ).map(f => (crossTarget in f) ~= (_ / "sjsout"))
    ))
  .settings(skip in packageJSDependencies := false)
  .settings(
    scalaJSUseMainModuleInitializer := false,
    //mainClass := Some("com.neo.sk.virgour.front.Main"),
    libraryDependencies ++=     Seq(
      //      "io.circe" %%% "circe-core" % "0.8.0",
      //      "io.circe" %%% "circe-generic" % "0.8.0",
      //      "io.circe" %%% "circe-parser" % "0.8.0",
      "io.circe" %%% "circe-core" % Dependencies.circeVersion,
      "io.circe" %%% "circe-generic" % Dependencies.circeVersion,
      "io.circe" %%% "circe-parser" % Dependencies.circeVersion,
      "org.scala-js" %%% "scalajs-dom" % Dependencies.scalaJsDomV,
      "in.nvilla" %%% "monadic-html" % Dependencies.monadicHtmlV,
      //"in.nvilla" %%% "monadic-rx-cats" % "0.4.0-RC1",
      "com.lihaoyi" %%% "scalatags" % Dependencies.scalaTagsV,
      "com.github.japgolly.scalacss" %%% "core" % Dependencies.scalaCssV,
      "org.seekloud" %%% "byteobject" % "0.1.1"
      //"com.lihaoyi" %%% "upickle" % upickleV,
      //"io.suzaku" %%% "diode" % "1.1.2",
      //"org.scala-js" %%% "scalajs-java-time" % scalaJsJavaTime
      //"com.lihaoyi" %%% "utest" % "0.3.0" % "test"
    )
  )
  .dependsOn(sharedJs)

//client
lazy val client = (project in file("client")).enablePlugins(PackPlugin)
  .settings(commonSettings: _*)
  .settings(
    mainClass in reStart := Some("com.neo.sk.carnie.Boot"),
    javaOptions in reStart += "-Xmx2g"
  )
  .settings(name := "carnie")
  .settings(
    packMain := Map("carnie" -> "com.neo.sk.carnie.Boot"),
    packJvmOpts := Map("carnie" -> Seq("-Xmx256m", "-Xms64m")),
    packExtraClasspath := Map("carnie" -> Seq("."))
  )
  .settings(
    libraryDependencies ++= Dependencies.backendDependencies
  )
  .dependsOn(sharedJvm)

// Akka Http based backend
lazy val backend = (project in file("backend")).enablePlugins(PackPlugin)
  .settings(commonSettings: _*)
  .settings(
    mainClass in reStart := Some(projectMainClass),
    javaOptions in reStart += "-Xmx2g"
  )
  .settings(name := "carnie")
  .settings(
    //pack
    // If you need to specify main classes manually, use packSettings and packMain
    //packSettings,
    // [Optional] Creating `hello` command that calls org.mydomain.Hello#main(Array[String])
    packMain := Map("carnie" -> projectMainClass),
    packJvmOpts := Map("carnie" -> Seq("-Xmx1024m", "-Xms128m")),
    packExtraClasspath := Map("carnie" -> Seq("."))
  )
  .settings(
    libraryDependencies ++= Dependencies.backendDependencies
  )
  .settings {
    (resourceGenerators in Compile) += Def.task {
      val fastJsOut = (fastOptJS in Compile in frontend).value.data
      val fastJsSourceMap = fastJsOut.getParentFile / (fastJsOut.getName + ".map")
      Seq(
        fastJsOut,
        fastJsSourceMap
      )
    }.taskValue
  }
  //  .settings(
  //    (resourceGenerators in Compile) += Def.task {
  //      val fullJsOut = (fullOptJS in Compile in frontend).value.data
  //      val fullJsSourceMap = fullJsOut.getParentFile / (fullJsOut.getName + ".map")
  //      Seq(
  //        fullJsOut,
  //        fullJsSourceMap
  //      )
  //    }.taskValue)
  .settings((resourceGenerators in Compile) += Def.task {
  Seq(
    (packageJSDependencies in Compile in frontend).value
    //(packageMinifiedJSDependencies in Compile in frontend).value
  )
}.taskValue)
  .settings(
    (resourceDirectories in Compile) += (crossTarget in frontend).value,
    watchSources ++= (watchSources in frontend).value
  )
  .dependsOn(sharedJvm)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(frontend, backend, client)
  .settings(name := "root")


