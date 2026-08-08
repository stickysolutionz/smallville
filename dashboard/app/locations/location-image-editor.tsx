'use client';

import { Button } from '@tremor/react';
import { useState } from 'react';
import QuickModal from '../modal';
import { uploadLocationImage } from '../../lib/smallville';
import LocationImage from '../map/location-image';

const MAX_BYTES = 5_000_000;

export default function LocationImageEditor({
  locationName,
  hasImage,
  onClose,
  onUploaded
}: {
  locationName: string | null;
  hasImage: boolean;
  onClose: () => void;
  onUploaded: () => void;
}) {
  const [isUploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file || !locationName) return;

    if (file.size > MAX_BYTES) {
      setError('Image must be under 5MB.');
      return;
    }

    setError(null);
    setUploading(true);
    const result = await uploadLocationImage(locationName, file);
    setUploading(false);

    if (result.success) {
      onUploaded();
    } else {
      setError(result.message || 'Upload failed.');
    }
  }

  return (
    <QuickModal
      setIsOpen={() => onClose()}
      isOpen={locationName !== null}
      title={locationName ? `${locationName}'s Photo` : ''}
    >
      <div className="text-left mt-2 space-y-3">
        {locationName && (
          <LocationImage
            name={locationName}
            hasImage={hasImage}
            className="h-40 w-full rounded-md"
          />
        )}

        <input
          type="file"
          accept="image/png,image/jpeg,image/webp"
          disabled={isUploading}
          onChange={handleFileChange}
          className="block w-full text-sm text-gray-500 file:mr-3 file:rounded-md file:border-0 file:bg-indigo-50 file:px-3 file:py-2 file:text-sm file:font-medium file:text-indigo-700 hover:file:bg-indigo-100"
        />

        {isUploading && <p className="text-sm text-gray-400">Uploading...</p>}
        {error && <p className="text-sm text-red-500">{error}</p>}

        <div className="flex justify-end">
          <Button variant="secondary" color="gray" onClick={() => onClose()}>
            Done
          </Button>
        </div>
      </div>
    </QuickModal>
  );
}
