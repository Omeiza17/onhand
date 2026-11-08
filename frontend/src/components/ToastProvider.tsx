import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { ToastContext } from '../lib/toast.ts'
import Toast, { type ToastMessage } from './Toast.tsx'

/** Holds the one toast the app shows at a time and renders it. */
export default function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastMessage | null>(null)
  const nextId = useRef(0)

  const show = useCallback((text: string, onUndo?: () => void) => {
    nextId.current += 1
    setToast({ id: nextId.current, text, onUndo })
  }, [])
  const dismiss = useCallback(() => setToast(null), [])
  const api = useMemo(() => ({ show, dismiss }), [show, dismiss])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <Toast toast={toast} onDismiss={dismiss} />
    </ToastContext.Provider>
  )
}
