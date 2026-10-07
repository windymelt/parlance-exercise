val scala3Version          = "3.9.0"
val tapirVersion           = "1.11.50"
val inertiaVersion         = "0.2.0"
val jsoniterVersion        = "2.38.9"
val parlanceVersion        = "0.1.0"
val testcontainersVersion  = "0.44.1"
val scribeVersion          = "3.19.0"
val airframeUlidVersion    = "2026.2.2"

lazy val root = project
  .in(file("."))
  .settings(
    name    := "parlance-exercise",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    // Parlanceのクエリ DSL（where(_.col === v)など）が暗黙変換を使うので、プロジェクト全体で有効にする
    scalacOptions ++= Seq("-feature", "-language:implicitConversions"),

    run / fork  := true,
    Test / fork := true,
    // mainが複数あるので`sbt run`はWebサーバを起動する。マイグレーションはrunMainで呼ぶ。
    Compile / run / mainClass := Some("parlance.exercise.NotesServer"),
    // JDK 25でNettyのJNI読み込みとScalaのLazyValsが出す警告を抑える
    run / javaOptions ++= Seq("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"),

    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir"           %% "tapir-core"                      % tapirVersion,
      "com.softwaremill.sttp.tapir"           %% "tapir-netty-server"              % tapirVersion,
      "com.softwaremill.sttp.tapir"           %% "tapir-jsoniter-scala"            % tapirVersion,
      "com.softwaremill.sttp.tapir"           %% "tapir-files"                     % tapirVersion,
      "dev.capslock"                          %% "inertia-core"                    % inertiaVersion,
      "dev.capslock"                          %% "inertia-tapir"                   % inertiaVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"             % jsoniterVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros"           % jsoniterVersion % "compile-internal",
      "ma.chinespirit"                        %% "parlance"                        % parlanceVersion,
      "ma.chinespirit"                        %% "parlance-migrate"                % parlanceVersion,
      "org.wvlet.airframe"                    %% "airframe-ulid"                   % airframeUlidVersion,
      "org.postgresql"                        %  "postgresql"                      % "42.7.13",
      "com.zaxxer"                            %  "HikariCP"                        % "7.1.0",
      // ログはscribeに集める。HikariCPとNettyはSLF4J経由、ParlanceのSQLログはSystem.Logger経由で届く
      "com.outr"                              %% "scribe"                          % scribeVersion,
      "com.outr"                              %% "scribe-slf4j2"                   % scribeVersion   % Runtime,
      "com.outr"                              %% "scribe-jpl"                      % scribeVersion   % Runtime,
      "org.scalameta"                         %% "munit"                           % "1.3.6"         % Test,
      "com.dimafeng"                          %% "testcontainers-scala-munit"      % testcontainersVersion % Test,
      "com.dimafeng"                          %% "testcontainers-scala-postgresql" % testcontainersVersion % Test,
    ),
  )
