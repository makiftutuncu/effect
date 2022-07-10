import Dependencies._
import com.jsuereth.sbtpgp.PgpKeys
import sbt._
import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import scalafix.sbt.ScalafixPlugin.autoImport._

object Settings {
  lazy val commonSettings: Seq[Setting[_]] =
    Seq(
      description          := "A basic, home-made functional effect system with powerful errors",
      developers           := List(Developer("makiftutuncu", "Mehmet Akif Tütüncü", "m.akif.tutuncu@gmail.com", url("https://akif.dev"))),
      homepage             := Some(url("https://github.com/makiftutuncu/effect")),
      licenses             := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
      organization         := "dev.akif",
      organizationHomepage := Some(url("https://akif.dev")),
      organizationName     := "Mehmet Akif Tütüncü",
      scalafixDependencies += scalafixOrganizeImports,
      scalafixOnCompile := true,
      scalaVersion      := "3.1.2",
      scmInfo           := Some(ScmInfo(url("https://github.com/makiftutuncu/effect"), "git@github.com:makiftutuncu/effect.git")),
      semanticdbEnabled := true,
      semanticdbVersion := scalafixSemanticdb.revision,
      startYear         := Some(2022),
      versionScheme     := Some("early-semver")
    )

  def artifactSettings(shouldPublish: Boolean): Seq[Setting[_]] =
    Seq(
      Compile / packageBin / publishArtifact := shouldPublish,
      Compile / packageSrc / publishArtifact := shouldPublish,
      Compile / packageDoc / publishArtifact := shouldPublish,
      publish / skip                         := !shouldPublish,
      releasePublishArtifactsAction          := PgpKeys.publishSigned.value
    )

  lazy val releaseSettings: Seq[Setting[_]] = {
    val sonatypeUser: String = sys.env.getOrElse("SONATYPE_USER", "")

    val sonatypePass: String = sys.env.getOrElse("SONATYPE_PASS", "")

    val checkPublishCredentials: ReleaseStep =
      ReleaseStep { state =>
        if (sonatypeUser.isEmpty || sonatypePass.isEmpty) {
          throw new Exception(
            "Sonatype credentials are missing! Make sure to provide SONATYPE_USER and SONATYPE_PASS environment variables."
          )
        }

        state
      }

    val publishSettings: Seq[Setting[_]] =
      Seq(
        credentials += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", sonatypeUser, sonatypePass),
        pomIncludeRepository := { _ => false },
        publishMavenStyle    := true,
        publishTo            := Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
      )

    val releaseProcessSettings: Seq[Setting[_]] = {
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
}
