/**
 * What to save for a typed category: trimmed, `''` when blank, and the existing spelling when it
 * matches a category already in use ("grains" → "Grains").
 */
export function canonicalCategory(text: string, known: string[]): string {
  const trimmed = text.trim()
  return known.find((c) => c.toLowerCase() === trimmed.toLowerCase()) ?? trimmed
}
