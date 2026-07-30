'use client';

import { Text, Button, Badge, Flex } from '@tremor/react';
import { PlayIcon, PauseIcon } from '@heroicons/react/24/outline';
import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  getSimulationStatus,
  startSimulation,
  stopSimulation,
  SimulationStatus
} from '../lib/smallville';

const REFRESH_MS = 4000;

// Polls independently of <SimulationControls> so it keeps working (and stays
// visible) whether the collapsible section it's placed next to is open or not.
export function SimulationStatusBadge() {
  const [running, setRunning] = useState<boolean | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      const s = await getSimulationStatus();
      if (!cancelled) {
        setRunning(s.running);
      }
    }

    poll();
    const interval = setInterval(poll, REFRESH_MS);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  if (running === null) {
    return null;
  }

  return (
    <Badge color={running ? 'emerald' : 'gray'}>
      {running ? 'Running' : 'Paused'}
    </Badge>
  );
}

// Small always-visible in-world clock, meant for the navbar so it shows up
// on every page regardless of which collapsible section is open.
export function SimulationClock() {
  const [status, setStatus] = useState<SimulationStatus | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      const s = await getSimulationStatus();
      if (!cancelled) {
        setStatus(s);
      }
    }

    poll();
    const interval = setInterval(poll, REFRESH_MS);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  if (!status?.date && !status?.time) {
    return null;
  }

  return (
    <Text className="text-gray-500 whitespace-nowrap">
      {status.date} · {status.time}
    </Text>
  );
}

export default function SimulationControls() {
  const router = useRouter();
  const [status, setStatus] = useState<SimulationStatus | null>(null);
  const [intervalInput, setIntervalInput] = useState('15');
  const [isPending, setPending] = useState(false);
  const initialized = useRef(false);

  async function refreshStatus() {
    const s = await getSimulationStatus();
    setStatus(s);
    if (!initialized.current) {
      setIntervalInput(String(s.intervalSeconds));
      initialized.current = true;
    }
  }

  useEffect(() => {
    refreshStatus();

    const poll = setInterval(async () => {
      await refreshStatus();
    }, REFRESH_MS);

    return () => clearInterval(poll);
  }, []);

  // Separately refresh the rest of the page (agent table etc.) while running,
  // so agent activity/emoji/location updates show up without a manual reload.
  useEffect(() => {
    if (!status?.running) return;

    const refresh = setInterval(() => {
      router.refresh();
    }, REFRESH_MS);

    return () => clearInterval(refresh);
  }, [status?.running, router]);

  async function handleToggle() {
    setPending(true);

    if (status?.running) {
      await stopSimulation();
    } else {
      const seconds = Math.max(2, Number(intervalInput) || 15);
      await startSimulation(seconds);
    }

    await refreshStatus();
    router.refresh();
    setPending(false);
  }

  const seconds = Math.max(2, Number(intervalInput) || 15);
  const ticksPerMinute = (60 / seconds).toFixed(1);

  return (
    <div>
      <Text>
        Controls whether the world advances on its own. Each tick can
        trigger several LLM calls per agent.
      </Text>

      <Flex className="mt-6 gap-4" justifyContent="start" alignItems="end">
        <Button
          icon={status?.running ? PauseIcon : PlayIcon}
          color={status?.running ? 'red' : 'emerald'}
          loading={isPending}
          onClick={handleToggle}
        >
          {status?.running ? 'Pause' : 'Start'}
        </Button>

        <div>
          <Text>Seconds between ticks</Text>
          <input
            type="number"
            min={2}
            value={intervalInput}
            disabled={isPending}
            className="h-10 w-28 rounded-md border border-gray-200 px-3 text-sm shadow-sm focus:border-indigo-500 focus:ring-indigo-500 disabled:opacity-50"
            onChange={(e) => setIntervalInput(e.target.value)}
            onBlur={async () => {
              if (status?.running) {
                setPending(true);
                await startSimulation(seconds);
                await refreshStatus();
                setPending(false);
              }
            }}
          />
        </div>

        <Text>≈ {ticksPerMinute} ticks/min</Text>
      </Flex>

      <Text className="mt-4">
        Simulated time: {status?.date ? `${status.date}, ` : ''}
        {status?.time || '—'} · Ticks so far: {status?.tickCount ?? 0} ·
        Agents: {status?.agentCount ?? 0}
      </Text>

      {status?.lastError && (
        <Text color="red" className="mt-2">
          Last tick error: {status.lastError}
        </Text>
      )}
    </div>
  );
}
