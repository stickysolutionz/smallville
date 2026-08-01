'use client';

import { useEffect, useState } from 'react';
import { getCharacteristics } from '../../lib/smallville';
import { detectProfession, ProfessionMatch } from './profession-icons';

// Fetches each agent's characteristics once (not on every 5s poll) and
// caches the detected profession, if any, keyed by name.
export function useAgentProfessions(agentNames: string[]): Record<string, ProfessionMatch | null> {
  const [professions, setProfessions] = useState<Record<string, ProfessionMatch | null>>({});
  const namesKey = agentNames.join(',');

  useEffect(() => {
    let cancelled = false;
    const unresolved = agentNames.filter((name) => !(name in professions));
    if (unresolved.length === 0) return;

    Promise.all(
      unresolved.map(async (name) => {
        const characteristics = await getCharacteristics(name);
        const text = characteristics.map((c) => c.description).join(' ');
        return [name, detectProfession(text)] as const;
      })
    ).then((results) => {
      if (cancelled) return;
      setProfessions((prev) => {
        const next = { ...prev };
        for (const [name, match] of results) {
          next[name] = match;
        }
        return next;
      });
    });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [namesKey, professions]);

  return professions;
}
