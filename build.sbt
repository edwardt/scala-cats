name := "scala-cats"

organization := "com.earldouglas"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.2"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

// allow F[_], e.g. `trait Functor[A, F[_]]`
scalacOptions += "-language:higherKinds"

// allow implicits, e.g. `implicit def listSemigroup`
scalacOptions += "-language:implicitConversions"
