const AVATAR_COLORS = [
  '#2B59FF', '#D6455C', '#0E9F6E', '#C2650C',
  '#8B3FA0', '#0E7C86', '#A88B00', '#4B5563',
];

export function getInitials(name: string): string {
  const trimmed = name?.trim() ?? '';
  if (!trimmed) return '?';
  return trimmed.charAt(0).toUpperCase();
}

export function getAvatarColor(name: string): string {
  const trimmed = name?.trim() ?? '';
  let hash = 0;
  for (let i = 0; i < trimmed.length; i++) {
    hash = trimmed.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}
