import Dependencies._
import Settings._

// Reload SBT automatically after changes to this file
Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(commonSettings ++ releaseSettings)

lazy val core = (project in file("core"))
  .settings(name := "effect-core")
  .settings(libraryDependencies ++= Seq(munit))
  .settings(artifactSettings(shouldPublish = true))

lazy val examples = (project in file("examples"))
  .dependsOn(core)
  .settings(fork := true)
  .settings(artifactSettings(shouldPublish = false))

lazy val root = (project in file("."))
  .aggregate(core, examples)
  .settings(name := "effect")
  .settings(artifactSettings(shouldPublish = false))
