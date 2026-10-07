package parlance.exercise

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.fixtures.TestContainersFixtures
import ma.chinespirit.parlance.*
import munit.{AnyFixture, FunSuite}
import org.testcontainers.utility.DockerImageName
import parlance.exercise.db.{Database, DbConfig, Tag}
import scribe.Level

/** 専用のPostgreSQLコンテナに対して、Web層が使うリポジトリの振る舞いを確認する。 */
class ParlanceNoteRepositorySuite extends FunSuite, TestContainersFixtures:

  // テストではSQLログを出さない。見たいときはSQL_LOG_LEVEL=debugのように環境変数で上書きする
  Logging.configure(defaultSqlLogLevel = Level.Info)

  private val container = ForAllContainerFixture(
    PostgreSQLContainer.Def(dockerImageName = DockerImageName.parse("postgres:18-alpine")).createContainer(),
  )

  private lazy val db: Database =
    val pg  = container()
    val dbx = Database(DbConfig(pg.jdbcUrl, pg.username, pg.password, maxPoolSize = 2))
    dbx.migrate()
    dbx

  private lazy val repo = ParlanceNoteRepository(db)

  override def munitFixtures: Seq[AnyFixture[?]] = super.munitFixtures :+ container

  override def afterAll(): Unit =
    db.close()
    super.afterAll()

  test("create normalizes tags and find returns the same note"):
    val created = repo.create(NoteInput("  最初のメモ ", "本文", List(" web ", "scala", "", "web")))
    assert(created.id > 0L)
    assertEquals(created.title, "最初のメモ")
    assertEquals(created.tags, List("scala", "web"))
    assertEquals(created.createdAt, created.updatedAt)
    assertEquals(repo.find(created.id), Some(created))

  test("list is ordered by updatedAt descending and updating moves a note to the top"):
    val a = repo.create(NoteInput("a", ""))
    val b = repo.create(NoteInput("b", ""))
    assertEquals(repo.list().map(_.id).take(2), List(b.id, a.id))

    val updated = repo.update(a.id, NoteInput("a2", "edited")).get
    assertEquals(updated.title, "a2")
    assertEquals(updated.createdAt, a.createdAt)
    assert(!updated.updatedAt.isBefore(b.updatedAt))
    assertEquals(repo.list().map(_.id).take(2), List(a.id, b.id))

  test("update syncs tags and keeps shared tags for other notes"):
    val n1 = repo.create(NoteInput("n1", "", List("shared", "only1")))
    val n2 = repo.create(NoteInput("n2", "", List("shared")))
    assertEquals(db.xa.connect(Tag.repo.query.where(_.name === "shared").count()), 1L)

    val updated = repo.update(n1.id, NoteInput("n1", "", List("new")))
    assertEquals(updated.map(_.tags), Some(List("new")))
    assertEquals(repo.find(n2.id).map(_.tags), Some(List("shared")))

  test("update and delete report unknown ids"):
    assertEquals(repo.update(999_999L, NoteInput("x", "")), None)
    assert(!repo.delete(999_999L))

  test("delete removes the note and its tag links but keeps the tags"):
    val before = db.xa.connect(Tag.repo.count)
    val n      = repo.create(NoteInput("to delete", "", List("keep-me")))
    assert(repo.delete(n.id))
    assertEquals(repo.find(n.id), None)
    assertEquals(db.xa.connect(Tag.repo.count), before + 1)
