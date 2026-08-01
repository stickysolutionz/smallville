'use client';

import QuickModal from '../modal';
import { ConversationGroup } from '../../lib/smallville';
import { getSpeakerColorClass } from './avatar-preferences';

// Same bubble concept as the full Conversations page, shrunk down and
// opened on click instead of auto-cycling - reading pace is up to you,
// not a timer.
export default function ConversationPopup({
  conversation,
  onClose
}: {
  conversation: ConversationGroup | null;
  onClose: () => void;
}) {
  return (
    <QuickModal
      isOpen={conversation !== null}
      setIsOpen={() => onClose()}
      title={conversation ? conversation.participants.join(' & ') : ''}
    >
      {conversation && (
        <div className="mt-2 max-h-80 space-y-2 overflow-y-auto pr-1 text-left">
          {conversation.participants.length === 2 ? (
            conversation.dialog.map((line, i) => {
              const isTalker = line.name === conversation.participants[0];
              return (
                <div key={i} className={isTalker ? 'text-left' : 'text-right'}>
                  <div
                    className={
                      'inline-block max-w-[85%] rounded-lg px-3 py-2 text-sm ' +
                      (isTalker ? 'bg-gray-100 text-gray-800' : 'bg-indigo-500 text-white')
                    }
                  >
                    <div
                      className={
                        'text-xs font-medium mb-0.5 ' +
                        (isTalker ? 'text-gray-400' : 'text-indigo-100')
                      }
                    >
                      {line.name}
                    </div>
                    {line.message}
                  </div>
                </div>
              );
            })
          ) : (
            <div className="space-y-2">
              {conversation.dialog.map((line, i) => (
                <div key={i} className="rounded-lg bg-gray-50 px-3 py-2 text-sm text-gray-800">
                  <div className={'text-xs font-medium mb-0.5 ' + getSpeakerColorClass(line.name)}>
                    {line.name}
                  </div>
                  {line.message}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </QuickModal>
  );
}
