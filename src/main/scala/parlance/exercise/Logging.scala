package parlance.exercise

import scribe.Level
import scribe.writer.SystemOutWriter

/** scribeの設定。出力先とParlanceが`System.Logger`に書くSQLログのレベルをここで決める。 */
object Logging:
  /** ParlanceがSQLログを書くロガーの名前。 */
  private val SqlLoggerName = "ma.chinespirit.parlance"

  /** SQLログのレベルを決める環境変数。trace・debug・infoなどscribeのレベル名を受け付ける。 */
  private val SqlLogLevelEnv = "SQL_LOG_LEVEL"

  /** SQLログのレベルを設定する。Parlanceが最初にSQLを実行する前に呼ぶ。
    *
    * scribe-jplはSystem.Loggerを作った時点のscribeロガーを保持するので、後から設定しても反映されない。
    * debugは実行したSQLと所要時間、traceはバインド値も出す。infoでSQLログは止まる。
    */
  def configure(defaultSqlLogLevel: Level = Level.Debug): Unit =
    // scribe 3.19.0の既定のwriterはレベルの順序が逆に定義されているため、debugとtraceを標準エラーに書く。
    // sbt runでは[error]と表示されて紛らわしいので、すべて標準出力に書く。
    scribe.Logger.root.clearHandlers().withHandler(writer = SystemOutWriter).replace()

    val level = sys.env.get(SqlLogLevelEnv) match
      case Some(name) =>
        Level.get(name).getOrElse:
          scribe.warn(s"$SqlLogLevelEnv=$name はscribeのレベル名ではないので ${defaultSqlLogLevel.name} を使う")
          defaultSqlLogLevel
      case None => defaultSqlLogLevel
    scribe.Logger(SqlLoggerName).withMinimumLevel(level).replace()
