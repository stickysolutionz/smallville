'use client';

import { Card, Title, Text, Button } from '@tremor/react';
import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/react/24/outline';
import { useEffect, useState } from 'react';
import { getAllConversations, ConversationGroup } from '../../lib/smallville';
import CollapsibleSection from '../collapsible-section';
import { getSpeakerColorClass } from '../map/avatar-preferences';

const PAGE_SIZE = 20;

export default function ConversationsPage() {
  const [conversations, setConversations] = useState<ConversationGroup[]>([]);
  const [isLoading, setLoading] = useState(true);
  const [page, setPage] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      const result = await getAllConversations();
      if (!cancelled) {
        setConversations(result);
        setLoading(false);
      }
    }

    load();
    const poll = setInterval(load, 5000);

    return () => {
      cancelled = true;
      clearInterval(poll);
    };
  }, []);

  // Backend returns oldest-first; show the most recent conversation at the top.
  const timeline = [...conversations].reverse();
  const pageCount = Math.max(1, Math.ceil(timeline.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const pageItems = timeline.slice(
    currentPage * PAGE_SIZE,
    currentPage * PAGE_SIZE + PAGE_SIZE
  );

  return (
    <main className="p-4 md:p-10 mx-auto max-w-3xl">
      <Title>Conversations</Title>
      <Text>
        When agents end up in the same place, they get a chance to notice
        each other and talk - sometimes just two, sometimes the whole room.
        Most recent first.
      </Text>

      <div className="mt-6 space-y-3">
        {isLoading && <Text className="text-gray-400">Loading...</Text>}

        {!isLoading && timeline.length === 0 && (
          <Card>
            <Text className="text-gray-400">
              No conversations yet. Agents only talk if they end up in the
              same location - put two of them in the same house and step
              the simulation forward.
            </Text>
          </Card>
        )}

        {pageItems.map((conversation, i) => {
          const preview = conversation.dialog[0]?.message || '';
          const subtitle = [conversation.time, preview]
            .filter(Boolean)
            .join(' · ');

          return (
            <CollapsibleSection
              key={currentPage * PAGE_SIZE + i}
              title={conversation.participants.join(' & ')}
              subtitle={subtitle}
            >
              <div className="space-y-2">
                {conversation.participants.length === 2 ? (
                  conversation.dialog.map((line, j) => {
                    const isTalker = line.name === conversation.participants[0];
                    return (
                      <div
                        key={j}
                        className={isTalker ? 'text-left' : 'text-right'}
                      >
                        <div
                          className={
                            'inline-block max-w-[80%] rounded-lg px-3 py-2 text-sm ' +
                            (isTalker
                              ? 'bg-gray-100 text-gray-800'
                              : 'bg-indigo-500 text-white')
                          }
                        >
                          <div
                            className={
                              'text-xs font-medium mb-0.5 ' +
                              (isTalker ? 'text-gray-400' : 'text-indigo-100')
                            }
                          >
                            {line.name}
                          </div>
                          {line.message}
                        </div>
                      </div>
                    );
                  })
                ) : (
                  conversation.dialog.map((line, j) => (
                    <div key={j} className="rounded-lg bg-gray-50 px-3 py-2 text-sm text-gray-800">
                      <div className={'text-xs font-medium mb-0.5 ' + getSpeakerColorClass(line.name)}>
                        {line.name}
                      </div>
                      {line.message}
                    </div>
                  ))
                )}
              </div>
            </CollapsibleSection>
          );
        })}
      </div>

      {timeline.length > PAGE_SIZE && (
        <div className="mt-6 flex items-center justify-between">
          <Button
            icon={ChevronLeftIcon}
            variant="secondary"
            color="gray"
            disabled={currentPage === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </Button>
          <Text className="text-gray-500">
            Page {currentPage + 1} of {pageCount}
          </Text>
          <Button
            iconPosition="right"
            icon={ChevronRightIcon}
            variant="secondary"
            color="gray"
            disabled={currentPage >= pageCount - 1}
            onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
          >
            Next
          </Button>
        </div>
      )}
    </main>
  );
}
