'use client';

import { useEffect, useState } from 'react';
import { isOnline, onConnectionChange } from '../lib/smallville';

/**
 * Tells you when the backend has gone away.
 *
 * Every call in lib/smallville.ts swallows its error and returns an empty
 * result, so a stopped backend used to render as a town where simply nothing
 * was happening - indistinguishable from a quiet simulation.
 */
export default function ConnectionBanner() {
  const [online, setOnline] = useState(true);

  useEffect(() => {
    setOnline(isOnline());
    return onConnectionChange(setOnline);
  }, []);

  if (online) {
    return null;
  }

  return (
    <div
      role="status"
      className="mb-4 rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800"
    >
      <span className="font-medium">Can&apos;t reach the simulation backend.</span>{' '}
      Anything shown below is the last data received. Check that the server is
      running and reachable.
    </div>
  );
}
