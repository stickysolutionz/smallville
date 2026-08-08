'use client';

import { Card, Title, Text, Button } from '@tremor/react';
import { useCallback, useEffect, useState } from 'react';
import { UsageReport, getUsage, resetUsage } from '../../lib/smallville';

const POLL_MS = 15000;

function formatTokens(value: number) {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}k`;
  return String(value);
}

function formatCost(value: number) {
  // Sub-cent totals are the normal case early in a run, and rounding them to
  // "$0.00" makes the panel look broken.
  if (value > 0 && value < 0.01) return '<$0.01';
  return `$${value.toFixed(2)}`;
}

export default function UsagePanel() {
  const [usage, setUsage] = useState<UsageReport | null>(null);

  const load = useCallback(async () => {
    setUsage(await getUsage());
  }, []);

  useEffect(() => {
    load();
    const poll = setInterval(load, POLL_MS);
    return () => clearInterval(poll);
  }, [load]);

  if (!usage || usage.total.calls === 0) {
    return null;
  }

  const rows = Object.entries(usage.byPrompt).sort(
    (a, b) => b[1].estimatedCostUsd - a[1].estimatedCostUsd
  );

  const { total } = usage;
  const cacheable = total.cacheHitTokens + total.cacheMissTokens;
  const cacheRate =
    cacheable > 0 ? Math.round((total.cacheHitTokens / cacheable) * 100) : 0;

  return (
    <Card className="mt-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <Title>API usage</Title>
          <Text>
            {total.calls} calls &middot; {formatCost(total.estimatedCostUsd)}{' '}
            estimated &middot; {cacheRate}% of input served from cache
          </Text>
        </div>
        <Button
          size="xs"
          variant="secondary"
          onClick={async () => {
            await resetUsage();
            load();
          }}
        >
          Reset
        </Button>
      </div>

      {total.reasoningTokens > 0 && (
        <Text className="mt-2 text-amber-700">
          {formatTokens(total.reasoningTokens)} reasoning tokens, billed as
          output. Set <code>thinking: disabled</code> in config.yaml to try
          turning this off.
        </Text>
      )}

      <div className="mt-4 overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left text-gray-500">
              <th className="py-2 pr-4 font-medium">Prompt</th>
              <th className="py-2 pr-4 text-right font-medium">Calls</th>
              <th className="py-2 pr-4 text-right font-medium">In</th>
              <th className="py-2 pr-4 text-right font-medium">Out</th>
              <th className="py-2 pr-4 text-right font-medium">Thinking</th>
              <th className="py-2 text-right font-medium">Cost</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([name, row]) => (
              <tr key={name} className="border-b last:border-0">
                <td className="py-2 pr-4 font-mono text-xs">{name}</td>
                <td className="py-2 pr-4 text-right">{row.calls}</td>
                <td className="py-2 pr-4 text-right">
                  {formatTokens(row.promptTokens)}
                </td>
                <td className="py-2 pr-4 text-right">
                  {formatTokens(row.completionTokens)}
                </td>
                <td className="py-2 pr-4 text-right">
                  {row.reasoningTokens > 0
                    ? formatTokens(row.reasoningTokens)
                    : '-'}
                </td>
                <td className="py-2 text-right">
                  {formatCost(row.estimatedCostUsd)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Text className="mt-3 text-xs text-gray-500">
        Estimated from the prices in config.yaml. Ignores DeepSeek&apos;s
        peak-hour surcharge, which doubles prices during two windows each day.
      </Text>
    </Card>
  );
}
