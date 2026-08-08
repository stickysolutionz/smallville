// Fixed palette with literal Tailwind class strings. Tailwind's JIT scanner
// only generates CSS for class names it can see verbatim in source, so this
// can't be built as `text-${color}-500` at runtime - every combination has
// to be spelled out here.
export interface PaletteEntry {
  key: string;
  label: string;
  textClass: string;
  bgClass: string;
  ringClass: string;
}

export const AVATAR_PALETTE: PaletteEntry[] = [
  { key: 'rose', label: 'Rose', textClass: 'text-rose-500', bgClass: 'bg-rose-100', ringClass: 'ring-rose-500' },
  { key: 'amber', label: 'Amber', textClass: 'text-amber-500', bgClass: 'bg-amber-100', ringClass: 'ring-amber-500' },
  { key: 'emerald', label: 'Emerald', textClass: 'text-emerald-500', bgClass: 'bg-emerald-100', ringClass: 'ring-emerald-500' },
  { key: 'sky', label: 'Sky', textClass: 'text-sky-500', bgClass: 'bg-sky-100', ringClass: 'ring-sky-500' },
  { key: 'violet', label: 'Violet', textClass: 'text-violet-500', bgClass: 'bg-violet-100', ringClass: 'ring-violet-500' },
  { key: 'slate', label: 'Slate', textClass: 'text-slate-500', bgClass: 'bg-slate-100', ringClass: 'ring-slate-500' }
];

export function getPaletteEntry(key: string): PaletteEntry {
  return AVATAR_PALETTE.find((entry) => entry.key === key) ?? AVATAR_PALETTE[0];
}
