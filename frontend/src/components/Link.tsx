import type { AnchorHTMLAttributes, MouseEvent } from 'react'
import { navigate } from '../lib/router.ts'

/** An <a> that navigates without a page load (modifier-clicks still open normally). */
export function Link({ href, onClick, ...rest }: AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) {
  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event)
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey) return
    event.preventDefault()
    navigate(href)
  }
  return <a href={href} onClick={handleClick} {...rest} />
}
