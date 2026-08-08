'use client';

import { PhotoIcon } from '@heroicons/react/24/outline';
import { useState } from 'react';
import { getLocationImageUrl } from '../../lib/smallville';

export default function LocationImage({
  name,
  hasImage,
  className
}: {
  name: string;
  hasImage: boolean;
  className?: string;
}) {
  const [errored, setErrored] = useState(false);

  if (!hasImage || errored) {
    return (
      <div
        className={
          'flex flex-col items-center justify-center gap-1 border-2 border-dashed border-gray-200 bg-gray-50 text-gray-400 ' +
          (className ?? '')
        }
      >
        <PhotoIcon className="h-8 w-8" />
        <span className="text-xs font-medium">{name}</span>
      </div>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={getLocationImageUrl(name)}
      alt={name}
      onError={() => setErrored(true)}
      className={'object-cover ' + (className ?? '')}
    />
  );
}
