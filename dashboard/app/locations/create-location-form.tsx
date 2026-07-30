'use client';

import { Button, Select, SelectItem } from '@tremor/react';
import { MapPinIcon } from '@heroicons/react/24/outline';
import { useState } from 'react';
import QuickModal from '../modal';
import { createLocation } from '../../lib/smallville';
import { SmallvilleLocation } from './table';

const TOP_LEVEL_VALUE = '__top_level__';

export default function CreateLocationForm({
  locations,
  onCreated
}: {
  locations: SmallvilleLocation[];
  onCreated: () => void;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [isPending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [name, setName] = useState('');
  const [parent, setParent] = useState(TOP_LEVEL_VALUE);

  function reset() {
    setName('');
    setParent(TOP_LEVEL_VALUE);
    setError('');
  }

  async function handleSubmit() {
    setError('');

    if (!name.trim()) {
      setError('Give the location a name.');
      return;
    }

    const fullName =
      parent === TOP_LEVEL_VALUE ? name.trim() : `${parent}: ${name.trim()}`;

    setPending(true);
    const result = await createLocation(fullName);
    setPending(false);

    if (result?.success === false) {
      setError(
        'Could not create that location - it may already exist, or the server might be unreachable.'
      );
      return;
    }

    reset();
    setIsOpen(false);
    onCreated();
  }

  return (
    <>
      <Button icon={MapPinIcon} onClick={() => setIsOpen(true)}>
        New Location
      </Button>

      <QuickModal setIsOpen={setIsOpen} isOpen={isOpen} title="Create a new location">
        <div className="space-y-4 mt-2 text-left">
          <p className="text-sm text-gray-500">
            Agents can only move between locations that already exist, and
            they refer to rooms as &quot;Building: Room&quot; - make sub-locations
            here so agents can actually reach them instead of getting stuck.
          </p>

          <div>
            <label className="text-sm text-gray-500">Parent location</label>
            <Select value={parent} onValueChange={setParent} disabled={isPending}>
              {[
                <SelectItem key={TOP_LEVEL_VALUE} value={TOP_LEVEL_VALUE}>
                  None - top-level location
                </SelectItem>,
                ...locations.map((loc) => (
                  <SelectItem key={loc.name} value={loc.name}>
                    {loc.name}
                  </SelectItem>
                ))
              ]}
            </Select>
          </div>

          <div>
            <label className="text-sm text-gray-500">
              {parent === TOP_LEVEL_VALUE ? 'Location name' : 'Room / sub-location name'}
            </label>
            <input
              type="text"
              autoComplete="off"
              disabled={isPending}
              className="mt-1 h-10 block w-full rounded-md border border-gray-200 px-3 focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm disabled:opacity-50"
              placeholder={parent === TOP_LEVEL_VALUE ? 'e.g. Town Square' : 'e.g. Kitchen'}
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSubmit();
              }}
            />
            {parent !== TOP_LEVEL_VALUE && name.trim() && (
              <p className="text-xs text-gray-400 mt-1">
                Will be created as &quot;{parent}: {name.trim()}&quot;
              </p>
            )}
          </div>

          {error && <p className="text-sm text-red-500">{error}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="secondary"
              color="gray"
              disabled={isPending}
              onClick={() => {
                reset();
                setIsOpen(false);
              }}
            >
              Cancel
            </Button>
            <Button loading={isPending} onClick={handleSubmit}>
              Create
            </Button>
          </div>
        </div>
      </QuickModal>
    </>
  );
}
