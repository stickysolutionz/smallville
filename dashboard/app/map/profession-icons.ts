// Keyword detection against an agent's characteristics text, so a character
// with an established profession shows an icon for that job instead of a
// generic silhouette. Extend this list as new professions show up in the
// cast - each entry just needs a few keywords and an emoji that already
// reads as "the profession" on its own (no recoloring needed or possible).
export interface ProfessionMatch {
  label: string;
  emoji: string;
}

interface ProfessionEntry extends ProfessionMatch {
  keywords: string[];
}

const PROFESSIONS: ProfessionEntry[] = [
  {
    label: 'Cop',
    emoji: '👮',
    keywords: ['cop', 'police', 'officer', 'sheriff', 'detective']
  },
  {
    label: 'Doctor',
    emoji: '🧑‍⚕️',
    keywords: ['doctor', 'dr.', 'physician', 'nurse', 'surgeon', 'medic']
  }
];

export function detectProfession(characteristicsText: string): ProfessionMatch | null {
  const lower = ` ${characteristicsText.toLowerCase()} `;

  for (const profession of PROFESSIONS) {
    if (profession.keywords.some((keyword) => lower.includes(keyword))) {
      return { label: profession.label, emoji: profession.emoji };
    }
  }

  return null;
}
