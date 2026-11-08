// Typed fetch wrapper for the Onhand API. All API calls go through here.
// URLs are relative: same origin via the Vite proxy (dev) or the k3s Ingress (prod).

const BASE = '/api/v1'

/** RFC 9457 Problem Details, as returned by the backend on errors. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  [extension: string]: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemDetail

  constructor(status: number, problem?: ProblemDetail) {
    super(problem?.detail ?? problem?.title ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json, application/problem+json')
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const res = await fetch(`${BASE}${path}`, { ...init, headers })

  if (!res.ok) {
    const isProblem = res.headers.get('Content-Type')?.includes('json') ?? false
    throw new ApiError(res.status, isProblem ? ((await res.json()) as ProblemDetail) : undefined)
  }
  return (res.status === 204 ? undefined : await res.json()) as T
}

/** JSON body helper: `request(path, json('POST', body))`. */
export function json(method: string, body?: unknown): RequestInit {
  return { method, body: body === undefined ? undefined : JSON.stringify(body) }
}

/** A short, human message for any failure from `request`. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const fieldErrors = error.problem?.errors
    if (Array.isArray(fieldErrors) && fieldErrors.length > 0) {
      const first = fieldErrors[0] as { message?: string }
      if (first.message) return first.message
    }
    return error.message
  }
  return 'Could not reach Onhand. Check your connection.'
}
