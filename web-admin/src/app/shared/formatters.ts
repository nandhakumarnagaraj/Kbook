export function formatCurrency(value: number | null | undefined): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2
  }).format(value ?? 0);
}

export function formatDate(value: number | null | undefined): string {
  if (!value) {
    return '-';
  }
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value));
}

export function formatAge(epochMs: number | null | undefined): string {
  if (!epochMs) return '-';
  const diffMs = Date.now() - epochMs;
  const days = Math.floor(diffMs / 86400000);
  if (days > 0) return `${days}d ago`;
  const hours = Math.floor(diffMs / 3600000);
  if (hours > 0) return `${hours}h ago`;
  const mins = Math.floor(diffMs / 60000);
  return mins > 0 ? `${mins}m ago` : 'just now';
}
