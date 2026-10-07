package parlance.exercise.db

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.fixtures.TestContainersFixtures
import ma.chinespirit.parlance.*
import munit.{AnyFixture, FunSuite}
import org.testcontainers.utility.DockerImageName

/** 専用のPostgreSQLコンテナに対して、マイグレーションとエンティティの読み書きを通しで確認する。 */
class DatabaseSuite extends FunSuite, TestContainersFixtures:

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

  test("1 - migrate applies every migration once"):
    val first = db.migrate()
    assertEquals(first.appliedCount, Migrations.all.size)
    assertEquals(first.applied.map(_.migration), List("1_notes", "2_tags"))

    val second = db.migrate()
    assertEquals(second.appliedCount, 0)

    val status = db.status()
    assertEquals(status.applied.size, Migrations.all.size)
    assertEquals(status.pending, Nil)

  test("2 - schema matches the entity definitions"):
    db.verifySchema().foreach: result =>
      assert(!result.hasErrors, result.prettyPrint)

  test("3 - notes get timestamps on create and update"):
    db.xa.transact:
      val note = NoteCreator("最初のメモ", "本文").create()
      assert(note.id > 0L)
      assertEquals(note.createdAt, note.updatedAt)

      note.copy(title = "改題").save()
      val reloaded = note.refresh()
      assertEquals(reloaded.title, "改題")
      assertEquals(reloaded.createdAt, note.createdAt)
      assert(!reloaded.updatedAt.isBefore(note.updatedAt))

  test("4 - tags attach, sync and cascade through note_tags"):
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
      assertEquals(Tag.repo.count, 2L)

  test("5 - tag names are unique"):
    intercept[SqlException]:
      db.xa.transact:
        TagCreator("web").create()

  test("6 - rollback removes the latest batch"):
    val result = db.rollback()
    assertEquals(result.rolledBackCount, Migrations.all.size)
    assertEquals(db.status().applied, Nil)
    assert(db.verifySchema().forall(_.hasErrors))
