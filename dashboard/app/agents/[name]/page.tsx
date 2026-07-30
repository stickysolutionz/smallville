'use client';

import { Card, Title, Text } from '@tremor/react';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { ArrowLeftIcon } from '@heroicons/react/24/outline';
import { getDiary, DiaryEntry } from '../../../lib/smallville';

const TYPE_STYLES: Record<string, string> = {
  Reflection: 'bg-violet-100 text-violet-700',
  Plan: 'bg-blue-100 text-blue-700',
  Observation: 'bg-gray-100 text-gray-600'
};

export default function AgentDiaryPage({
  params
}: {
  params: { name: string };
}) {
  const name = decodeURIComponent(params.name);
  const [diary, setDiary] = useState<DiaryEntry[]>([]);
  const [isLoading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      const result = await getDiary(name);
      if (!cancelled) {
        setDiary(result);
        setLoading(false);
      }
    }

    load();
    const poll = setInterval(load, 5000);

    return () => {
      cancelled = true;
      clearInterval(poll);
    };
  }, [name]);

  // Backend returns oldest-first; show most recent thoughts at the top.
  const timeline = [...diary].reverse();

  return (
    <main className="p-4 md:p-10 mx-auto max-w-3xl">
      <Link
        href="/"
        className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4"
      >
        <ArrowLeftIcon className="h-4 w-4" /> Back to agents
      </Link>

      <Title>{name}&apos;s Diary</Title>
      <Text>
        Plans, reflections, and observations generated during the
        simulation - most recent first.
      </Text>

      <Card className="mt-6">
        {isLoading && <Text className="text-gray-400">Loading...</Text>}

        {!isLoading && timeline.length === 0 && (
          <Text className="text-gray-400">
            Nothing yet - start or step the simulation forward to see{' '}
            {name} start thinking.
          </Text>
        )}

        <ul className="space-y-3">
          {timeline.map((entry, i) => (
            <li
              key={i}
              className="border-b border-gray-100 pb-3 last:border-0"
            >
              <div className="flex items-center gap-2 mb-1">
                <span
                  className={
                    'text-xs font-medium px-2 py-0.5 rounded-full ' +
                    (TYPE_STYLES[entry.type] || 'bg-gray-100 text-gray-600')
                  }
                >
                  {entry.type}
                </span>
                {entry.time && (
                  <span className="text-xs text-gray-400">{entry.time}</span>
                )}
              </div>
              <p className="text-sm text-gray-700 whitespace-pre-wrap">
                {entry.description}
              </p>
            </li>
          ))}
        </ul>
      </Card>
    </main>
  );
}
