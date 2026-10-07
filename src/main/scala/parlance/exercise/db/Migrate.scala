package parlance.exercise.db

import parlance.exercise.Logging

import scala.util.Using

/** マイグレーションを操作するCLI。`sbt "runMain parlance.exercise.db.Migrate status"`のように呼ぶ。 */
object Migrate:
  private val usage =
    """usage: Migrate [migrate|status|pretend|rollback|verify]
      |  migrate   未適用のマイグレーションをすべて適用する（既定）
      |  status    適用済みと未適用の一覧を表示する
      |  pretend   未適用分の SQL を表示するだけで実行しない
      |  rollback  直近のバッチを取り消す
      |  verify    エンティティ定義とスキーマの差分を表示する""".stripMargin

  def main(args: Array[String]): Unit =
    val command = args.headOption.getOrElse("migrate")
    Logging.configure()
    Using.resource(Database.fromEnv()): db =>
      command match
        case "migrate" =>
          val result = db.migrate()
          if result.appliedCount == 0 then println("適用するマイグレーションはありません")
          else
            println(s"${result.appliedCount} 件を batch ${result.batch.getOrElse(0)} として適用しました")
            result.applied.foreach(m => println(s"  ${m.migration}"))
        case "status" =>
          val status = db.status()
          println(s"適用済み: ${status.applied.size} 件")
          status.applied.foreach(m => println(s"  ${m.migration} (batch ${m.batch}, ${m.appliedAt})"))
          println(s"未適用: ${status.pending.size} 件")
          status.pending.foreach(m => println(s"  ${m.version}_${m.name}"))
        case "pretend" =>
          db.pretend().foreach: p =>
            println(s"-- ${p.migrationDef.version}_${p.migrationDef.name}")
            p.compiledSql.foreach(sql => println(s"$sql;"))
        case "rollback" =>
          val result = db.rollback()
          println(s"${result.rolledBackCount} 件を取り消しました")
          result.rolledBack.foreach(m => println(s"  ${m.migration}"))
        case "verify" =>
          val results = db.verifySchema()
          results.foreach(r => println(r.prettyPrint))
          if results.exists(_.hasErrors) then
            System.err.println("スキーマがエンティティ定義と一致しません")
            sys.exit(1)
        case other =>
          System.err.println(s"不明なコマンドです: $other")
          System.err.println(usage)
          sys.exit(2)
