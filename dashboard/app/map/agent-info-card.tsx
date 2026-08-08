'use client';

import { ChevronRightIcon } from '@heroicons/react/24/outline';
import Link from 'next/link';
import { useState } from 'react';
import QuickModal from '../modal';
import { User } from '../table';
import { AVATAR_PALETTE } from './avatar-palette';
import { AvatarPreference } from './avatar-preferences';
import { ProfessionMatch } from './profession-icons';
import PersonSilhouette from './person-silhouette';

export default function AgentInfoCard({
  agent,
  preference,
  profession,
  onChangePreference,
  onClose
}: {
  agent: User | null;
  preference: AvatarPreference | null;
  profession: ProfessionMatch | null;
  onChangePreference: (preference: AvatarPreference) => void;
  onClose: () => void;
}) {
  const [appearanceOpen, setAppearanceOpen] = useState(false);

  return (
    <QuickModal
      isOpen={agent !== null}
      setIsOpen={() => onClose()}
      title={agent?.name ?? ''}
    >
      {agent && preference && (
        <div className="mt-2 space-y-4 text-left">
          <p className="text-sm text-gray-500">
            <span className="font-medium text-gray-700">{agent.location}</span>
            {' — '}
            {agent.action}
          </p>

          <div>
            <button
              className="flex items-center gap-1 text-xs font-medium text-gray-500 hover:text-gray-700"
              onClick={() => setAppearanceOpen((open) => !open)}
            >
              <ChevronRightIcon
                className={'h-3 w-3 transition-transform ' + (appearanceOpen ? 'rotate-90' : '')}
              />
              Appearance
            </button>

            {appearanceOpen && (
              <div className="mt-2">
                {profession && (
                  <div className="flex gap-2 mb-3">
                    <button
                      className={
                        'rounded-md px-2.5 py-1 text-xs font-medium ' +
                        (preference.mode === 'auto'
                          ? 'bg-indigo-100 text-indigo-700'
                          : 'bg-gray-100 text-gray-500')
                      }
                      onClick={() => onChangePreference({ ...preference, mode: 'auto' })}
                    >
                      Auto ({profession.emoji} {profession.label})
                    </button>
                    <button
                      className={
                        'rounded-md px-2.5 py-1 text-xs font-medium ' +
                        (preference.mode === 'custom'
                          ? 'bg-indigo-100 text-indigo-700'
                          : 'bg-gray-100 text-gray-500')
                      }
                      onClick={() => onChangePreference({ ...preference, mode: 'custom' })}
                    >
                      Custom
                    </button>
                  </div>
                )}

                {(preference.mode === 'custom' || !profession) && (
                  <div className="space-y-2">
                    <div className="flex gap-2">
                      {(['male', 'female'] as const).map((gender) => (
                        <button
                          key={gender}
                          className={
                            'flex h-9 w-9 items-center justify-center rounded-full bg-gray-100 text-gray-500 ' +
                            (preference.gender === gender ? 'ring-2 ring-indigo-500' : '')
                          }
                          onClick={() => onChangePreference({ ...preference, mode: 'custom', gender })}
                        >
                          <PersonSilhouette gender={gender} className="h-5 w-5" />
                        </button>
                      ))}
                    </div>
                    <div className="flex gap-1.5">
                      {AVATAR_PALETTE.map((entry) => (
                        <button
                          key={entry.key}
                          title={entry.label}
                          className={
                            'h-6 w-6 rounded-full ' +
                            entry.bgClass +
                            ' ' +
                            (preference.color === entry.key ? 'ring-2 ' + entry.ringClass : '')
                          }
                          onClick={() => onChangePreference({ ...preference, mode: 'custom', color: entry.key })}
                        >
                          <span className={'block h-2 w-2 mx-auto rounded-full ' + entry.textClass} style={{ backgroundColor: 'currentColor' }} />
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          <Link
            href={`/agents/${encodeURIComponent(agent.name)}`}
            className="inline-block text-sm font-medium text-indigo-600 hover:text-indigo-500"
          >
            View diary →
          </Link>
        </div>
      )}
    </QuickModal>
  );
}
