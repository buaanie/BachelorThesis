import com.typesafe.sbt.SbtStartScript

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

seq(SbtStartScript.startScriptForClassesSettings: _*)

name := "TemplateExtractor"

scalacOptions ++= Seq("-deprecation", "-unchecked")

unmanagedResourceDirectories in Compile <+= baseDirectory{ _ / "conf"}

mainClass in (Compile, run) := Some("thu.ailab.cluster.TestNaiveAggloCluster")
