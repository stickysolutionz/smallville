'use client';

import { Button } from '@tremor/react';
import { TrashIcon } from '@heroicons/react/24/outline';
import { useEffect, useState } from 'react';
import QuickModal from './modal';
import {
  Characteristic,
  addCharacteristic,
  getCharacteristics,
  removeCharacteristic
} from '../lib/smallville';

export default function PersonalityEditor({
  agentName,
  onClose
}: {
  agentName: string | null;
  onClose: () => void;
}) {
  const [characteristics, setCharacteristics] = useState<Characteristic[]>([]);
  const [newTrait, setNewTrait] = useState('');
  const [isPending, setPending] = useState(false);
  const [isLoading, setLoading] = useState(false);

  useEffect(() => {
    if (!agentName) return;

    setLoading(true);
    getCharacteristics(agentName).then((result) => {
      setCharacteristics(result);
      setLoading(false);
    });
  }, [agentName]);

  async function handleAdd() {
    if (!agentName || !newTrait.trim()) return;

    setPending(true);
    const result = await addCharacteristic(agentName, newTrait.trim());
    setCharacteristics(result.characteristics || []);
    setNewTrait('');
    setPending(false);
  }

  async function handleRemove(index: number) {
    if (!agentName) return;

    setPending(true);
    const result = await removeCharacteristic(agentName, index);
    setCharacteristics(result.characteristics || []);
    setPending(false);
  }

  return (
    <QuickModal
      setIsOpen={() => onClose()}
      isOpen={agentName !== null}
      title={agentName ? `${agentName}'s Personality` : ''}
    >
      <div className="text-left mt-2">
        <p className="text-sm text-gray-500 mb-2">
          These are the memories that define {agentName}&apos;s backstory and
          personality. Changes take effect on the next simulation tick.
        </p>

        {isLoading && <p className="text-sm text-gray-400">Loading...</p>}

        <ul className="space-y-2 max-h-64 overflow-y-auto">
          {characteristics.map((c) => (
            <li
              key={c.index}
              className="flex items-start justify-between gap-2 rounded-md border border-gray-100 p-2"
            >
              <span className="text-sm text-gray-700">{c.description}</span>
              <button
                disabled={isPending}
                onClick={() => handleRemove(c.index)}
                className="flex-shrink-0 text-gray-400 hover:text-red-500 disabled:opacity-50"
              >
                <TrashIcon className="h-4 w-4" />
              </button>
            </li>
          ))}
          {!isLoading && characteristics.length === 0 && (
            <li className="text-sm text-gray-400">No traits yet.</li>
          )}
        </ul>

        <div className="flex gap-2 mt-4">
          <input
            type="text"
            placeholder="e.g. Secretly afraid of the dark"
            value={newTrait}
            disabled={isPending}
            className="h-10 block w-full rounded-md border border-gray-200 px-3 focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm disabled:opacity-50"
            onChange={(e) => setNewTrait(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleAdd();
            }}
          />
          <Button loading={isPending} onClick={handleAdd}>
            Add
          </Button>
        </div>

        <div className="flex justify-end mt-4">
          <Button variant="secondary" color="gray" onClick={() => onClose()}>
            Done
          </Button>
        </div>
      </div>
    </QuickModal>
  );
}
