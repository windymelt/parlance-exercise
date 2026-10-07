val scala3Version   = "3.9.0"
val caskVersion     = "0.11.3"
val inertiaVersion  = "0.2.0"
val jsoniterVersion = "2.38.9"

lazy val root = project
  .in(file("."))
  .settings(
    name    := "parlance-exercise",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    run / fork := true,
    run / connectInput := true,

    libraryDependencies ++= Seq(
      "com.lihaoyi"                           %% "cask"                  % caskVersion,
      "dev.capslock"                          %% "inertia-core"          % inertiaVersion,
      "dev.capslock"                          %% "inertia-cask"          % inertiaVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % jsoniterVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterVersion % "compile-internal",
      "org.scalameta"                         %% "munit"                 % "1.3.6"         % Test,
    ),
  )
