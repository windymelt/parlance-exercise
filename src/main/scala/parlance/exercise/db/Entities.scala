package parlance.exercise.db

import ma.chinespirit.parlance.*

import java.time.Instant

// ── notes ────────────────────────────────────────────────────────────────────

/** `notes`へ挿入するときの入力。idと時刻はDBが採番・設定する。 */
@SqlName("notes")
@Table(SqlNameMapper.CamelToSnakeCase)
final case class NoteCreator(
  title: String,
  body: String,
) extends CreatorOf[Note] derives DbCodec

/** `notes`の1行。タグは`note_tags`を介した多対多なので、この行には含まれない。 */
@SqlName("notes")
@Table(SqlNameMapper.CamelToSnakeCase)
final case class Note(
  @Id id: Long,
  title: String,
  body: String,
  @createdAt createdAt: Instant,
  @updatedAt updatedAt: Instant,
) derives EntityMeta, HasCreatedAt, HasUpdatedAt

object Note:
  val tags = Relationship.belongsToMany[Note, Tag]("note_tags", "note_id", "tag_id")

  // Timestampsを混ぜると、挿入時にcreated_atとupdated_atを、更新時にupdated_atをCURRENT_TIMESTAMPにする
  given repo: (Repo[NoteCreator, Note, Long] & Timestamps[NoteCreator, Note, Long]) =
    new Repo[NoteCreator, Note, Long] with Timestamps[NoteCreator, Note, Long]

// ── tags ─────────────────────────────────────────────────────────────────────

@SqlName("tags")
@Table(SqlNameMapper.CamelToSnakeCase)
final case class TagCreator(
  name: String,
) extends CreatorOf[Tag] derives DbCodec

/** `tags`の1行。nameは一意で、複数のメモから共有される。 */
@SqlName("tags")
@Table(SqlNameMapper.CamelToSnakeCase)
final case class Tag(
  @Id id: Long,
  name: String,
) derives EntityMeta

object Tag:
  val notes = Relationship.belongsToMany[Tag, Note]("note_tags", "tag_id", "note_id")

  given repo: Repo[TagCreator, Tag, Long] = Repo[TagCreator, Tag, Long]()

// ── note_tags ────────────────────────────────────────────────────────────────

@SqlName("note_tags")
@Table(SqlNameMapper.CamelToSnakeCase)
final case class NoteTagCreator(
  noteId: Long,
  tagId: Long,
) extends CreatorOf[NoteTag] derives DbCodec

/** `note_tags`の1行。メモとタグの組は一意で、どちらかを削除すると連鎖して消える。 */
@SqlName("note_tags")
@Table(SqlNameMapper.CamelToSnakeCase)
final case class NoteTag(
  @Id id: Long,
  noteId: Long,
  tagId: Long,
) derives EntityMeta

object NoteTag:
  val note = Relationship.belongsTo[NoteTag, Note](_.noteId, _.id)
  val tag  = Relationship.belongsTo[NoteTag, Tag](_.tagId, _.id)

  given repo: Repo[NoteTagCreator, NoteTag, Long] = Repo[NoteTagCreator, NoteTag, Long]()
