import com.jsuereth.sbtpgp.PgpKeys
import sbtrelease.ReleaseStateTransformations.*

// Reload SBT automatically after changes to this file
Global / onChangedBuildSource := ReloadOnSourceChanges

val eVersion                       = "3.0.1"
val scalafixOrganizeImportsVersion = "0.6.0"
val munitVersion                   = "1.0.0"

val e                       = "dev.akif"             %% "e-scala"          % eVersion
val scalafixOrganizeImports = "com.github.liancheng" %% "organize-imports" % scalafixOrganizeImportsVersion
val munit                   = "org.scalameta"        %% "munit"            % munitVersion % Test

val commonSettings: Seq[Setting[?]] =
  Seq(
    description := "A basic, home-made functional effect system with powerful errors",
    developers  := List(Developer("makiftutuncu", "Mehmet Akif Tütüncü", "m.akif.tutuncu@gmail.com", url("https://akif.dev"))),
    homepage    := Some(url("https://github.com/makiftutuncu/effect")),
    javacOptions ++= Seq("source", "21", "target", "21"),
    licenses             := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
    organization         := "dev.akif",
    organizationHomepage := Some(url("https://akif.dev")),
    organizationName     := "Mehmet Akif Tütüncü",
    scalafixDependencies += scalafixOrganizeImports,
    scalafixOnCompile := true,
    scalaVersion      := "3.4.3",
    scmInfo           := Some(ScmInfo(url("https://github.com/makiftutuncu/effect"), "git@github.com:makiftutuncu/effect.git")),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    startYear         := Some(2022),
    versionScheme     := Some("early-semver")
  )

def artifactSettings(shouldPublish: Boolean): Seq[Setting[?]] =
  Seq(
    Compile / packageBin / publishArtifact := shouldPublish,
    Compile / packageSrc / publishArtifact := shouldPublish,
    Compile / packageDoc / publishArtifact := shouldPublish,
    Test / packageBin / publishArtifact := false,
    Test / packageSrc / publishArtifact := false,
    Test / packageDoc / publishArtifact := false,
    publish / skip                         := !shouldPublish,
    releasePublishArtifactsAction          := PgpKeys.publishSigned.value
  )

val releaseSettings: Seq[Setting[?]] = {
  val sonatypeUser: String = sys.env.getOrElse("SONATYPE_USER", "")

  val sonatypePass: String = sys.env.getOrElse("SONATYPE_PASS", "")

  val checkPublishCredentials: ReleaseStep =
    ReleaseStep { state =>
      if (sonatypeUser.isEmpty || sonatypePass.isEmpty) {
        throw new Exception("Sonatype credentials are missing! Make sure to provide SONATYPE_USER and SONATYPE_PASS environment variables.")
      }

      state
    }

  val publishSettings: Seq[Setting[?]] =
    Seq(
      credentials ++= Seq(
        Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", sonatypeUser, sonatypePass),
        Credentials("GnuPG Key ID", "gpg", "3D5A9AE9F71508A0D85E78DF877A4F41752BB3B5", "ignored")
      ),
      pomIncludeRepository := { _ => false },
      publishMavenStyle    := true,
      publishTo            := Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
    )

  val releaseProcessSettings: Seq[Setting[?]] = {
    Seq(
      releaseProcess := Seq[ReleaseStep](
        checkSnapshotDependencies,
        checkPublishCredentials,
        inquireVersions,
        runClean,
        runTest,
        setReleaseVersion,
        commitReleaseVersion,
        tagRelease,
        publishArtifacts,
        setNextVersion,
        commitNextVersion,
        pushChanges
      )
    )
  }


  publishSettings ++ releaseProcessSettings
}

usePgpKeyHex("3D5A9AE9F71508A0D85E78DF877A4F41752BB3B5")

inThisBuild(commonSettings ++ releaseSettings)

lazy val core = (project in file("core"))
  .settings(name := "effect-core")
  .settings(libraryDependencies ++= Seq(e, munit))
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
