import { Head, Link, router, useForm } from '@inertiajs/react'
import { useEffect } from 'react'
import type { FormEvent } from 'react'
import type { Note, NotesIndexProps } from '../../types'
import TagInput from './TagInput'

export default function NotesIndex({ notes, selected, errors }: NotesIndexProps) {
  return (
    <>
      <Head title={selected ? `${selected.title} - メモ帳` : 'メモ帳'} />
      <div className="app">
        <aside className="sidebar">
          <header className="sidebar__header">
            <h1 className="sidebar__title">メモ帳</h1>
            <Link href="/" className="button button--primary" preserveScroll>
              + 新しいメモ
            </Link>
          </header>
          <NoteList notes={notes} selectedId={selected?.id ?? null} />
        </aside>
        <main className="editor">
          {/* 選択中のメモが変わるたびにkeyでフォーム状態を作り直す */}
          <NoteEditor key={selected?.id ?? 'new'} note={selected} serverErrors={errors} />
        </main>
      </div>
    </>
  )
}

function NoteList({ notes, selectedId }: { notes: Note[]; selectedId: number | null }) {
  if (notes.length === 0) {
    return <p className="sidebar__empty">メモはまだありません</p>
  }
  return (
    <ul className="note-list">
      {notes.map((note) => (
        <li key={note.id}>
          <Link
            href={`/notes/${note.id}`}
            className={`note-item${note.id === selectedId ? ' note-item--active' : ''}`}
            preserveScroll
          >
            <span className="note-item__title">{note.title || '(無題)'}</span>
            <span className="note-item__excerpt">{excerpt(note.body)}</span>
            {note.tags.length > 0 && (
              <span className="note-item__tags">
                {note.tags.map((tag) => (
                  <span key={tag} className="tag tag--small">
                    {tag}
                  </span>
                ))}
              </span>
            )}
            <time className="note-item__time" dateTime={note.updatedAt}>
              {formatTime(note.updatedAt)}
            </time>
          </Link>
        </li>
      ))}
    </ul>
  )
}

function NoteEditor({
  note,
  serverErrors,
}: {
  note: Note | null
  serverErrors: NotesIndexProps['errors']
}) {
  const form = useForm({
    title: note?.title ?? '',
    body: note?.body ?? '',
    tags: note?.tags ?? [],
  })
  const errors = { ...serverErrors, ...form.errors }

  function submit(e?: FormEvent) {
    e?.preventDefault()
    if (form.processing) return
    if (note) {
      form.put(`/notes/${note.id}`, { preserveScroll: true })
    } else {
      form.post('/notes', { preserveScroll: true })
    }
  }

  function remove() {
    if (!note) return
    if (!window.confirm(`「${note.title}」を削除しますか？`)) return
    router.delete(`/notes/${note.id}`, { preserveScroll: true })
  }

  // Ctrl+SまたはCmd+Sで保存する
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault()
        submit()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  })

  return (
    <form className="editor__form" onSubmit={submit}>
      <div className="editor__toolbar">
        <span className="editor__status">
          {note ? `最終更新: ${formatTime(note.updatedAt)}` : '新しいメモ'}
          {form.isDirty && <span className="editor__dirty"> ・未保存</span>}
          {form.recentlySuccessful && <span className="editor__saved"> ・保存しました</span>}
        </span>
        <div className="editor__actions">
          {note && (
            <button type="button" className="button button--danger" onClick={remove} disabled={form.processing}>
              削除
            </button>
          )}
          <button type="submit" className="button button--primary" disabled={form.processing || !form.isDirty}>
            {form.processing ? '保存中…' : '保存'}
          </button>
        </div>
      </div>

      <input
        className={`editor__title${errors.title ? ' is-invalid' : ''}`}
        type="text"
        placeholder="タイトル"
        value={form.data.title}
        onChange={(e) => form.setData('title', e.target.value)}
        autoFocus={!note}
        aria-invalid={Boolean(errors.title)}
        aria-describedby={errors.title ? 'title-error' : undefined}
      />
      {errors.title && (
        <p id="title-error" className="field-error">
          {errors.title}
        </p>
      )}

      <TagInput
        tags={form.data.tags}
        onChange={(tags) => form.setData('tags', tags)}
        invalid={Boolean(errors.tags)}
        describedBy={errors.tags ? 'tags-error' : undefined}
      />
      {errors.tags && (
        <p id="tags-error" className="field-error">
          {errors.tags}
        </p>
      )}

      <textarea
        className={`editor__body${errors.body ? ' is-invalid' : ''}`}
        placeholder="本文を書く…"
        value={form.data.body}
        onChange={(e) => form.setData('body', e.target.value)}
        aria-invalid={Boolean(errors.body)}
        aria-describedby={errors.body ? 'body-error' : undefined}
      />
      {errors.body && (
        <p id="body-error" className="field-error">
          {errors.body}
        </p>
      )}
    </form>
  )
}

function excerpt(body: string): string {
  const line = body.split('\n').find((l) => l.trim() !== '') ?? ''
  return line.length > 60 ? `${line.slice(0, 60)}…` : line
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return new Intl.DateTimeFormat('ja-JP', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(d)
}
