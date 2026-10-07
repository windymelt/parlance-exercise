package parlance.exercise.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import ma.chinespirit.parlance.*
import ma.chinespirit.parlance.migrate.*

/** 接続設定。compose.yamlと同じ環境変数から組み立てる。 */
final case class DbConfig(jdbcUrl: String, user: String, password: String, maxPoolSize: Int = 8)

object DbConfig:
  def fromEnv(env: Map[String, String] = sys.env): DbConfig =
    val host = env.getOrElse("POSTGRES_HOST", "localhost")
    val port = env.getOrElse("POSTGRES_PORT", "5432")
    val db   = env.getOrElse("POSTGRES_DB", "parlance_exercise")
    DbConfig(
      jdbcUrl = s"jdbc:postgresql://$host:$port/$db",
      user = env.getOrElse("POSTGRES_USER", "parlance"),
      password = env.getOrElse("POSTGRES_PASSWORD", "parlance"),
    )

/** コネクションプールとTransactor、マイグレーションの入口をまとめる。 */
final class Database(config: DbConfig) extends AutoCloseable:
  private val dataSource: HikariDataSource =
    val hc = HikariConfig()
    hc.setJdbcUrl(config.jdbcUrl)
    hc.setUsername(config.user)
    hc.setPassword(config.password)
    hc.setMaximumPoolSize(config.maxPoolSize)
    hc.setPoolName("parlance-exercise")
    HikariDataSource(hc)

  val xa: Transactor[Postgres] = Transactor(Postgres, dataSource)

  private val migrator = Migrator(Migrations.all, xa, PostgresCompiler)

  def migrate(): MigrateResult        = migrator.migrate()
  def status(): MigrationStatus       = migrator.status()
  def pretend(): List[PretendResult]  = migrator.pretend()
  def rollback(): RollbackResult      = migrator.rollback()

  /** エンティティ定義と実際のスキーマが一致しているかを調べる。 */
  def verifySchema(): List[VerifyResult] =
    xa.connect:
      List(verify[Note], verify[Tag], verify[NoteTag])

  def close(): Unit = dataSource.close()

object Database:
  def fromEnv(): Database = Database(DbConfig.fromEnv())
