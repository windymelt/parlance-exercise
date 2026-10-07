package parlance.exercise

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/** メモの永続化の境界。Parlance導入時はこのトレイトの実装を差し替える。 */
trait NoteRepository:
  def list(): List[Note]
  def find(id: Long): Option[Note]
  def create(input: NoteInput, now: Instant): Note
  def update(id: Long, input: NoteInput, now: Instant): Option[Note]
  def delete(id: Long): Boolean

/** 開発用のインメモリ実装。プロセスを再起動すると内容は消える。 */
final class InMemoryNoteRepository(initial: List[Note] = Nil) extends NoteRepository:
  private val lock   = new Object
  private var notes  = initial.map(n => n.id -> n).toMap // scalafix:ok DisableSyntax.var
  private val nextId = new AtomicLong(initial.map(_.id).maxOption.getOrElse(0L) + 1)

  def list(): List[Note] = lock.synchronized:
    notes.values.toList.sortBy(_.updatedAt)(using Ordering[Instant].reverse)

  def find(id: Long): Option[Note] = lock.synchronized(notes.get(id))

  def create(input: NoteInput, now: Instant): Note = lock.synchronized:
    val in   = input.normalized
    val note = Note(nextId.getAndIncrement(), in.title, in.body, in.tags, now, now)
    notes += note.id -> note
    note

  def update(id: Long, input: NoteInput, now: Instant): Option[Note] = lock.synchronized:
    notes.get(id).map: existing =>
      val in      = input.normalized
      val updated = existing.copy(title = in.title, body = in.body, tags = in.tags, updatedAt = now)
      notes += id -> updated
      updated

  def delete(id: Long): Boolean = lock.synchronized:
    val existed = notes.contains(id)
    notes -= id
    existed
