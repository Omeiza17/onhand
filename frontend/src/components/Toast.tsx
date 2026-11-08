import { useEffect } from 'react'
import { UndoIcon } from './Icons.tsx'

export interface ToastMessage {
  /** changes on every new message, so the hide timer restarts */
  id: number
  text: string
  onUndo?: () => void
}

const VISIBLE_MS = 4000

/**
 * Dark card above the tab bar. The role="status" region is always rendered so screen
 * readers announce each new message; it hides itself after ~4s.
 */
export default function Toast({ toast, onDismiss }: { toast: ToastMessage | null; onDismiss: () => void }) {
  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(onDismiss, VISIBLE_MS)
    return () => window.clearTimeout(timer)
  }, [toast, onDismiss])

  return (
    <div className="toast-region" role="status">
      {toast && (
        <div className="toast" key={toast.id}>
          <div className="toast__text">{toast.text}</div>
          {toast.onUndo && (
            <button type="button" className="toast__action" onClick={toast.onUndo}>
              <UndoIcon size={18} />
              Undo
            </button>
          )}
        </div>
      )}
    </div>
  )
}
