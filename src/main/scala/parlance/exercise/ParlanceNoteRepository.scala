package parlance.exercise

import ma.chinespirit.parlance.*
import parlance.exercise.db.{Database, NoteCreator, Tag, TagCreator, Note as NoteRow}

/** Parlanceのエンティティを使う実装。notesの1行とタグをまとめてWeb層のNoteに変換する。 */
final class ParlanceNoteRepository(database: Database) extends NoteRepository:
  private val xa = database.xa

  def list(): List[Note] = xa.connect:
    NoteRow.repo.query
      .orderBy(_.updatedAt, SortOrder.Desc)
      .orderBy(_.id, SortOrder.Desc)
      .withRelated(NoteRow.tags)
      .run()
      .map(toView)
      .toList

  def find(id: Long): Option[Note] = xa.connect:
    NoteRow.repo.findById(id).map(row => toView(row, row.load(NoteRow.tags)))

  def create(input: NoteInput): Note = xa.transact:
    val in   = input.normalized
    val row  = NoteCreator(in.title, in.body).create()
    val tags = ensureTags(in.tags)
    if tags.nonEmpty then NoteRow.tags.attach(row, tags*)
    toView(row, tags)

  def update(id: Long, input: NoteInput): Option[Note] = xa.transact:
    NoteRow.repo.findById(id).map: existing =>
      val in = input.normalized
      existing.copy(title = in.title, body = in.body).save()
      val tags = ensureTags(in.tags)
      NoteRow.tags.sync(existing, tags)
      toView(existing.refresh(), tags)

  def delete(id: Long): Boolean = xa.transact:
    NoteRow.repo.findById(id) match
      case Some(row) =>
        // note_tagsの行は外部キーのON DELETE CASCADEで消える。タグ自体は他のメモと共有しうるので残す。
        row.delete()
        true
      case None => false

  // 生SQL用のテーブル・列参照。列名はエンティティ定義から取るので、改名するとコンパイルエラーになる。
  private val tagsTable = TableInfo[TagCreator, Tag, Long]

  /** 名前に対応するタグ行を揃える。無い名前だけを挿入し、同名の同時挿入はON CONFLICT DO NOTHINGで無視される。 */
  private def ensureTags(names: List[String])(using DbCon[Postgres]): Vector[Tag] =
    if names.isEmpty then Vector.empty
    else
      // RepoのfirstOrCreateは検索してから挿入するだけなので、同名タグの同時作成で一意制約違反になる。
      // insertOnConflictとinsertAllIgnoringはParlance 0.1.0では競合対象列の値を誤った
      // コーデックで書き込む（Creator側の添字でエンティティ側のコーデックを引く）ため、
      // idを持たないCreatorでは使えない。そのためSQL補間でON CONFLICTを直接書く。
      names.foreach: name =>
        sql"INSERT INTO $tagsTable (${tagsTable.name}) VALUES ($name) ON CONFLICT (${tagsTable.name}) DO NOTHING".update.run()
      Tag.repo.query.where(_.name in names).run()

  private def toView(row: NoteRow, tags: Vector[Tag]): Note =
    Note(row.id, row.title, row.body, tags.map(_.name).sorted.toList, row.createdAt, row.updatedAt)

  private def toView(pair: (NoteRow, Vector[Tag])): Note = toView(pair._1, pair._2)
