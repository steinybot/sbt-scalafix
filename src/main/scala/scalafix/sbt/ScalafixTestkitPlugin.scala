package scalafix.sbt

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

import java.io.File.pathSeparator

object ScalafixTestkitPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = JvmPlugin

  object autoImport {
    val scalafixTestkitInputClasspath =
      taskKey[Classpath]("Classpath of input project")
    val scalafixTestkitInputScalacOptions =
      taskKey[Seq[String]](
        "Scalac compiler flags that were used to compile the input project"
      )
    val scalafixTestkitInputScalaVersion =
      settingKey[String](
        "Scala compiler version that was used to compile the input project"
      )
    val scalafixTestkitInputSourceDirectories =
      taskKey[Seq[File]]("Source directories of input project")
    val scalafixTestkitOutputSourceDirectories =
      taskKey[Seq[File]]("Source directories of output project")
  }
  import autoImport._

  // Since we currently build against sbt 1.2.1 and https://github.com/sbt/sbt/blob/6664cbe/main/src/main/scala/sbt/Keys.scala#L414
  // was introduced in sbt 1.3.1, we redefine that setting key to assign it, with the gotcha that it only has an effect if the
  // client uses sbt 1.3.1 or later.
  private val includePluginResolvers =
    settingKey[Boolean]("Include the resolvers from the metabuild.")

  override def buildSettings: Seq[Def.Setting[_]] =
    List(
      // This makes it simpler to use sbt-scalafix SNAPSHOTS: such snapshots may bring scalafix-* SNAPSHOTS which is fine in the
      // meta build as the same resolver (declared in project/plugins.sbt) is used. However, since it is advised in the docs and the g8
      // template to build testkit-enabled projects against scalafix-testkit:_root_.scalafix.sbt.BuildInfo.scalafixVersion, the same
      // resolver is needed here as well.
      includePluginResolvers := true
    )

  override def projectSettings: Seq[Def.Setting[_]] =
    List(
      scalafixTestkitInputScalacOptions := scalacOptions.value,
      scalafixTestkitInputScalaVersion := scalaVersion.value,
      Test / resourceGenerators += Def.task {
        val props = new java.util.Properties()
        val values = Map[String, Seq[File]](
          "sourceroot" ->
            List((ThisBuild / baseDirectory).value),
          "inputClasspath" ->
            scalafixTestkitInputClasspath.value.map(_.data),
          "inputSourceDirectories" ->
            scalafixTestkitInputSourceDirectories.value.distinct, // https://github.com/sbt/sbt/pull/6511
          "outputSourceDirectories" ->
            scalafixTestkitOutputSourceDirectories.value
        )
        values.foreach { case (key, files) =>
          props.put(
            key,
            files.iterator.filter(_.exists()).mkString(pathSeparator)
          )
        }
        props.put("scalaVersion", scalafixTestkitInputScalaVersion.value)
        props.put(
          "scalacOptions",
          scalafixTestkitInputScalacOptions.value.mkString("|")
        )
        val out =
          (Test / managedResourceDirectories).value.head /
            "scalafix-testkit.properties"
        IO.write(props, "Input data for scalafix testkit", out)
        List(out)
      }
    )
}
