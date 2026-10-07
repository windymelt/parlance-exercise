package parlance.exercise

import com.github.plokhotnyuk.jsoniter_scala.core.*

import wvlet.airframe.ulid.ULID

import java.time.Instant

class NoteJsonSuite extends munit.FunSuite:
  private val t0 = Instant.parse("2026-10-07T00:00:00Z")
  private val id = ULID.fromString("01K6ZV8G3MG0F4W4T3X1B5F3VN")

  test("a note without tags still serializes the tags field as an empty array"):
    val json = writeToString(Note(id, "t", "", Nil, t0, t0))
    assert(json.contains("\"tags\":[]"), json)

  test("a list of notes keeps empty tags arrays too"):
    val json = writeToString(List(Note(id, "t", "", Nil, t0, t0)))
    assert(json.contains("\"tags\":[]"), json)

  test("the id is written as the 26-character ULID string and read back"):
    val json = writeToString(Note(id, "t", "", Nil, t0, t0))
    assert(json.contains("\"id\":\"01K6ZV8G3MG0F4W4T3X1B5F3VN\""), json)
    assertEquals(readFromString[Note](json).id, id)

  test("NoteInput without a tags field decodes to an empty list"):
    assertEquals(readFromString[NoteInput]("""{"title":"t","body":"b"}""").tags, Nil)
