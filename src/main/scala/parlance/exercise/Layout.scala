package parlance.exercise

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

import java.nio.file.{Files, Path}

/** Inertiaが生成したpageスクリプトとマウント要素をHTML文書で包む。 */
trait Layout:
  /** アセットバージョン。値が変わるとクライアントがフルリロードする。 */
  def assetVersion: String
  def render(content: String): String

object Layout:
  private val entry = "src/main.tsx"

  /** Viteのdevサーバを参照するレイアウト。HMRのためにReact Refreshの前処理を埋め込む。 */
  final class ViteDev(devServer: String) extends Layout:
    val assetVersion: String = "dev"
    def render(content: String): String =
      s"""|<!DOCTYPE html>
          |<html lang="ja">
          |<head>
          |  <meta charset="UTF-8">
          |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
          |  <title>メモ帳</title>
          |  <script type="module">
          |    import RefreshRuntime from "$devServer/@react-refresh"
          |    RefreshRuntime.injectIntoGlobalHook(window)
          |    window.$$RefreshReg$$ = () => {}
          |    window.$$RefreshSig$$ = () => (type) => type
          |    window.__vite_plugin_react_preamble_installed__ = true
          |  </script>
          |  <script type="module" src="$devServer/@vite/client"></script>
          |  <script type="module" src="$devServer/$entry"></script>
          |</head>
          |<body>
          |  $content
          |</body>
          |</html>""".stripMargin

  private final case class ManifestEntry(file: String, css: Option[List[String]])
  private given JsonValueCodec[Map[String, ManifestEntry]] = JsonCodecMaker.make

  /** `vite build`が出力したmanifest.jsonを参照するレイアウト。 */
  final class ViteBuilt(js: String, css: List[String]) extends Layout:
    val assetVersion: String = js.hashCode.toHexString
    def render(content: String): String =
      val cssTags = css.map(href => s"""  <link rel="stylesheet" href="/$href">""").mkString("\n")
      s"""|<!DOCTYPE html>
          |<html lang="ja">
          |<head>
          |  <meta charset="UTF-8">
          |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
          |  <title>メモ帳</title>
          |$cssTags
          |  <script type="module" src="/$js"></script>
          |</head>
          |<body>
          |  $content
          |</body>
          |</html>""".stripMargin

  object ViteBuilt:
    def fromManifest(distDir: Path): Option[ViteBuilt] =
      val manifest = distDir.resolve(".vite").resolve("manifest.json")
      Option.when(Files.isRegularFile(manifest)):
        val entries = readFromArray[Map[String, ManifestEntry]](Files.readAllBytes(manifest))
        val e       = entries.getOrElse(entry, throw new IllegalStateException(s"manifest に $entry がありません: $manifest"))
        ViteBuilt(e.file, e.css.getOrElse(Nil))
