import {
  Table,
  TableHead,
  TableRow,
  TableHeaderCell,
  TableBody,
  TableCell,
  Text,
  Button
} from '@tremor/react';
import { TrashIcon } from '@heroicons/react/24/outline';
import { useState } from 'react';
import { deleteLocation } from '../../lib/smallville';
import LocationImageEditor from './location-image-editor';
export interface SmallvilleLocation {
  name: string;
  state: string | undefined | null;
  hasImage?: boolean;
}

export default async function LocationsTable({
  locations,
  onImageUploaded,
  onDeleted
}: {
  locations: SmallvilleLocation[];
  onImageUploaded?: () => void;
  onDeleted?: () => void;
}) {
  const [editingImageLocation, setEditingImageLocation] = useState<string | null>(null);
  const [deletingName, setDeletingName] = useState<string | null>(null);

  async function handleDelete(name: string) {
    if (!confirm(`Delete ${name}? This cannot be undone.`)) return;

    setDeletingName(name);
    await deleteLocation(name);
    setDeletingName(null);
    onDeleted?.();
  }

  return (
    <>
      <Table>
        <TableHead>
          <TableRow>
            <TableHeaderCell>Name</TableHeaderCell>
            <TableHeaderCell>State</TableHeaderCell>
            <TableHeaderCell className="text-right"></TableHeaderCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {locations.map((location) => (
            <TableRow key={location.name}>
              <TableCell>{location.name}</TableCell>
              <TableCell>
                <Text>{location.state || 'not known'}</Text>
              </TableCell>
              <TableCell className="text-right">
                <Text className="space-x-2">
                  <Button
                    className="mx-0"
                    size="xs"
                    variant="secondary"
                    color="gray"
                    onClick={() => setEditingImageLocation(location.name)}
                  >
                    Photo
                  </Button>
                  <Button
                    className="mx-0"
                    size="xs"
                    variant="secondary"
                    color="red"
                    icon={TrashIcon}
                    loading={deletingName === location.name}
                    onClick={() => handleDelete(location.name)}
                  >
                    Delete
                  </Button>
                </Text>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <LocationImageEditor
        locationName={editingImageLocation}
        hasImage={
          locations.find((l) => l.name === editingImageLocation)?.hasImage ?? false
        }
        onClose={() => setEditingImageLocation(null)}
        onUploaded={() => {
          onImageUploaded?.();
          setEditingImageLocation(null);
        }}
      />
    </>
  );
}
