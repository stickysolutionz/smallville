'use client';

import { Button, TextInput, Select, SelectItem } from '@tremor/react';
import { UserPlusIcon, SparklesIcon } from '@heroicons/react/24/outline';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import QuickModal from './modal';
import { createAgent, createLocation, generateCharacter } from '../lib/smallville';

const NEW_LOCATION_VALUE = '__new__';

export default function CreateAgentForm({
  locations
}: {
  locations: { name: string }[];
}) {
  const router = useRouter();
  const [isOpen, setIsOpen] = useState(false);
  const [isPending, setPending] = useState(false);
  const [isGenerating, setGenerating] = useState(false);
  const [error, setError] = useState('');
  const busy = isPending || isGenerating;

  const [name, setName] = useState('');
  const [memories, setMemories] = useState('');
  const [activity, setActivity] = useState('idle');
  const [location, setLocation] = useState(locations[0]?.name || '');
  const [newLocation, setNewLocation] = useState('');

  function reset() {
    setName('');
    setMemories('');
    setActivity('idle');
    setNewLocation('');
    setError('');
  }

  async function handleGenerate() {
    setError('');
    setGenerating(true);

    const character = await generateCharacter();

    setGenerating(false);

    if (!character) {
      setError('Could not generate a character. Is the server running?');
      return;
    }

    setName(character.name);
    setMemories(character.memories.join('\n'));
  }

  async function handleSubmit() {
    setError('');

    const memoryList = memories
      .split('\n')
      .map((m) => m.trim())
      .filter((m) => m.length > 0);

    const finalLocation = location === NEW_LOCATION_VALUE ? newLocation.trim() : location;

    if (!name.trim()) {
      setError('Give your character a name.');
      return;
    }
    if (memoryList.length === 0) {
      setError('Add at least one starting memory - it doubles as their backstory/personality.');
      return;
    }
    if (!finalLocation) {
      setError('Choose or create a starting location.');
      return;
    }

    setPending(true);

    if (location === NEW_LOCATION_VALUE) {
      await createLocation(finalLocation);
    }

    const result = await createAgent(name.trim(), memoryList, finalLocation, activity.trim() || 'idle');

    setPending(false);

    if (result?.success === false) {
      setError('Something went wrong creating the character. Is the server running?');
      return;
    }

    reset();
    setIsOpen(false);
    router.refresh();
  }

  return (
    <>
      <Button icon={UserPlusIcon} onClick={() => setIsOpen(true)}>
        New Character
      </Button>

      <QuickModal setIsOpen={setIsOpen} isOpen={isOpen} title="Create a new character">
        <div className="space-y-4 mt-2 text-left">
          <Button
            icon={SparklesIcon}
            variant="secondary"
            loading={isGenerating}
            disabled={isPending}
            onClick={handleGenerate}
          >
            Generate personality
          </Button>

          <div>
            <label className="text-sm text-gray-500">Name</label>
            <TextInput
              placeholder="e.g. Priya"
              value={name}
              disabled={busy}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          <div>
            <label className="text-sm text-gray-500">
              Starting memories (one per line - this is their backstory/personality)
            </label>
            <textarea
              className="mt-1 block w-full rounded-md border border-gray-200 text-sm shadow-sm focus:border-indigo-500 focus:ring-indigo-500 disabled:opacity-50"
              placeholder={'Priya is a night-shift nurse who loves gardening.\nPriya is usually cheerful but hates being woken up early.'}
              rows={5}
              value={memories}
              disabled={busy}
              onChange={(e) => setMemories(e.target.value)}
            />
          </div>

          <div>
            <label className="text-sm text-gray-500">Starting activity</label>
            <TextInput
              placeholder="sleeping"
              value={activity}
              disabled={busy}
              onChange={(e) => setActivity(e.target.value)}
            />
          </div>

          <div>
            <label className="text-sm text-gray-500">Starting location</label>
            <Select value={location} onValueChange={setLocation} disabled={isPending}>
              {[
                ...locations.map((loc) => (
                  <SelectItem key={loc.name} value={loc.name}>
                    {loc.name}
                  </SelectItem>
                )),
                <SelectItem key={NEW_LOCATION_VALUE} value={NEW_LOCATION_VALUE}>
                  + Create a new location...
                </SelectItem>
              ]}
            </Select>

            {location === NEW_LOCATION_VALUE && (
              <div className="mt-2">
                <TextInput
                  placeholder="e.g. Town Library"
                  value={newLocation}
                  disabled={busy}
                  onChange={(e) => setNewLocation(e.target.value)}
                />
              </div>
            )}
          </div>

          {error && <p className="text-sm text-red-500">{error}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="secondary"
              color="gray"
              disabled={busy}
              onClick={() => {
                reset();
                setIsOpen(false);
              }}
            >
              Cancel
            </Button>
            <Button loading={isPending} disabled={isGenerating} onClick={handleSubmit}>
              Create
            </Button>
          </div>
        </div>
      </QuickModal>
    </>
  );
}
