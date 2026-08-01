'use client';

import { Title, Text } from '@tremor/react';
import { useEffect, useState } from 'react';
import {
  ConversationGroup,
  getAgents,
  getAllConversations,
  getAllLocations
} from '../../lib/smallville';
import { User } from '../table';
import CollapsibleSection from '../collapsible-section';
import SimulationControls, { SimulationStatusBadge } from '../simulation-controls';
import StoryPanel from './story-panel';
import TownMap from './town-map';

export default function MapPage() {
  const [agents, setAgents] = useState<User[]>([]);
  const [locations, setLocations] = useState<
    { name: string; state?: string | null; hasImage?: boolean }[]
  >([]);
  const [conversations, setConversations] = useState<ConversationGroup[]>([]);
  const [isLoading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      const [agentsResult, locationsResult, conversationsResult] = await Promise.all([
        getAgents(),
        getAllLocations(),
        getAllConversations()
      ]);
      if (!cancelled) {
        setAgents(agentsResult);
        setLocations(locationsResult);
        setConversations(conversationsResult);
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

  return (
    <main className="p-4 md:p-10 mx-auto max-w-7xl space-y-6">
      <CollapsibleSection
        title="Simulation"
        subtitle="Start/pause and adjust tick speed"
        headerRight={<SimulationStatusBadge />}
      >
        <SimulationControls />
      </CollapsibleSection>

      <div>
        <Title>Town Map</Title>
        <Text>Where everyone actually is, at a glance.</Text>
      </div>

      {isLoading && <Text className="text-gray-400">Loading...</Text>}

      {!isLoading && (
        <TownMap agents={agents} locations={locations} conversations={conversations} />
      )}

      <StoryPanel />
    </main>
  );
}
