import sbt._

object Dependencies {
  lazy val scalafixOrganizeImportsVersion = "0.6.0"
  lazy val munitVersion                   = "0.7.29"

  lazy val scalafixOrganizeImports = "com.github.liancheng" %% "organize-imports" % scalafixOrganizeImportsVersion
  lazy val munit                   = "org.scalameta"        %% "munit"            % munitVersion % Test
}
