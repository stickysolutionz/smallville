'use client';

import { ChatBubbleLeftEllipsisIcon, TrashIcon } from '@heroicons/react/24/outline';
import { useEffect, useRef, useState } from 'react';
import { Select, SelectItem, Button, Text } from '@tremor/react';
import { interview } from '../lib/smallville';

export const dynamic = 'force-dynamic';

interface ChatMessage {
  sender: 'user' | 'agent';
  text: string;
}

export default function InterviewInput({
  agents
}: {
  agents: { name: string }[];
}) {
  const [selectedAgent, setSelectedAgent] = useState(agents[0]?.name || '');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isPending, setPending] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const selectedAgentRef = useRef(selectedAgent);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
  }, [messages, isPending]);

  function handleAgentChange(agent: string) {
    selectedAgentRef.current = agent;
    setSelectedAgent(agent);
    setMessages([]);
  }

  function handleClear() {
    setMessages([]);
  }

  async function handleSend() {
    const question = input.trim();
    if (!question || !selectedAgent || isPending) return;

    const agentAtSendTime = selectedAgent;

    setMessages((prev) => [...prev, { sender: 'user', text: question }]);
    setInput('');
    setPending(true);

    const answer = await interview(agentAtSendTime, question);

    // If the user switched agents while this was in flight, drop the stale
    // answer instead of appending it to the wrong conversation.
    if (selectedAgentRef.current === agentAtSendTime) {
      setMessages((prev) => [...prev, { sender: 'agent', text: answer }]);
    }

    setPending(false);
  }

  return (
    <div>
      <div className="flex items-center gap-3 mb-4">
        <div className="w-56">
          <Select
            value={selectedAgent}
            onValueChange={handleAgentChange}
            disabled={isPending}
            enableClear={false}
          >
            {agents.map((agent) => (
              <SelectItem key={agent.name} value={agent.name}>
                {agent.name}
              </SelectItem>
            ))}
          </Select>
        </div>
        <Button
          icon={TrashIcon}
          variant="secondary"
          color="gray"
          disabled={messages.length === 0}
          onClick={handleClear}
        >
          Clear chat
        </Button>
      </div>

      <div
        ref={scrollRef}
        className="h-80 overflow-y-auto rounded-md border border-gray-100 bg-gray-50 p-4 space-y-3"
      >
        {messages.length === 0 && (
          <Text className="text-gray-400">
            {selectedAgent
              ? `Ask ${selectedAgent} something to start the conversation.`
              : 'Create a character first.'}
          </Text>
        )}

        {messages.map((message, i) => (
          <div
            key={i}
            className={message.sender === 'user' ? 'text-right' : 'text-left'}
          >
            <div
              className={
                'inline-block max-w-[80%] rounded-lg px-3 py-2 text-sm ' +
                (message.sender === 'user'
                  ? 'bg-indigo-500 text-white'
                  : 'bg-white border border-gray-200 text-gray-800')
              }
            >
              {message.sender === 'agent' && (
                <div className="text-xs font-medium text-gray-400 mb-0.5">
                  {selectedAgent}
                </div>
              )}
              {message.text}
            </div>
          </div>
        ))}

        {isPending && (
          <Text className="text-gray-400">{selectedAgent} is thinking...</Text>
        )}
      </div>

      <div className="relative mt-3">
        <div
          className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3"
          aria-hidden="true"
        >
          <ChatBubbleLeftEllipsisIcon
            className="h-4 w-4 text-gray-400"
            aria-hidden="true"
          />
        </div>
        <input
          type="text"
          autoComplete="off"
          disabled={isPending || !selectedAgent}
          className="h-10 block w-full rounded-md border border-gray-200 pl-9 pr-4 focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm disabled:opacity-50"
          placeholder={selectedAgent ? `Message ${selectedAgent}` : 'Message...'}
          spellCheck={false}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleSend();
          }}
        />
      </div>
    </div>
  );
}
