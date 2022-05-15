lazy val scalafixOrganizeImportsVersion = "0.6.0"

lazy val scalafixOrganizeImports = "com.github.liancheng" %% "organize-imports" % scalafixOrganizeImportsVersion

// Reload SBT automatically after changes to this file
Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  Seq(
    organization     := "dev.akif",
    organizationName := "Mehmet Akif Tütüncü",
    scalafixDependencies += scalafixOrganizeImports,
    scalafixOnCompile := true,
    scalaVersion      := "3.1.2",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    version           := "0.1.0"
  )
)

lazy val core = project in file("core")

lazy val example = (project in file("example")).dependsOn(core)

lazy val root = (project in file("."))
  .settings(name := "effect")
  .aggregate(core, example)
