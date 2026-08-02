import { User } from "../app/table";

/**
 * Base URL of the Java backend.
 *
 * Was hardcoded as a literal at two dozen call sites, which meant the
 * dashboard could only ever talk to a backend on localhost:8080. Set
 * NEXT_PUBLIC_API_URL to point it somewhere else.
 */
export const API_URL =
  process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

/**
 * Whether the last request to the backend succeeded.
 *
 * Every function here swallows its error and returns an empty result, so a
 * backend that is down renders as a town where nothing is happening rather
 * than as an error. Callers can subscribe to this to tell the difference.
 */
type ConnectionListener = (online: boolean) => void;

const listeners = new Set<ConnectionListener>();
let online = true;

export function onConnectionChange(listener: ConnectionListener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function isOnline() {
  return online;
}

function setOnline(next: boolean) {
  if (next === online) return;
  online = next;
  listeners.forEach((listener) => listener(next));
}

/**
 * fetch, with the outcome recorded.
 *
 * A thrown fetch means the backend is unreachable; an HTTP error status means
 * it answered, so only the former counts as being offline.
 */
async function trackedFetch(input: string, init?: RequestInit): Promise<Response> {
  try {
    // globalThis.fetch, not bare fetch: every other call in this file was
    // rewritten from `fetch(` to `trackedFetch(` by a find and replace that
    // also caught this line, turning the wrapper into infinite recursion.
    // Qualifying it makes that substitution impossible to repeat.
    const response = await globalThis.fetch(input, init);
    setOnline(true);
    return response;
  } catch (error) {
    setOnline(false);
    throw error;
  }
}

/** Marks the connection down and returns the caller's fallback. */
function failed<T>(context: string, error: unknown, fallback: T): T {
  setOnline(false);
  console.error(`Error ${context}:`, error);
  return fallback;
}

export async function getAgents() {
  try {
    const response = await trackedFetch(API_URL + '/agents', {cache: "no-store"}); // Replace with your actual server URL
    if (!response.ok) {
      throw new Error('Failed to fetch agents data.');
    }

    const agentsData = await response.json();
    const result: User[] = agentsData.agents

    return result
  } catch (error) {
    return failed('fetching agents data', error, []);
  }
}

export interface SmallvilleAnalytics {
  step: Number;
  time: String;
  locationVisits: any;
  prompts: any[]
}

export async function getInfo() {
  try {
    const response = await trackedFetch(API_URL + '/info', {cache: "no-store"}); // Replace with your actual server URL
    
    if (!response.ok) {
      throw new Error('Failed to fetch agents data.');
    }

    const agentsData = await response.json();
    const result: SmallvilleAnalytics = agentsData

    return result
  } catch (error) {
    return failed('fetching agents data', error, []);
  }
}

export async function getAllLocations() {
  try {
    const response = await trackedFetch(API_URL + '/locations', {cache: "no-store"}); // Replace with your actual server URL
    
    if (!response.ok) {
      throw new Error('Failed to fetch location data.');
    }

    const result = await response.json();
    console.log("fetching new data")
    return result.locations
  } catch (error) {
    return failed('fetching locations data', error, []);
  }
}

export async function interview(agent: string, question: string) {
  try {
    const response = await trackedFetch(API_URL + '/agents/' + agent + '/ask',{
      method: 'POST',
      headers: {
        Accept: 'application.json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        question: question
      }),
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Failed to interivew agent.');
    }

    const responseJson = await response.json();
    const result = responseJson.answer

    return result
  } catch (error) {
    return failed('interviewing agent', error, []);
  }
}

export async function deleteAgent(name: string) {
  try {
    const response = await trackedFetch(API_URL + '/agents/' + name, {
      method: 'DELETE',
      cache: 'no-store'
    });

    return await response.json();
  } catch (error) {
    return failed('deleting agent', error, { success: false });
  }
}

export async function deleteLocation(name: string) {
  try {
    const response = await trackedFetch(
      API_URL + '/locations/' + encodeURIComponent(name),
      {
        method: 'DELETE',
        cache: 'no-store'
      }
    );

    return await response.json();
  } catch (error) {
    return failed('deleting location', error, { success: false });
  }
}

export interface Characteristic {
  index: number;
  description: string;
}

export async function getCharacteristics(name: string): Promise<Characteristic[]> {
  try {
    const response = await trackedFetch(
      API_URL + '/agents/' + name + '/characteristics',
      { cache: 'no-store' }
    );

    if (!response.ok) {
      throw new Error('Failed to fetch characteristics.');
    }

    const result = await response.json();
    return result.characteristics;
  } catch (error) {
    return failed('fetching characteristics', error, []);
  }
}

export async function addCharacteristic(name: string, description: string) {
  try {
    const response = await trackedFetch(
      API_URL + '/agents/' + name + '/characteristics',
      {
        method: 'POST',
        headers: {
          Accept: 'application.json',
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ description }),
        cache: 'no-store'
      }
    );

    return await response.json();
  } catch (error) {
    return failed('adding characteristic', error, { success: false });
  }
}

export async function removeCharacteristic(name: string, index: number) {
  try {
    const response = await trackedFetch(
      API_URL + '/agents/' + name + '/characteristics/' + index,
      { method: 'DELETE', cache: 'no-store' }
    );

    return await response.json();
  } catch (error) {
    return failed('removing characteristic', error, { success: false });
  }
}

export async function createLocation(name: string) {
  try {
    const response = await trackedFetch(API_URL + '/locations', {
      method: 'POST',
      headers: {
        Accept: 'application.json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ name }),
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Failed to create location.');
    }

    return await response.json();
  } catch (error) {
    return failed('creating location', error, { success: false });
  }
}

export interface ConversationLine {
  name: string;
  message: string;
}

export interface ConversationGroup {
  participants: string[];
  time: string | null;
  dialog: ConversationLine[];
}

export async function getAllConversations(): Promise<ConversationGroup[]> {
  try {
    const response = await trackedFetch(API_URL + '/conversations', {
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Failed to fetch conversations.');
    }

    const result = await response.json();
    return result.conversations;
  } catch (error) {
    return failed('fetching conversations', error, []);
  }
}

export interface DiaryEntry {
  description: string;
  time: string | null;
  type: 'Observation' | 'Plan' | 'Reflection' | 'Characteristic' | string;
  importance: number;
}

export async function getDiary(name: string): Promise<DiaryEntry[]> {
  try {
    const response = await trackedFetch(
      API_URL + '/agents/' + name + '/diary',
      { cache: 'no-store' }
    );

    if (!response.ok) {
      throw new Error('Failed to fetch diary.');
    }

    const result = await response.json();
    return result.diary;
  } catch (error) {
    return failed('fetching diary', error, []);
  }
}

export interface GeneratedCharacter {
  name: string;
  memories: string[];
}

export async function generateCharacter(): Promise<GeneratedCharacter | null> {
  try {
    const response = await trackedFetch(API_URL + '/agents/generate', {
      method: 'POST',
      headers: {
        Accept: 'application.json',
        'Content-Type': 'application/json'
      },
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Failed to generate character.');
    }

    return await response.json();
  } catch (error) {
    return failed('generating character', error, null);
  }
}

export async function createAgent(
  name: string,
  memories: string[],
  location: string,
  activity: string
) {
  try {
    const response = await trackedFetch(API_URL + '/agents', {
      method: 'POST',
      headers: {
        Accept: 'application.json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ name, memories, location, activity }),
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Failed to create agent.');
    }

    return await response.json();
  } catch (error) {
    return failed('creating agent', error, { success: false });
  }
}

export interface SimulationStatus {
  running: boolean;
  intervalSeconds: number;
  tickCount: number;
  lastError: string | null;
  time: string;
  date: string;
  agentCount: number;
}

export async function getSimulationStatus(): Promise<SimulationStatus> {
  const fallback: SimulationStatus = {
    running: false,
    intervalSeconds: 15,
    tickCount: 0,
    lastError: null,
    time: '',
    date: '',
    agentCount: 0
  };

  try {
    const response = await trackedFetch(API_URL + '/simulation/status', {
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Failed to fetch simulation status.');
    }

    return await response.json();
  } catch (error) {
    return failed('fetching simulation status', error, fallback);
  }
}

export async function startSimulation(intervalSeconds: number) {
  try {
    const response = await trackedFetch(API_URL + '/simulation/start', {
      method: 'POST',
      headers: {
        Accept: 'application.json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ intervalSeconds }),
      cache: 'no-store'
    });

    return await response.json();
  } catch (error) {
    return failed('starting simulation', error, { success: false });
  }
}

export async function stopSimulation() {
  try {
    const response = await trackedFetch(API_URL + '/simulation/stop', {
      method: 'POST',
      cache: 'no-store'
    });

    return await response.json();
  } catch (error) {
    return failed('stopping simulation', error, { success: false });
  }
}

export async function setTimestep(minutes: number) {
  try {
    const response = await trackedFetch(API_URL + '/timestep', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ numOfMinutes: String(minutes) }),
      cache: 'no-store'
    });

    return await response.json();
  } catch (error) {
    return failed('setting timestep', error, { success: false });
  }
}

export async function resetSimulation() {
  try {
    const response = await trackedFetch(API_URL + '/simulation/reset', {
      method: 'POST',
      cache: 'no-store'
    });

    return await response.json();
  } catch (error) {
    return failed('resetting simulation', error, { success: false });
  }
}

export async function updateLocation(name: string, state: string) {
  try {
    const response = await trackedFetch(API_URL + '/locations/' + name,{
      method: 'POST',
      headers: {
        Accept: 'application.json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        state: state
      }),
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Failed to update location state.');
    }

    const responseJson = await response.json();
    const result = responseJson.answer

    return result
  } catch (error) {
    return failed('updating location', error, []);
  }
}

export interface StoryState {
  story: string;
  exists: boolean;
  updated?: boolean;
  asOfDate: string | null;
  asOfTime: string | null;
  minutesSinceUpdate?: number;
  message?: string | null;
}

export async function getStory(): Promise<StoryState> {
  try {
    const response = await trackedFetch(API_URL + '/story', {
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Failed to fetch story.');
    }

    return await response.json();
  } catch (error) {
    return failed('fetching story', error, { story: '', exists: false, asOfDate: null, asOfTime: null });
  }
}

export async function generateStory(): Promise<StoryState | null> {
  try {
    const response = await trackedFetch(API_URL + '/story/generate', {
      method: 'POST',
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Failed to generate story.');
    }

    return await response.json();
  } catch (error) {
    return failed('generating story', error, null);
  }
}

export function getLocationImageUrl(name: string): string {
  return `${API_URL}/locations/${encodeURIComponent(name)}/image`;
}

export async function uploadLocationImage(
  name: string,
  file: File
): Promise<{ success: boolean; message?: string }> {
  try {
    const formData = new FormData();
    formData.append('image', file);

    // No Content-Type header here on purpose - the browser needs to set
    // its own multipart boundary, unlike every other function in this
    // file which sends JSON.
    const response = await trackedFetch(
      `${API_URL}/locations/${encodeURIComponent(name)}/image`,
      {
        method: 'POST',
        body: formData,
        cache: 'no-store'
      }
    );

    return await response.json();
  } catch (error) {
    return failed('uploading location image', error, { success: false, message: 'Upload failed' });
  }
}
export interface PromptUsage {
  calls: number;
  promptTokens: number;
  completionTokens: number;
  reasoningTokens: number;
  cacheHitTokens: number;
  cacheMissTokens: number;
  estimatedCostUsd: number;
}

export interface UsageReport {
  byPrompt: Record<string, PromptUsage>;
  total: PromptUsage;
}

export async function getUsage(): Promise<UsageReport | null> {
  try {
    const response = await trackedFetch(API_URL + '/usage', { cache: 'no-store' });

    if (!response.ok) {
      throw new Error('Failed to fetch usage.');
    }

    return await response.json();
  } catch (error) {
    return failed('fetching usage', error, null);
  }
}

export async function resetUsage() {
  try {
    const response = await trackedFetch(API_URL + '/usage/reset', {
      method: 'POST',
      cache: 'no-store'
    });

    return await response.json();
  } catch (error) {
    return failed('resetting usage', error, { success: false });
  }
}
