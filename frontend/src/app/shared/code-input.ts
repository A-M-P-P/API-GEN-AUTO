/** Accept natural typing while keeping path separators and invalid symbols visible for validation. */
export function normalizeCode(value: string, allowUnderscore = false): string {
  const normalized = value.trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/\s+/g, '-');
  return allowUnderscore ? normalized : normalized.replace(/_/g, '-');
}
