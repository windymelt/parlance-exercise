package parlance.exercise

import wvlet.airframe.ulid.ULID

/** メモの永続化の境界。時刻の設定とidの採番は実装側が行う。 */
trait NoteRepository:
  def list(): List[Note]
  def find(id: ULID): Option[Note]
  def create(input: NoteInput): Note
  def update(id: ULID, input: NoteInput): Option[Note]
  def delete(id: ULID): Boolean
