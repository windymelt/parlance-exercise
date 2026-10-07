/** サーバ側の`parlance.exercise.Note`と同じ形。idはULIDの26文字、時刻はISO-8601文字列。 */
export interface Note {
  id: string
  title: string
  body: string
  tags: string[]
  createdAt: string
  updatedAt: string
}

/** `Notes/Index`が受け取るprops。 */
export interface NotesIndexProps {
  notes: Note[]
  selected: Note | null
  errors: Partial<Record<'title' | 'body' | 'tags', string>>
}
