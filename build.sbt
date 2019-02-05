// Constants //

val projectName = "simple-cache-for-scala"

// Groups //

val openJdkJmhG = "org.openjdk.jmh"
val typelevelG  = "org.typelevel"

// Artifacts //

val jmhCoreA = "jmh-core"
val catsEffectA = "cats-effect"

// Versions //

val jmhV = "1.21"
val catsEffectV = "1.2.0"

// GAVs //

lazy val jmhCore = openJdkJmhG % jmhCoreA % jmhV
lazy val catsEffect = typelevelG %% catsEffectA % catsEffectV

// ThisBuild Scoped Settings //

ThisBuild / organization := "io.isomarcte"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / version      := "1.0.0-SNAPSHOT"

// Enable

// Root Project //

lazy val root = (project in file(".")).settings(
  name := projectName
).aggregate(core, cats)

// Projects //

lazy val core = project.settings(
  name := s"$projectName-core"
)

lazy val cats = project.settings(
  name := s"$projectName-cats",
  libraryDependencies ++= Seq(
    catsEffect
  )
).dependsOn(core)

lazy val jmh = project.settings(
  name := s"$projectName-jmh",
  libraryDependencies ++= Seq(
    jmhCore
  )
).dependsOn(
  cats
)
