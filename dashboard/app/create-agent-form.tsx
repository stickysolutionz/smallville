'use client';

import { Button, TextInput, Select, SelectItem } from '@tremor/react';
import { UserPlusIcon, SparklesIcon } from '@heroicons/react/24/outline';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import QuickModal from './modal';
import { createAgent, createLocation, generateCharacter } from '../lib/smallville';

const NEW_LOCATION_VALUE = '__new__';

/**
 * The six parts a character needs to be playable here, and all six are
 * required.
 *
 * Left to write a free list of traits, people write adjectives - "kind",
 * "highly analytical", "above average intelligence" - and none of those produce
 * anything at nine in the morning. These are the ones the simulation can act
 * on. The anchor matters most: where somebody means to be is what puts them in
 * a room with other people, and everything else follows from that.
 */
const SECTIONS = [
  {
    key: 'anchor',
    label: 'Daily anchor',
    hint: 'where they are, and when. the most important one',
    placeholder: 'Runs the auto shop on Main, open by seven, closed by six'
  },
  {
    key: 'want',
    label: 'Want',
    hint: 'something a single day will not settle',
    placeholder: 'To buy the building before the landlord sells it out from under him'
  },
  {
    key: 'behavior',
    label: 'Something they do',
    hint: 'a verb, not an adjective',
    placeholder: 'Fixes things that are not broken when he has something on his mind'
  },
  {
    key: 'flaw',
    label: 'Flaw',
    hint: 'something that costs them, and they do it anyway',
    placeholder: 'Undercharges people he likes and cannot afford to'
  },
  {
    key: 'tie',
    label: 'Someone off-screen',
    hint: 'family, an ex, a creditor. nobody who lives in town',
    placeholder: 'His brother co-signed the shop lease and has not spoken to him since'
  },
  {
    key: 'tell',
    label: 'A tell',
    hint: 'something another person could notice by watching',
    placeholder: 'Wipes his hands on a rag long after they are clean'
  }
] as const;

type SectionKey = (typeof SECTIONS)[number]['key'];

/**
 * What the dial actually shifts is what somebody wants and who pays for it -
 * not whether their adjectives are pleasant.
 */
function alignmentLabel(alignment: number) {
  if (alignment < 15) return '- dangerous, and at ease with it';
  if (alignment < 35) return '- selfish, takes when it suits them';
  if (alignment < 65) return '- ordinary, mixed motives';
  if (alignment < 85) return '- puts others first, and pays for it';
  return '- good in a way that costs them';
}

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
  // The six parts a character needs to be playable here. A loose list of
  // traits reliably comes out as adjectives - "kind", "analytical", "above
  // average intelligence" - none of which produce anything at nine in the
  // morning. These are the ones that drive the simulation.
  const [anchor, setAnchor] = useState('');
  const [want, setWant] = useState('');
  const [behavior, setBehavior] = useState('');
  const [flaw, setFlaw] = useState('');
  const [tie, setTie] = useState('');
  const [tell, setTell] = useState('');
  const [alignment, setAlignment] = useState(50);
  const [activity, setActivity] = useState('idle');
  const [location, setLocation] = useState(locations[0]?.name || '');
  const [newLocation, setNewLocation] = useState('');

  function reset() {
    setName('');
    setAnchor('');
    setWant('');
    setBehavior('');
    setFlaw('');
    setTie('');
    setTell('');
    setActivity('idle');
    setNewLocation('');
    setError('');
  }

  async function handleGenerate() {
    setError('');
    setGenerating(true);

    const character = await generateCharacter(alignment);

    setGenerating(false);

    if (!character) {
      setError('Could not generate a character. Is the server running?');
      return;
    }

    setName(character.name);
    setAnchor(character.anchor ?? '');
    setWant(character.want ?? '');
    setBehavior(character.behavior ?? '');
    setFlaw(character.flaw ?? '');
    setTie(character.tie ?? '');
    setTell(character.tell ?? '');
  }

  const values: Record<SectionKey, string> = { anchor, want, behavior, flaw, tie, tell };
  const setters: Record<SectionKey, (value: string) => void> = {
    anchor: setAnchor,
    want: setWant,
    behavior: setBehavior,
    flaw: setFlaw,
    tie: setTie,
    tell: setTell
  };

  async function handleSubmit() {
    setError('');

    // Flattened on the way out. The sections shape what gets written; a label
    // like "Tell:" would only read oddly inside a conversation prompt.
    const missing = SECTIONS.filter((section) => !values[section.key].trim());
    const memoryList = SECTIONS.map((section) => values[section.key].trim()).filter(Boolean);

    const finalLocation = location === NEW_LOCATION_VALUE ? newLocation.trim() : location;

    if (!name.trim()) {
      setError('Give your character a name.');
      return;
    }
    if (missing.length > 0) {
      setError(`Still needed: ${missing.map((section) => section.label.toLowerCase()).join(', ')}.`);
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
          <div className="rounded-md border border-gray-200 p-3">
            <label className="text-sm text-gray-500">
              Who are they, roughly? {alignmentLabel(alignment)}
            </label>
            <input
              type="range"
              min={0}
              max={100}
              step={5}
              value={alignment}
              disabled={busy}
              className="mt-2 w-full accent-indigo-500 disabled:opacity-50"
              onChange={(e) => setAlignment(Number(e.target.value))}
            />
            <div className="flex justify-between text-xs text-gray-400">
              <span>dangerous</span>
              <span>ordinary</span>
              <span>selfless</span>
            </div>

            <Button
              className="mt-3"
              icon={SparklesIcon}
              variant="secondary"
              loading={isGenerating}
              disabled={isPending}
              onClick={handleGenerate}
            >
              Generate personality
            </Button>
          </div>

          <div>
            <label className="text-sm text-gray-500">Name</label>
            <TextInput
              placeholder="e.g. Priya"
              value={name}
              disabled={busy}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          {SECTIONS.map(({ key, label, hint, placeholder }) => (
            <div key={key}>
              <label className="text-sm text-gray-500">
                {label} <span className="text-gray-400">- {hint}</span>
              </label>
              <textarea
                className="mt-1 block w-full rounded-md border border-gray-200 text-sm shadow-sm focus:border-indigo-500 focus:ring-indigo-500 disabled:opacity-50"
                placeholder={placeholder}
                rows={2}
                value={values[key]}
                disabled={busy}
                onChange={(e) => setters[key](e.target.value)}
              />
            </div>
          ))}

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
