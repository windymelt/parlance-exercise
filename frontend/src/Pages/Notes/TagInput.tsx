import { useState } from 'react'
import type { KeyboardEvent } from 'react'

const MAX_TAG_LENGTH = 30

interface Props {
  tags: string[]
  onChange: (tags: string[]) => void
  invalid?: boolean
  describedBy?: string
}

/** チップ表示のタグ入力。Enterかカンマで追加し、空欄でのBackspaceで末尾を削除し、×で個別に削除する。 */
export default function TagInput({ tags, onChange, invalid, describedBy }: Props) {
  const [draft, setDraft] = useState('')

  function commit(raw: string) {
    const next = raw
      .split(',')
      .map((t) => t.trim())
      .filter((t) => t !== '' && !tags.includes(t))
    if (next.length > 0) onChange([...tags, ...next])
    setDraft('')
  }

  function remove(tag: string) {
    onChange(tags.filter((t) => t !== tag))
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    // IME変換中のEnterは変換の確定なので無視する
    if (e.nativeEvent.isComposing) return
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault()
      commit(draft)
    } else if (e.key === 'Backspace' && draft === '' && tags.length > 0) {
      e.preventDefault()
      remove(tags[tags.length - 1]!)
    }
  }

  return (
    <div className={`tag-input${invalid ? ' is-invalid' : ''}`} aria-invalid={invalid}>
      <ul className="tag-input__list" aria-label="タグ">
        {tags.map((tag) => (
          <li key={tag} className="tag">
            <span className="tag__label">{tag}</span>
            <button
              type="button"
              className="tag__remove"
              onClick={() => remove(tag)}
              aria-label={`タグ「${tag}」を削除`}
            >
              ×
            </button>
          </li>
        ))}
        <li className="tag-input__draft">
          <input
            type="text"
            value={draft}
            maxLength={MAX_TAG_LENGTH}
            placeholder={tags.length === 0 ? 'タグを追加（Enter で確定）' : ''}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={onKeyDown}
            onBlur={() => commit(draft)}
            aria-describedby={describedBy}
          />
        </li>
      </ul>
    </div>
  )
}
