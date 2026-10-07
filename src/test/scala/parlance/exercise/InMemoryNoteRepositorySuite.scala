package parlance.exercise

import java.time.Instant

class InMemoryNoteRepositorySuite extends munit.FunSuite:
  private val t0 = Instant.parse("2026-10-07T00:00:00Z")

  test("create assigns increasing ids and trims the title"):
    val repo = InMemoryNoteRepository()
    val a    = repo.create(NoteInput("  a  ", "body"), t0)
    val b    = repo.create(NoteInput("b", ""), t0)
    assertEquals(a.id, 1L)
    assertEquals(b.id, 2L)
    assertEquals(a.title, "a")

  test("list is ordered by updatedAt descending"):
    val repo = InMemoryNoteRepository()
    val old  = repo.create(NoteInput("old", ""), t0)
    val fresh = repo.create(NoteInput("fresh", ""), t0.plusSeconds(10))
    assertEquals(repo.list().map(_.id), List(fresh.id, old.id))
    repo.update(old.id, NoteInput("old", "edited"), t0.plusSeconds(20))
    assertEquals(repo.list().map(_.id), List(old.id, fresh.id))

  test("update keeps createdAt and returns None for unknown id"):
    val repo = InMemoryNoteRepository()
    val n    = repo.create(NoteInput("n", ""), t0)
    val u    = repo.update(n.id, NoteInput("n2", "x"), t0.plusSeconds(1))
    assertEquals(u.map(_.createdAt), Some(t0))
    assertEquals(u.map(_.updatedAt), Some(t0.plusSeconds(1)))
    assertEquals(repo.update(999, NoteInput("", ""), t0), None)

  test("delete reports whether the note existed"):
    val repo = InMemoryNoteRepository()
    val n    = repo.create(NoteInput("n", ""), t0)
    assert(repo.delete(n.id))
    assert(!repo.delete(n.id))
    assertEquals(repo.find(n.id), None)

  test("tags are trimmed, de-duplicated and stored"):
    val repo = InMemoryNoteRepository()
    val n    = repo.create(NoteInput("n", "", List(" scala ", "", "web", "scala")), t0)
    assertEquals(n.tags, List("scala", "web"))
    val u = repo.update(n.id, NoteInput("n", "", List("web")), t0.plusSeconds(1))
    assertEquals(u.map(_.tags), Some(List("web")))
    val cleared = repo.update(n.id, NoteInput("n", "", Nil), t0.plusSeconds(2))
    assertEquals(cleared.map(_.tags), Some(Nil))

  test("initial notes seed the id counter"):
    val repo = InMemoryNoteRepository(List(Note(7, "seed", "", Nil, t0, t0)))
    assertEquals(repo.create(NoteInput("next", ""), t0).id, 8L)

class NoteInputSuite extends munit.FunSuite:
  test("title is required"):
    assertEquals(NoteInput.validate(NoteInput("   ", "x")).keySet, Set("title"))

  test("valid input has no errors"):
    assertEquals(NoteInput.validate(NoteInput("t", "x")), Map.empty)

  test("over-long title and body are rejected"):
    val errs = NoteInput.validate(NoteInput("a" * 201, "b" * 100_001))
    assertEquals(errs.keySet, Set("title", "body"))

  test("tag count and tag length limits are enforced after normalization"):
    val tooMany = (1 to NoteInput.MaxTagCount + 1).map(i => s"t$i").toList
    assertEquals(NoteInput.validate(NoteInput("t", "", tooMany)).keySet, Set("tags"))
    val tooLong = List("a" * (NoteInput.MaxTagLength + 1))
    assertEquals(NoteInput.validate(NoteInput("t", "", tooLong)).keySet, Set("tags"))
    // 空要素と重複はタグ数に数えない
    val dupes = List.fill(NoteInput.MaxTagCount + 5)("same") ++ List("", "  ")
    assertEquals(NoteInput.validate(NoteInput("t", "", dupes)), Map.empty)
