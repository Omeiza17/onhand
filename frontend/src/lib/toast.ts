import { createContext, useContext } from 'react'

export interface ToastApi {
  /** Shows a message above the tab bar for ~4s, with an Undo action when `onUndo` is given. */
  show: (text: string, onUndo?: () => void) => void
  dismiss: () => void
}

export const ToastContext = createContext<ToastApi>({ show: () => {}, dismiss: () => {} })

/** The app-wide undo toast: any screen can show a change and offer Undo. */
export function useToast(): ToastApi {
  return useContext(ToastContext)
}
