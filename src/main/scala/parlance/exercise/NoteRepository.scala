package parlance.exercise

/** メモの永続化の境界。時刻の設定とidの採番は実装側が行う。 */
trait NoteRepository:
  def list(): List[Note]
  def find(id: Long): Option[Note]
  def create(input: NoteInput): Note
  def update(id: Long, input: NoteInput): Option[Note]
  def delete(id: Long): Boolean
