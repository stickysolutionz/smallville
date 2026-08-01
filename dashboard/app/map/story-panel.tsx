'use client';

import { Card, Title, Text, Button } from '@tremor/react';
import { SparklesIcon } from '@heroicons/react/24/outline';
import { useEffect, useRef, useState } from 'react';
import { StoryState, getStory, generateStory } from '../../lib/smallville';

const POLL_MS = 20000;

// "2 hours 15 minutes" / "1 hour" / "40 minutes" - never "1 hours" or a
// decimal, and skips the minutes remainder once it's small enough to be
// noise on top of a multi-hour gap.
function formatElapsed(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const mins = Math.round(minutes % 60);

  if (hours === 0) {
    return `${mins} minute${mins === 1 ? '' : 's'}`;
  }
  if (mins < 5) {
    return `${hours} hour${hours === 1 ? '' : 's'}`;
  }
  return `${hours} hour${hours === 1 ? '' : 's'} ${mins} minutes`;
}

export default function StoryPanel() {
  const [story, setStory] = useState<StoryState | null>(null);
  const [isGenerating, setGenerating] = useState(false);
  const [errorNote, setErrorNote] = useState<string | null>(null);
  const generatingRef = useRef(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      if (generatingRef.current) return;
      const result = await getStory();
      if (!cancelled) setStory(result);
    }

    load();
    const poll = setInterval(load, POLL_MS);

    return () => {
      cancelled = true;
      clearInterval(poll);
    };
  }, []);

  async function handleGenerate() {
    generatingRef.current = true;
    setGenerating(true);
    setErrorNote(null);

    const result = await generateStory();

    if (result) {
      setStory(result);
    } else {
      setErrorNote("Couldn't generate the story right now — try again in a moment.");
    }

    setGenerating(false);
    generatingRef.current = false;
  }

  const paragraphs = (story?.story || '')
    .split(/\n\s*\n/)
    .map((p) => p.trim())
    .filter(Boolean);

  const showCatchUpNudge =
    story?.exists && story.minutesSinceUpdate != null && story.minutesSinceUpdate >= 1;

  return (
    <Card>
      <div className="flex items-start justify-between gap-4">
        <div>
          <Title>The Story So Far</Title>
          {story?.exists && (
            <Text className="text-gray-400">
              Current through {story.asOfDate}, {story.asOfTime}
            </Text>
          )}
          {!story?.exists && (
            <Text className="text-gray-400">
              Nothing generated yet — click below whenever you want to know what's been happening.
            </Text>
          )}
          {showCatchUpNudge && (
            <Text className="text-gray-400">
              {formatElapsed(story!.minutesSinceUpdate!)} has happened since then — whenever you
              want it, it's ready.
            </Text>
          )}
        </div>
        <Button
          icon={SparklesIcon}
          loading={isGenerating}
          disabled={isGenerating}
          onClick={handleGenerate}
        >
          {story?.exists ? 'Continue the story' : 'Generate story so far'}
        </Button>
      </div>

      {story?.message && (
        <Text className="mt-3 text-gray-400 italic">{story.message}</Text>
      )}

      {errorNote && <Text className="mt-3 text-red-500">{errorNote}</Text>}

      {paragraphs.length > 0 && (
        <div className="mt-4 space-y-3">
          {paragraphs.map((paragraph, i) => (
            <Text key={i} className="text-gray-700 leading-relaxed">
              {paragraph}
            </Text>
          ))}
        </div>
      )}
    </Card>
  );
}
