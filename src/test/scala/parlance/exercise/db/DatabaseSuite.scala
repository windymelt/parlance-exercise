package parlance.exercise.db

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.fixtures.TestContainersFixtures
import ma.chinespirit.parlance.*
import ma.chinespirit.parlance.migrate.{Migrator, PostgresCompiler}
import munit.{AnyFixture, FunSuite}
import org.testcontainers.utility.DockerImageName
import parlance.exercise.Logging
import scribe.Level
import wvlet.airframe.ulid.ULID

import java.time.Instant

/** 専用のPostgreSQLコンテナに対して、マイグレーションとエンティティの読み書きを通しで確認する。 */
class DatabaseSuite extends FunSuite, TestContainersFixtures:

  // テストではSQLログを出さない。見たいときはSQL_LOG_LEVEL=debugのように環境変数で上書きする
  Logging.configure(defaultSqlLogLevel = Level.Info)

  private val container = ForAllContainerFixture(
    PostgreSQLContainer.Def(dockerImageName = DockerImageName.parse("postgres:18-alpine")).createContainer(),
  )

  private lazy val db: Database =
    val pg = container()
    Database(DbConfig(pg.jdbcUrl, pg.username, pg.password, maxPoolSize = 2))

  override def munitFixtures: Seq[AnyFixture[?]] = super.munitFixtures :+ container

  override def afterAll(): Unit =
    db.close()
    super.afterAll()

  private val legacyCreatedAt = Instant.parse("2026-10-01T12:34:56.789Z")

  test("1 - the bigint schema accepts legacy rows before the ULID migration"):
    val legacy = Migrator(Migrations.all.take(2), db.xa, PostgresCompiler).migrate()
    assertEquals(legacy.applied.map(_.migration), List("1_notes", "2_tags"))

    db.xa.transact:
      sql"INSERT INTO notes (title, body, created_at, updated_at) VALUES ('legacy', '', $legacyCreatedAt, $legacyCreatedAt)".update.run()
      sql"INSERT INTO tags (name) VALUES ('old'), ('unused')".update.run()
      sql"INSERT INTO note_tags (note_id, tag_id) SELECT n.id, t.id FROM notes n, tags t WHERE t.name = 'old'".update.run()

  test("2 - the ULID migration converts legacy rows and later runs are no-ops"):
    val converted = db.migrate()
    assertEquals(converted.applied.map(_.migration), List("3_ulid_ids"))
    assertEquals(db.migrate().appliedCount, 0)
    assertEquals(db.status().pending, Nil)

    db.xa.connect:
      val legacy = Note.repo.query.where(_.title === "legacy").run().head
      assertEquals(ULID.fromUUID(legacy.id).epochMillis, legacyCreatedAt.toEpochMilli)
      assertEquals(legacy.load(Note.tags).map(_.name), Vector("old"))
      assertEquals(Tag.repo.count, 2L)
      assertEquals(NoteTag.repo.count, 1L)

  test("3 - schema matches the entity definitions"):
    db.verifySchema().foreach: result =>
      assert(!result.hasErrors, result.prettyPrint)

  test("4 - notes get a ULID whose time is the creation time, and timestamps on create and update"):
    db.xa.transact:
      val note       = NoteCreator("最初のメモ", "本文").create()
      val ulidMillis = ULID.fromUUID(note.id).epochMillis
      // gen_ulid()の既定はclock_timestamp()で、created_atのCURRENT_TIMESTAMPはトランザクション開始時刻なので、同じかわずかに後になる
      assert(ulidMillis >= note.createdAt.toEpochMilli, s"$ulidMillis < ${note.createdAt}")
      assert(ulidMillis - note.createdAt.toEpochMilli < 60_000L)
      assertEquals(note.createdAt, note.updatedAt)

      note.copy(title = "改題").save()
      val reloaded = note.refresh()
      assertEquals(reloaded.title, "改題")
      assertEquals(reloaded.createdAt, note.createdAt)
      assert(!reloaded.updatedAt.isBefore(note.updatedAt))

  test("5 - tags attach, sync and cascade through note_tags"):
    db.xa.transact:
      val note  = NoteCreator("タグ付き", "").create()
      val scala = TagCreator("scala").create()
      val web   = TagCreator("web").create()

      Note.tags.attach(note, scala, web)
      assertEquals(note.load(Note.tags).map(_.name).sorted, Vector("scala", "web"))
      assertEquals(web.load(Tag.notes).map(_.id), Vector(note.id))

      val sync = Note.tags.sync(note, List(web))
      assertEquals((sync.attached, sync.detached, sync.unchanged), (0, 1, 1))
      assertEquals(note.load(Note.tags).map(_.name), Vector("web"))

      note.delete()
      assertEquals(NoteTag.repo.query.where(_.noteId === note.id).count(), 0L)
      assertEquals(Tag.repo.count, 4L)

  test("6 - tag names are unique"):
    intercept[SqlException]:
      db.xa.transact:
        TagCreator("web").create()

  test("7 - rollback removes one batch at a time and the ULID rollback keeps the rows"):
    val ulidBatch = db.rollback()
    assertEquals(ulidBatch.rolledBackCount, 1)
    assertEquals(db.status().applied.size, 2)
    // bigintに戻った列はUUIDのエンティティ定義と一致しない
    assert(db.verifySchema().exists(_.hasErrors))
    // 残っている関連はテスト1で入れたlegacyとoldの1件だけで、bigintの外部キーで結び直されている
    db.xa.connect:
      assertEquals(sql"SELECT count(*) FROM note_tags nt JOIN notes n ON n.id = nt.note_id JOIN tags t ON t.id = nt.tag_id".query[Long].run().head, 1L)
      assertEquals(sql"SELECT n.title FROM note_tags nt JOIN notes n ON n.id = nt.note_id".query[String].run(), Vector("legacy"))

    val legacyBatch = db.rollback()
    assertEquals(legacyBatch.rolledBackCount, 2)
    assertEquals(db.status().applied, Nil)
    assert(db.verifySchema().forall(_.hasErrors))
