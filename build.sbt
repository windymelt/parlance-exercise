val scala3Version   = "3.9.0"
val tapirVersion    = "1.11.50"
val inertiaVersion  = "0.2.0"
val jsoniterVersion = "2.38.9"

lazy val root = project
  .in(file("."))
  .settings(
    name    := "parlance-exercise",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    run / fork := true,
    // JDK 25でNettyのJNI読み込みとScalaのLazyValsが出す警告を抑える
    run / javaOptions ++= Seq("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"),

    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir"           %% "tapir-core"            % tapirVersion,
      "com.softwaremill.sttp.tapir"           %% "tapir-netty-server"    % tapirVersion,
      "com.softwaremill.sttp.tapir"           %% "tapir-jsoniter-scala"  % tapirVersion,
      "com.softwaremill.sttp.tapir"           %% "tapir-files"           % tapirVersion,
      "dev.capslock"                          %% "inertia-core"          % inertiaVersion,
      "dev.capslock"                          %% "inertia-tapir"         % inertiaVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % jsoniterVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterVersion % "compile-internal",
      "org.slf4j"                             %  "slf4j-simple"          % "2.0.20"        % Runtime,
      "org.scalameta"                         %% "munit"                 % "1.3.6"         % Test,
    ),
  )
