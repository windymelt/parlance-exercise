# parlance-exercise

[inertia-scala](https://github.com/windymelt/inertia-scala) を使ったメモ帳アプリです。
サーバは Scala 3 + [Tapir](https://tapir.softwaremill.com/)（Netty バックエンド、`Future`）、フロントエンドは Vite + React + TypeScript で構成しています。
メモはタイトル・本文・タグを持ちます。

現時点ではメモの保存先はインメモリ（`InMemoryNoteRepository`）で、プロセスを再起動すると内容は消えます。
永続化は `NoteRepository` トレイトの実装を Parlance を使ったものに差し替える形で後から追加する予定です。

## 構成

```
build.sbt
src/main/scala/parlance/exercise/
  NotesServer.scala      Tapir のエンドポイント定義。InertiaTapir.render / redirect を呼ぶ
  Note.scala             Note モデル・フォーム入力・バリデーション・jsoniter コーデック
  NoteRepository.scala   永続化境界のトレイトとインメモリ実装
  Layout.scala           Inertia のページ要素を包む HTML レイアウト（dev / build の 2 種）
frontend/
  src/main.tsx           createInertiaApp のエントリ
  src/Pages/Notes/Index.tsx  一覧 + エディタの画面
  src/Pages/Notes/TagInput.tsx  チップ表示のタグ入力（Enter / カンマで追加、× で削除）
  src/types.ts           サーバ側 props と対応する型
  src/styles.css
```

## ルート

各エンドポイントは `InertiaTapir.inertiaHeadersInput` で Inertia のヘッダを受け取り、`InertiaTapir.inertiaOutput` でステータス・本文・ヘッダを返します。リクエスト本文は `tapir-jsoniter-scala` の `jsonBody[NoteInput]` で受けるため、JSON として不正な本文には Tapir が 400 を返します。

| メソッド | パス | 動作 |
| --- | --- | --- |
| GET | `/` | 一覧と新規作成フォーム |
| GET | `/notes/:id` | 一覧と対象メモのエディタ |
| POST | `/notes` | 作成。成功時は `/notes/:id` へ 303 |
| PUT | `/notes/:id` | 更新。成功時は `/notes/:id` へ 303 |
| DELETE | `/notes/:id` | 削除。成功時は `/` へ 303 |
| GET | `/assets/*` | `vite build` の成果物 |

作成・更新の本文は `{"title": string, "body": string, "tags": string[]}` です。タグは保存時に trim・空要素除去・重複除去され、1 件 30 文字以内、1 メモ 20 件以内に制限されます。

バリデーションエラー（タイトル未入力、タグ数超過など）は同じコンポーネントを `errors` 付きで再描画し、クライアントの `useForm` が `form.errors` として受け取ります。

`Note` の jsoniter コーデックは `transientEmpty = false` にしています。既定のままだとタグが空のメモで `tags` フィールドが出力から消え、フロントエンドの型 `tags: string[]` と合わなくなるためです。

## 開発時の起動

ターミナルを 2 つ使います。

```console
$ cd frontend && npm install && npm run dev   # Vite dev サーバ (http://localhost:5173)
$ sbt run                                      # Tapir サーバ (http://localhost:9000)
```

ブラウザでは http://localhost:9000 を開きます。`frontend/dist` が存在しない間、サーバは Vite dev サーバ（既定 `http://localhost:5173`、環境変数 `VITE_DEV_SERVER` で変更可）を参照する HTML を返します。

## ビルド成果物での起動

```console
$ cd frontend && npm run build   # tsc --noEmit && vite build
$ sbt run
```

`frontend/dist/.vite/manifest.json` があると、サーバはそこからエントリの JS / CSS を解決して `/assets` から配信します。
アセットバージョンにはビルド成果物のファイル名由来の値を使うので、再ビルド後に古いタブから操作すると Inertia の仕組みでフルリロードされます。

dev サーバに戻すには `frontend/dist` を削除してください。

## PostgreSQL（Docker Compose）

Parlance で永続化する際に使う PostgreSQL を `compose.yaml` で定義しています。

```console
$ docker compose up -d          # 起動。healthcheck が通るまで数秒かかります
$ docker compose ps             # STATUS が healthy になれば接続できます
$ docker compose down           # 停止（データは db-data ボリュームに残ります）
$ docker compose down -v        # 停止してデータも削除
```

既定の接続情報は次のとおりです。

| 項目 | 値 |
| --- | --- |
| ホスト / ポート | `localhost:5432` |
| データベース | `parlance_exercise` |
| ユーザー / パスワード | `parlance` / `parlance` |
| JDBC URL | `jdbc:postgresql://localhost:5432/parlance_exercise` |

値は環境変数 `POSTGRES_USER`、`POSTGRES_PASSWORD`、`POSTGRES_DB`、`POSTGRES_PORT` で上書きできます。プロジェクト直下に `.env` を置くと Compose が自動で読み込みます（`.env` は git 管理外です）。

```console
$ psql postgresql://parlance:parlance@localhost:5432/parlance_exercise
```

## Parlance（エンティティとマイグレーション）

永続化には [Parlance](https://github.com/lbialy/parlance) を使います。エンティティとマイグレーションは `src/main/scala/parlance/exercise/db/` にあります。

```
db/Entities.scala    Note / Tag / NoteTag のエンティティと Creator、Repo、リレーション
db/Migrations.scala  V1_Notes（notes）、V2_Tags（tags、note_tags）
db/Database.scala    環境変数からの接続設定、HikariCP、Transactor、Migrator
db/Migrate.scala     マイグレーションを操作する CLI
```

テーブルは `notes`、`tags`、`note_tags` の 3 つです。タグはメモ間で共有される独立したエンティティで、`note_tags` を介した多対多（`Note.tags` / `Tag.notes`）で結び付きます。`notes` の `created_at` と `updated_at` は `Timestamps` ミックスインが挿入・更新時に設定します。

Web 層の `parlance.exercise.Note`（タグ名の一覧を持つ DTO）と、`parlance.exercise.db.Note`（`notes` の 1 行）は別の型です。

接続情報は compose.yaml と同じ環境変数 `POSTGRES_HOST`、`POSTGRES_PORT`、`POSTGRES_DB`、`POSTGRES_USER`、`POSTGRES_PASSWORD` から組み立てます。

```console
$ docker compose up -d
$ sbt "runMain parlance.exercise.db.Migrate"           # 未適用のマイグレーションを適用
$ sbt "runMain parlance.exercise.db.Migrate status"    # 適用状況
$ sbt "runMain parlance.exercise.db.Migrate pretend"   # 実行せず SQL を表示
$ sbt "runMain parlance.exercise.db.Migrate verify"    # エンティティ定義とスキーマの照合
$ sbt "runMain parlance.exercise.db.Migrate rollback"  # 直近のバッチを取り消す
```

Web サーバ（`NotesServer`）はまだインメモリの `NoteRepository` を使っており、これらのエンティティには接続していません。

## テスト

```console
$ sbt test                       # munit テスト。DatabaseSuite は testcontainers で PostgreSQL コンテナを起動するため Docker が必要
$ cd frontend && npm run typecheck
```
