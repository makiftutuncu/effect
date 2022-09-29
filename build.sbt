import Dependencies._
import Settings._

// Reload SBT automatically after changes to this file
Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(commonSettings ++ releaseSettings)

lazy val core = (project in file("core"))
  .settings(name := "effect-core")
  .settings(libraryDependencies ++= Seq(munit))
  .settings(artifactSettings(shouldPublish = true))
  .settings(
    // Since most tests depend on elapsed time, running them in parallel causes them to be flaky
    Test / parallelExecution := false,
    // Since tests won't run in parallel, disable buffered log output for immediate feedback while a test is running
    Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "-b")
  )

lazy val examples = (project in file("examples"))
  .dependsOn(core)
  .settings(fork := true)
  .settings(artifactSettings(shouldPublish = false))

lazy val root = (project in file("."))
  .aggregate(core, examples)
  .settings(name := "effect")
  .settings(artifactSettings(shouldPublish = false))
