package parlance.exercise.db

import ma.chinespirit.parlance.migrate.*

import java.util.UUID

/** メモ本体のテーブルを作る。 */
object V1_Notes extends MigrationDef:
  val version = 1L
  val name    = "notes"
  val up      = List(
    createTable("notes")(
      (List(
        id(),
        column[String]("title").varchar(200),
        column[String]("body"),
      ) ++ timestamps())*,
    ),
  )
  val down = List(dropTable("notes"))

/** タグと、メモとタグを結ぶ中間テーブルを作る。 */
object V2_Tags extends MigrationDef:
  val version = 2L
  val name    = "tags"
  val up      = List(
    createTable("tags")(
      id(),
      column[String]("name").varchar(30).unique,
    ),
    createTable("note_tags")(
      id(),
      column[Long]("note_id").references("notes", "id", FkAction.Cascade, FkAction.NoAction),
      column[Long]("tag_id").references("tags", "id", FkAction.Cascade, FkAction.NoAction),
    ),
    alterTable("note_tags")(
      addUniqueIndex("note_id", "tag_id"),
      addIndex("tag_id"),
    ),
  )
  val down = List(
    dropTable("note_tags"),
    dropTable("tags"),
  )

/** 3テーブルの主キーをbigintの連番からULIDに変える。既存の行は変換して残す。 */
object V3_UlidIds extends MigrationDef:
  val version = 3L
  val name    = "ulid_ids"

  // ULIDをuuid型で返す。先頭6バイトがミリ秒のUNIX時刻、残り10バイトが乱数で、既定の時刻はclock_timestamp()。
  // 乱数はgen_random_uuid()の16バイトから取るので拡張は要らない。
  private val createGenUlid = raw(
    """CREATE FUNCTION gen_ulid(ts timestamptz DEFAULT clock_timestamp()) RETURNS uuid
      |LANGUAGE sql VOLATILE AS $$
      |  SELECT encode(
      |    overlay(uuid_send(gen_random_uuid())
      |            PLACING substring(int8send(floor(extract(epoch FROM ts) * 1000)::bigint) FROM 3 FOR 6)
      |            FROM 1 FOR 6),
      |    'hex')::uuid
      |$$""".stripMargin,
  )

  private def ulidColumn(name: String) = column[UUID](name).defaultExpression("gen_ulid()")

  /** 旧主キーを参照する制約と索引を外し、新しい列を`id`、`note_id`、`tag_id`に改名して張り直す。 */
  private def swapKeys(newNoteTagCols: (String, String, String), newIdCols: (String, String)) = List(
    alterTable("note_tags")(
      dropForeignKey("note_tags_note_id_fkey"),
      dropForeignKey("note_tags_tag_id_fkey"),
      dropIndex("note_tags_note_id_tag_id_unique"),
      dropIndex("note_tags_tag_id_idx"),
      AlterOp.DropConstraint("note_tags_pkey"),
      dropColumn("id"),
      dropColumn("note_id"),
      dropColumn("tag_id"),
      renameColumn(newNoteTagCols._1, "id"),
      renameColumn(newNoteTagCols._2, "note_id"),
      renameColumn(newNoteTagCols._3, "tag_id"),
      AlterOp.AddPrimaryKey(List("id")),
    ),
    alterTable("notes")(
      AlterOp.DropConstraint("notes_pkey"),
      dropColumn("id"),
      renameColumn(newIdCols._1, "id"),
      AlterOp.AddPrimaryKey(List("id")),
    ),
    alterTable("tags")(
      AlterOp.DropConstraint("tags_pkey"),
      dropColumn("id"),
      renameColumn(newIdCols._2, "id"),
      AlterOp.AddPrimaryKey(List("id")),
    ),
    alterTable("note_tags")(
      addForeignKey("note_id", "notes", "id", FkAction.Cascade),
      addForeignKey("tag_id", "tags", "id", FkAction.Cascade),
      addUniqueIndex("note_id", "tag_id"),
      addIndex("tag_id"),
    ),
  )

  val up = List(
    createGenUlid,
    // 既存のメモにはcreated_atを時刻に使ったULIDを割り当てる。タグと中間テーブルは移行時刻のULIDになる。
    alterTable("notes")(AlterOp.AddColumn(ulidColumn("ulid"))),
    raw("UPDATE notes SET ulid = gen_ulid(created_at)"),
    alterTable("tags")(AlterOp.AddColumn(ulidColumn("ulid"))),
    alterTable("note_tags")(
      AlterOp.AddColumn(ulidColumn("ulid")),
      AlterOp.AddColumn(column[UUID]("note_ulid").nullable),
      AlterOp.AddColumn(column[UUID]("tag_ulid").nullable),
    ),
    raw(
      """UPDATE note_tags nt SET note_ulid = n.ulid, tag_ulid = t.ulid
        |FROM notes n, tags t WHERE n.id = nt.note_id AND t.id = nt.tag_id""".stripMargin,
    ),
    alterTable("note_tags")(AlterOp.SetNotNull("note_ulid"), AlterOp.SetNotNull("tag_ulid")),
  ) ++ swapKeys(("ulid", "note_ulid", "tag_ulid"), ("ulid", "ulid"))

  // 元の連番は残っていないので、bigserialで新しく振り直す。
  val down = List(
    alterTable("notes")(AlterOp.AddColumn(column[Long]("legacy_id").autoIncrement)),
    alterTable("tags")(AlterOp.AddColumn(column[Long]("legacy_id").autoIncrement)),
    alterTable("note_tags")(
      AlterOp.AddColumn(column[Long]("legacy_id").autoIncrement),
      AlterOp.AddColumn(column[Long]("legacy_note_id").nullable),
      AlterOp.AddColumn(column[Long]("legacy_tag_id").nullable),
    ),
    raw(
      """UPDATE note_tags nt SET legacy_note_id = n.legacy_id, legacy_tag_id = t.legacy_id
        |FROM notes n, tags t WHERE n.id = nt.note_id AND t.id = nt.tag_id""".stripMargin,
    ),
    alterTable("note_tags")(AlterOp.SetNotNull("legacy_note_id"), AlterOp.SetNotNull("legacy_tag_id")),
  ) ++ swapKeys(("legacy_id", "legacy_note_id", "legacy_tag_id"), ("legacy_id", "legacy_id")) ++ List(
    raw("DROP FUNCTION gen_ulid(timestamptz)"),
  )

object Migrations:
  /** versionの昇順で並べる。Migratorはこの順序を検証する。 */
  val all: List[MigrationDef] = List(V1_Notes, V2_Tags, V3_UlidIds)
