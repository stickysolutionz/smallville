import { AVATAR_PALETTE, getPaletteEntry, PaletteEntry } from './avatar-palette';
import { ProfessionMatch } from './profession-icons';

// Purely cosmetic, zero effect on the simulation, so this lives in the
// browser rather than as a new backend field - the agent roster itself is
// already fully wiped on every backend restart, a server field would just
// orphan itself for no benefit.
export interface AvatarPreference {
  mode: 'auto' | 'custom';
  gender: 'male' | 'female';
  color: string;
}

const STORAGE_KEY = 'smallville-avatar-preferences';

type PreferenceMap = Record<string, AvatarPreference>;

function loadAll(): PreferenceMap {
  if (typeof window === 'undefined') return {};
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function saveAll(prefs: PreferenceMap) {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
  } catch {
    // ignore - cosmetic feature, not worth surfacing a storage error over
  }
}

// Deterministic so the map looks varied on first load without any setup,
// and so the same agent doesn't jump to a different default every render.
function defaultForName(name: string): { gender: 'male' | 'female'; color: string } {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) | 0;
  }
  const abs = Math.abs(hash);
  const gender = abs % 2 === 0 ? 'male' : 'female';
  const color = AVATAR_PALETTE[abs % AVATAR_PALETTE.length].key;
  return { gender, color };
}

export function getPreference(name: string): AvatarPreference {
  const stored = loadAll()[name];
  if (stored) return stored;
  return { mode: 'auto', ...defaultForName(name) };
}

export function setPreference(name: string, preference: AvatarPreference): AvatarPreference {
  const all = loadAll();
  all[name] = preference;
  saveAll(all);
  return preference;
}

export type ResolvedAvatar =
  | { kind: 'profession'; emoji: string; label: string }
  | { kind: 'silhouette'; gender: 'male' | 'female'; palette: PaletteEntry };

export function resolveAvatar(
  preference: AvatarPreference,
  profession: ProfessionMatch | null
): ResolvedAvatar {
  if (preference.mode === 'auto' && profession) {
    return { kind: 'profession', emoji: profession.emoji, label: profession.label };
  }
  return { kind: 'silhouette', gender: preference.gender, palette: getPaletteEntry(preference.color) };
}

// The same color an agent's avatar already shows on the map, so a group
// conversation's speaker labels stay visually consistent with the rest of
// the map instead of picking their own independent colors.
export function getSpeakerColorClass(name: string): string {
  return getPaletteEntry(getPreference(name).color).textClass;
}
