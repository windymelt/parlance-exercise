package parlance.exercise

import com.github.plokhotnyuk.jsoniter_scala.core.*
import dev.capslock.inertia.cask.InertiaCask
import dev.capslock.inertia.core.JsoniterProps.*
import dev.capslock.inertia.core.{*, given}

import java.nio.file.{Files, Path}
import java.time.Instant

object NotesServer extends cask.MainRoutes:

  override def port: Int = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(9000)

  private val distDir: Path = Path.of("frontend", "dist")

  /** `frontend/dist`にビルド成果物があればそれを配信し、なければVite devサーバを参照する。 */
  private val layout: Layout =
    Layout.ViteBuilt
      .fromManifest(distDir)
      .getOrElse(Layout.ViteDev(sys.env.getOrElse("VITE_DEV_SERVER", "http://localhost:5173")))

  // DB導入時はParlanceを使った実装に差し替える。
  private val repo: NoteRepository =
    val now = Instant.now()
    InMemoryNoteRepository(
      List(
        Note(1, "はじめてのメモ", "ここに本文を書きます。\n\n左の一覧からメモを選ぶか、「新しいメモ」から作成してください。", List("使い方"), now, now),
        Note(2, "買い物リスト", "- 牛乳\n- 卵\n- コーヒー豆", List("家事", "todo"), now.minusSeconds(3600), now.minusSeconds(3600)),
      ),
    )

  private def renderIndex(
    req: cask.Request,
    selected: Option[Note],
    errors: Map[String, String] = Map.empty,
  ): cask.Response[String] =
    InertiaCask.render(
      req,
      component = "Notes/Index",
      props = Props.of(
        "notes"    -> prop(repo.list()),
        "selected" -> selected.map(prop(_)).getOrElse(RawJson.raw("null")),
      ),
      errors = errors,
      version = layout.assetVersion,
      layoutFn = layout.render,
    )

  private def notFound: cask.Response[String] = cask.Response("Not Found", statusCode = 404)

  @cask.get("/")
  def index(req: cask.Request) = renderIndex(req, selected = None)

  @cask.get("/notes/:id")
  def show(req: cask.Request, id: Long) =
    repo.find(id) match
      case Some(note) => renderIndex(req, selected = Some(note))
      case None       => notFound

  // @cask.postJsonは戻り値をJSONとして再シリアライズする。
  // Inertiaレスポンスをそのまま返すため、@cask.postで受けて本文を自分で読む。
  @cask.post("/notes")
  def create(req: cask.Request) =
    val input  = readFromString[NoteInput](req.text())
    val errors = NoteInput.validate(input)
    if errors.nonEmpty then renderIndex(req, selected = None, errors = errors)
    else
      val note = repo.create(input, Instant.now())
      InertiaCask.redirect(req, s"/notes/${note.id}")

  @cask.put("/notes/:id")
  def update(req: cask.Request, id: Long) =
    repo.find(id) match
      case None           => notFound
      case Some(existing) =>
        val input  = readFromString[NoteInput](req.text())
        val errors = NoteInput.validate(input)
        if errors.nonEmpty then renderIndex(req, selected = Some(existing), errors = errors)
        else
          repo.update(id, input, Instant.now())
          InertiaCask.redirect(req, s"/notes/$id")

  @cask.delete("/notes/:id")
  def delete(req: cask.Request, id: Long) =
    if repo.delete(id) then InertiaCask.redirect(req, "/") else notFound

  // vite buildの成果物を配信する。devサーバ利用時はディレクトリがなく404を返すだけで問題はない。
  @cask.staticFiles("/assets")
  def assets() = distDir.resolve("assets").toString

  initialize()

  if Files.isDirectory(distDir) then println(s"frontend: serving built assets from $distDir")
  else println("frontend: expecting Vite dev server (run `npm run dev` in frontend/)")
