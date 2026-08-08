'use client';

import { ConversationGroup } from '../../lib/smallville';
import { User } from '../table';
import AgentAvatar from './agent-avatar';
import { ResolvedAvatar } from './avatar-preferences';
import LocationImage from './location-image';

export default function LocationCard({
  name,
  hasImage,
  agents,
  resolveAgentAvatar,
  activeConversation,
  onSelectAgent,
  onOpenConversation
}: {
  name: string;
  hasImage: boolean;
  agents: User[];
  resolveAgentAvatar: (agent: User) => ResolvedAvatar;
  activeConversation: ConversationGroup | null;
  onSelectAgent: (agent: User) => void;
  onOpenConversation: (conversation: ConversationGroup) => void;
}) {
  const pairNames = new Set(activeConversation?.participants ?? []);
  const soloAgents = agents.filter((agent) => !pairNames.has(agent.name));
  const pairedAgents = agents.filter((agent) => pairNames.has(agent.name));

  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
      <LocationImage name={name} hasImage={hasImage} className="aspect-[16/10] w-full" />

      <div className="p-3">
        <div className="flex items-center justify-between">
          <div className="font-medium text-gray-800">{name}</div>
          <div className="text-xs text-gray-400">
            {agents.length} {agents.length === 1 ? 'person' : 'people'} here
          </div>
        </div>

        <div className="mt-3 flex min-h-[2rem] items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            {agents.length === 0 && (
              <div className="text-xs text-gray-300">Nobody here right now</div>
            )}
            {soloAgents.map((agent) => (
              <AgentAvatar
                key={agent.name}
                agent={agent}
                avatar={resolveAgentAvatar(agent)}
                onClick={() => onSelectAgent(agent)}
              />
            ))}
          </div>

          {pairedAgents.length > 0 && activeConversation && (
            <div
              className="flex flex-shrink-0 items-center gap-1 rounded-full bg-indigo-50 py-1 pl-1 pr-2"
              title="Tap to read their conversation"
            >
              <div className="flex -space-x-2">
                {pairedAgents.map((agent) => (
                  <AgentAvatar
                    key={agent.name}
                    agent={agent}
                    avatar={resolveAgentAvatar(agent)}
                    onClick={() => onOpenConversation(activeConversation)}
                  />
                ))}
              </div>
              <span className="text-xs">💬</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
