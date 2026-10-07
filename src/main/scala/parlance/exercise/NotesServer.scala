package parlance.exercise

import dev.capslock.inertia.core.JsoniterProps.*
import dev.capslock.inertia.core.{*, given}
import dev.capslock.inertia.tapir.*
import parlance.exercise.db.Database
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.json.jsoniter.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.NettyFutureServer
import wvlet.airframe.ulid.ULID

import java.nio.file.{Files, Path}
import java.util.concurrent.CountDownLatch
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

object NotesServer:

  private given ExecutionContext = ExecutionContext.global

  private val port: Int = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(9000)

  private val distDir: Path = Path.of("frontend", "dist")

  /** `frontend/dist`にビルド成果物があればそれを配信し、なければVite devサーバを参照する。 */
  private val layout: Layout =
    Layout.ViteBuilt
      .fromManifest(distDir)
      .getOrElse(Layout.ViteDev(sys.env.getOrElse("VITE_DEV_SERVER", "http://localhost:5173")))

  // ParlanceがSQLを実行する前にSQLログのレベルを決める
  Logging.configure()

  // 接続情報はcompose.yamlと同じ環境変数から読む。DBに接続できなければここで失敗する。
  private val database: Database     = Database.fromEnv()
  private val repo: NoteRepository   = ParlanceNoteRepository(database)

  // jsonBody[NoteInput]に必要なスキーマ。JSONコーデックはNoteInputのコンパニオンにある。
  private given Schema[NoteInput] = Schema.derived

  // Inertiaのページオブジェクトに入れるURL。パスとクエリだけを使う。
  private val requestUrl: EndpointInput[String] =
    extractFromRequest(req => req.uri.copy(scheme = None, authority = None).toString)

  private val notFound: EndpointOutput[String] = statusCode(StatusCode.NotFound).and(stringBody)

  private def renderIndex(
    headers: InertiaHeaders,
    url: String,
    method: String,
    selected: Option[Note],
    errors: Map[String, String] = Map.empty,
  ): InertiaResponse =
    InertiaTapir.render(
      headers,
      url,
      method,
      component = "Notes/Index",
      props = Props.of(
        "notes"    -> prop(repo.list()),
        "selected" -> selected.map(prop(_)).getOrElse(RawJson.raw("null")),
      ),
      errors = errors,
      version = layout.assetVersion,
      layoutFn = layout.render,
    )

  private def redirect(headers: InertiaHeaders, method: String, location: String): InertiaResponse =
    InertiaTapir.redirect(method, location, 303, headers.isInertia)

  // ── Endpoints ───────────────────────────────────────────────────────────────

  // パスのidは文字列として受け、ここでULIDに解釈する。Tapirのパス復号で失敗させると既定では405になるため、
  // ULIDでない文字列は存在しないメモと同じ404にする。
  private def parseId(text: String): Either[String, ULID] =
    ULID.unapply(text).toRight("Not Found")

  // パス入力のないエンドポイントはすべてのパスに一致する。ルートだけに限定するため空パスを指定する。
  private val index = endpoint.get
    .in("")
    .in(InertiaTapir.inertiaHeadersInput)
    .in(requestUrl)
    .out(InertiaTapir.inertiaOutput)
    .serverLogicSuccess[Future]: (headers, url) =>
      Future.successful(renderIndex(headers, url, "GET", selected = None))

  private val show = endpoint.get
    .in("notes" / path[String]("id"))
    .in(InertiaTapir.inertiaHeadersInput)
    .in(requestUrl)
    .out(InertiaTapir.inertiaOutput)
    .errorOut(notFound)
    .serverLogic[Future]: (rawId, headers, url) =>
      Future.successful:
        parseId(rawId).flatMap(id => repo.find(id).toRight("Not Found")).map: note =>
          renderIndex(headers, url, "GET", selected = Some(note))

  private val create = endpoint.post
    .in("notes")
    .in(InertiaTapir.inertiaHeadersInput)
    .in(requestUrl)
    .in(jsonBody[NoteInput])
    .out(InertiaTapir.inertiaOutput)
    .serverLogicSuccess[Future]: (headers, url, input) =>
      Future.successful:
        val errors = NoteInput.validate(input)
        if errors.nonEmpty then renderIndex(headers, url, "POST", selected = None, errors = errors)
        else
          val note = repo.create(input)
          redirect(headers, "POST", s"/notes/${note.id}")

  private val update = endpoint.put
    .in("notes" / path[String]("id"))
    .in(InertiaTapir.inertiaHeadersInput)
    .in(requestUrl)
    .in(jsonBody[NoteInput])
    .out(InertiaTapir.inertiaOutput)
    .errorOut(notFound)
    .serverLogic[Future]: (rawId, headers, url, input) =>
      Future.successful:
        for
          id       <- parseId(rawId)
          existing <- repo.find(id).toRight("Not Found")
        yield
          val errors = NoteInput.validate(input)
          if errors.nonEmpty then renderIndex(headers, url, "PUT", selected = Some(existing), errors = errors)
          else
            repo.update(id, input)
            redirect(headers, "PUT", s"/notes/$id")

  private val delete = endpoint.delete
    .in("notes" / path[String]("id"))
    .in(InertiaTapir.inertiaHeadersInput)
    .out(InertiaTapir.inertiaOutput)
    .errorOut(notFound)
    .serverLogic[Future]: (rawId, headers) =>
      Future.successful:
        parseId(rawId).filterOrElse(repo.delete, "Not Found").map(_ => redirect(headers, "DELETE", "/"))

  // vite buildの成果物を配信する。devサーバ利用時はディレクトリがなく404を返すだけで問題はない。
  private val assets: ServerEndpoint[Any, Future] =
    staticFilesGetServerEndpoint[Future]("assets")(distDir.resolve("assets").toAbsolutePath.toString)

  val endpoints: List[ServerEndpoint[Any, Future]] =
    List(index, show, create, update, delete, assets)

  // ── Main ────────────────────────────────────────────────────────────────────

  def main(args: Array[String]): Unit =
    val migrated = database.migrate()
    if migrated.appliedCount > 0 then println(s"database: applied ${migrated.appliedCount} migration(s)")
    else println("database: schema is up to date")

    val binding = Await.result(NettyFutureServer().port(port).addEndpoints(endpoints).start(), Duration.Inf)
    println(s"listening on http://localhost:${binding.port}")
    if Files.isDirectory(distDir) then println(s"frontend: serving built assets from $distDir")
    else println("frontend: expecting Vite dev server (run `npm run dev` in frontend/)")

    val stopped = new CountDownLatch(1)
    sys.addShutdownHook:
      Await.result(binding.stop(), 10.seconds)
      database.close()
      stopped.countDown()
    stopped.await()
