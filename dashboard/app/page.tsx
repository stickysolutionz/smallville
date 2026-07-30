import { Card, Title, Text, Flex } from '@tremor/react';
import UsersTable from './table';
import InterviewInput from './interview_button';
import SimulationControls, { SimulationStatusBadge } from './simulation-controls';
import CreateAgentForm from './create-agent-form';
import CollapsibleSection from './collapsible-section';
import { getAgents, getAllLocations } from '../lib/smallville';

export const dynamic = 'force-dynamic';

export default async function IndexPage({
  searchParams
}: {
  searchParams: { q: string };
}) {

  const search = searchParams.q ?? '';
  const users = await getAgents();
  const locations = await getAllLocations();

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
        <Flex justifyContent="between" alignItems="center">
          <div>
            <Title>Generative Agents</Title>
            <Text>A list of all the generative agents</Text>
          </div>
          <CreateAgentForm locations={locations} />
        </Flex>
        <Card className="mt-6">
          <UsersTable users={users} />
        </Card>
      </div>

      <CollapsibleSection
        title="Interview Agents"
        subtitle="Take the role of an interviewer and ask a character questions directly"
      >
        <InterviewInput agents={users}></InterviewInput>
      </CollapsibleSection>
    </main>
  );
}
