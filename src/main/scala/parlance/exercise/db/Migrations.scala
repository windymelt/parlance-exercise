package parlance.exercise.db

import ma.chinespirit.parlance.migrate.*

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

object Migrations:
  /** versionの昇順で並べる。Migratorはこの順序を検証する。 */
  val all: List[MigrationDef] = List(V1_Notes, V2_Tags)
