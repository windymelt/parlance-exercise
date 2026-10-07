package parlance.exercise

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

  test("normalized trims the title and de-duplicates tags"):
    val n = NoteInput("  a  ", "body", List(" scala ", "", "web", "scala")).normalized
    assertEquals(n.title, "a")
    assertEquals(n.tags, List("scala", "web"))
