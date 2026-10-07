package parlance.exercise

import com.github.plokhotnyuk.jsoniter_scala.core.*

import java.time.Instant

class NoteJsonSuite extends munit.FunSuite:
  private val t0 = Instant.parse("2026-10-07T00:00:00Z")

  test("a note without tags still serializes the tags field as an empty array"):
    val json = writeToString(Note(1, "t", "", Nil, t0, t0))
    assert(json.contains("\"tags\":[]"), json)

  test("a list of notes keeps empty tags arrays too"):
    val json = writeToString(List(Note(1, "t", "", Nil, t0, t0)))
    assert(json.contains("\"tags\":[]"), json)

  test("NoteInput without a tags field decodes to an empty list"):
    assertEquals(readFromString[NoteInput]("""{"title":"t","body":"b"}""").tags, Nil)
