import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "securesocial"
    val appVersion      = "2.1-RC1"

    val appDependencies = Seq(
      "com.typesafe" %% "play-plugins-util" % "2.1-RC1",
      "com.typesafe" %% "play-plugins-mailer" % "2.1-RC1",
      "org.mindrot" % "jbcrypt" % "0.3m"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
    ).settings(
      resolvers ++= Seq(
        "jBCrypt Repository" at "http://repo1.maven.org/maven2/org/",
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/snapshots/"
      )
    )

}
