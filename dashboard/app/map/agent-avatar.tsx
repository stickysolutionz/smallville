'use client';

import { motion } from 'framer-motion';
import { User } from '../table';
import { ResolvedAvatar } from './avatar-preferences';
import PersonSilhouette from './person-silhouette';

export default function AgentAvatar({
  agent,
  avatar,
  onClick
}: {
  agent: User;
  avatar: ResolvedAvatar;
  onClick?: () => void;
}) {
  return (
    <motion.div
      layoutId={agent.name}
      layout
      title={`${agent.name} — ${agent.action}`}
      onClick={onClick}
      className={
        'flex h-8 w-8 flex-shrink-0 cursor-pointer items-center justify-center rounded-full text-lg shadow hover:ring-2 hover:ring-indigo-300 ' +
        (avatar.kind === 'silhouette' ? avatar.palette.bgClass : 'bg-white')
      }
      transition={{ duration: 0.8, ease: 'easeInOut' }}
    >
      {avatar.kind === 'profession' ? (
        avatar.emoji
      ) : (
        <PersonSilhouette gender={avatar.gender} className={'h-5 w-5 ' + avatar.palette.textClass} />
      )}
    </motion.div>
  );
}
