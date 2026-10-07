package parlance.exercise

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

import wvlet.airframe.ulid.ULID

import java.time.Instant

/** メモ1件。idはULIDの26文字、時刻はISO-8601文字列としてそのままフロントエンドへ渡す。 */
final case class Note(
  id: ULID,
  title: String,
  body: String,
  tags: List[String],
  createdAt: Instant,
  updatedAt: Instant,
)

object Note:
  /** ULIDをJSONの文字列として読み書きする。 */
  given JsonValueCodec[ULID] = new JsonValueCodec[ULID]:
    def decodeValue(in: JsonReader, default: ULID): ULID =
      val text = in.readString(null)
      ULID.unapply(text).getOrElse(in.decodeError(s"ULIDではありません: $text"))
    def encodeValue(x: ULID, out: JsonWriter): Unit = out.writeVal(x.toString)
    def nullValue: ULID                               = null

  // jsoniter-scalaは既定で空のコレクションを出力から省き、フロントエンドの型`tags: string[]`と一致しなくなる。
  // そのため空でも`"tags":[]`を出力する。マクロが設定を定数として読むので、valに切り出さず各呼び出しに直接書く。
  given JsonValueCodec[Note]       = JsonCodecMaker.make(CodecMakerConfig.withTransientEmpty(false))
  given JsonValueCodec[List[Note]] = JsonCodecMaker.make(CodecMakerConfig.withTransientEmpty(false))

/** 作成・更新フォームの入力。 */
final case class NoteInput(title: String, body: String, tags: List[String] = Nil):
  /** 保存する形に整える。タイトルとタグの前後の空白を除き、タグからは空要素と重複も除く。 */
  def normalized: NoteInput =
    copy(title = title.trim, tags = tags.map(_.trim).filter(_.nonEmpty).distinct)

object NoteInput:
  given JsonValueCodec[NoteInput] = JsonCodecMaker.make

  val MaxTitleLength = 200
  val MaxBodyLength  = 100_000
  val MaxTagLength   = 30
  val MaxTagCount    = 20

  /** 入力を検査する。エラーはInertiaの`errors`としてフォームへ返す。 */
  def validate(raw: NoteInput): Map[String, String] =
    val input  = raw.normalized
    val errors = Map.newBuilder[String, String]
    if input.title.isEmpty then errors += "title" -> "タイトルを入力してください"
    else if input.title.length > MaxTitleLength then errors += "title" -> s"タイトルは${MaxTitleLength}文字以内にしてください"
    if input.body.length > MaxBodyLength then errors += "body" -> s"本文は${MaxBodyLength}文字以内にしてください"
    if input.tags.length > MaxTagCount then errors += "tags" -> s"タグは${MaxTagCount}個以内にしてください"
    else if input.tags.exists(_.length > MaxTagLength) then errors += "tags" -> s"タグは1つ${MaxTagLength}文字以内にしてください"
    errors.result()
