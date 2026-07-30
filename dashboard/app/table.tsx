'use client';

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
import { TrashIcon, Cog6ToothIcon, BookOpenIcon } from '@heroicons/react/24/outline';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import PersonalityEditor from './personality-editor';
import { deleteAgent } from '../lib/smallville';

export interface User {
  name: string;
  location: string;
  action: string;
  emoji: string;
}

export default function UsersTable({ users }: { users: User[] }) {
  const router = useRouter();
  const [editingAgent, setEditingAgent] = useState<string | null>(null);
  const [deletingName, setDeletingName] = useState<string | null>(null);

  async function handleDelete(name: string) {
    if (!confirm(`Delete ${name}? This cannot be undone.`)) return;

    setDeletingName(name);
    await deleteAgent(name);
    setDeletingName(null);
    router.refresh();
  }

  return (
    <>
      <Table>
        <TableHead>
          <TableRow>
            <TableHeaderCell>Name</TableHeaderCell>
            <TableHeaderCell>Location</TableHeaderCell>
            <TableHeaderCell>Activity</TableHeaderCell>
            <TableHeaderCell>Emoji</TableHeaderCell>
            <TableHeaderCell className="text-right"></TableHeaderCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {users.map((user) => (
            <TableRow key={user.name}>
              <TableCell>
                <Link
                  href={`/agents/${encodeURIComponent(user.name)}`}
                  className="font-medium text-indigo-600 hover:text-indigo-800 hover:underline"
                >
                  {user.name}
                </Link>
              </TableCell>
              <TableCell>
                <Text>{user.location}</Text>
              </TableCell>
              <TableCell>
                <Text>{user.action}</Text>
              </TableCell>
              <TableCell>
                <Text>{user.emoji}</Text>
              </TableCell>
              <TableCell className="text-right">
                <div className="flex justify-end gap-2">
                  <Link href={`/agents/${encodeURIComponent(user.name)}`}>
                    <Button size="xs" variant="secondary" color="gray" icon={BookOpenIcon}>
                      Diary
                    </Button>
                  </Link>
                  <Button
                    size="xs"
                    variant="secondary"
                    color="gray"
                    icon={Cog6ToothIcon}
                    onClick={() => setEditingAgent(user.name)}
                  >
                    Personality
                  </Button>
                  <Button
                    size="xs"
                    variant="secondary"
                    color="red"
                    icon={TrashIcon}
                    loading={deletingName === user.name}
                    onClick={() => handleDelete(user.name)}
                  >
                    Delete
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <PersonalityEditor
        agentName={editingAgent}
        onClose={() => setEditingAgent(null)}
      />
    </>
  );
}
