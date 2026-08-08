'use client';

import { useEffect, useState } from 'react';
import { ConversationGroup } from '../../lib/smallville';
import { User } from '../table';
import AgentInfoCard from './agent-info-card';
import {
  AvatarPreference,
  getPreference,
  resolveAvatar,
  setPreference
} from './avatar-preferences';
import ConversationPopup from './conversation-popup';
import { findActiveConversation } from './conversation-matching';
import LocationCard from './location-card';
import { resolveMarkerKey } from './location-key';
import { useAgentProfessions } from './use-agent-professions';

export default function TownMap({
  agents,
  locations,
  conversations
}: {
  agents: User[];
  locations: { name: string; state?: string | null; hasImage?: boolean }[];
  conversations: ConversationGroup[];
}) {
  const [selectedAgent, setSelectedAgent] = useState<User | null>(null);
  const [selectedConversation, setSelectedConversation] = useState<ConversationGroup | null>(null);
  const [preferences, setPreferences] = useState<Record<string, AvatarPreference>>({});
  const professions = useAgentProfessions(agents.map((agent) => agent.name));

  useEffect(() => {
    setPreferences((prev) => {
      const next = { ...prev };
      let changed = false;
      for (const agent of agents) {
        if (!next[agent.name]) {
          next[agent.name] = getPreference(agent.name);
          changed = true;
        }
      }
      return changed ? next : prev;
    });
  }, [agents]);

  function handleChangePreference(name: string, preference: AvatarPreference) {
    setPreference(name, preference);
    setPreferences((prev) => ({ ...prev, [name]: preference }));
  }

  function resolveAgentAvatar(agent: User) {
    const preference = preferences[agent.name] ?? getPreference(agent.name);
    return resolveAvatar(preference, professions[agent.name] ?? null);
  }

  const topLevelNames = Array.from(
    new Set(locations.map((loc) => resolveMarkerKey(loc.name)))
  ).filter(Boolean);

  const hasImageByName = new Map(
    locations.map((loc) => [resolveMarkerKey(loc.name), loc.hasImage ?? false])
  );

  // Group agents by resolved location name.
  const agentsByLocation = new Map<string, User[]>();
  for (const agent of agents) {
    const key = resolveMarkerKey(agent.location);
    const list = agentsByLocation.get(key) ?? [];
    list.push(agent);
    agentsByLocation.set(key, list);
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {topLevelNames.map((name) => {
        const here = agentsByLocation.get(name) ?? [];

        const activeConversation =
          here.length >= 2
            ? findActiveConversation(here.map((agent) => agent.name), conversations)
            : null;

        return (
          <LocationCard
            key={name}
            name={name}
            hasImage={hasImageByName.get(name) ?? false}
            agents={here}
            resolveAgentAvatar={resolveAgentAvatar}
            activeConversation={activeConversation}
            onSelectAgent={setSelectedAgent}
            onOpenConversation={setSelectedConversation}
          />
        );
      })}

      <AgentInfoCard
        agent={selectedAgent}
        preference={selectedAgent ? preferences[selectedAgent.name] ?? getPreference(selectedAgent.name) : null}
        profession={selectedAgent ? professions[selectedAgent.name] ?? null : null}
        onChangePreference={(preference) => selectedAgent && handleChangePreference(selectedAgent.name, preference)}
        onClose={() => setSelectedAgent(null)}
      />

      <ConversationPopup
        conversation={selectedConversation}
        onClose={() => setSelectedConversation(null)}
      />
    </div>
  );
}
