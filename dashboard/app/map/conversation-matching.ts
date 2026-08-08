import { ConversationGroup } from '../../lib/smallville';

// Neither the Agent entity nor the conversation DTOs expose a "this is
// currently happening" flag, so we match heuristically: find the most
// recent conversation where every participant is still present at this
// location right now. getAllConversations() returns oldest-first (same
// convention the conversations page reverses), so the last match in the
// array is the most recent one. Requiring every participant still be
// present avoids surfacing a stale conversation for someone who already
// walked off.
export function findActiveConversation(
  presentNames: string[],
  conversations: ConversationGroup[]
): ConversationGroup | null {
  const presentSet = new Set(presentNames);
  let found: ConversationGroup | null = null;

  for (const convo of conversations) {
    if (!convo.dialog.length || convo.participants.length < 2) continue;
    if (convo.participants.every((name) => presentSet.has(name))) {
      found = convo;
    }
  }

  return found;
}
