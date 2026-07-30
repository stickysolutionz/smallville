import { User } from "../app/table";

export async function getAgents() {
  try {
    const response = await fetch('http://localhost:8080/agents', {cache: "no-store"}); // Replace with your actual server URL
    if (!response.ok) {
      throw new Error('Failed to fetch agents data.');
    }

    const agentsData = await response.json();
    const result: User[] = agentsData.agents

    return result
  } catch (error) {
    console.error('Error fetching agents data:');
    return []
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
    const response = await fetch('http://localhost:8080/info', {cache: "no-store"}); // Replace with your actual server URL
    
    if (!response.ok) {
      throw new Error('Failed to fetch agents data.');
    }

    const agentsData = await response.json();
    const result: SmallvilleAnalytics = agentsData

    return result
  } catch (error) {
    console.error('Error fetching agents data');
    return []
  }
}

export async function getAllLocations() {
  try {
    const response = await fetch('http://localhost:8080/locations', {cache: "no-store"}); // Replace with your actual server URL
    
    if (!response.ok) {
      throw new Error('Failed to fetch location data.');
    }

    const result = await response.json();
    console.log("fetching new data")
    return result.locations
  } catch (error) {
    console.error('Error fetching locations data');
    return []
  }
}

export async function interview(agent: string, question: string) {
  try {
    const response = await fetch('http://localhost:8080/agents/' + agent + '/ask',{
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
    console.error('Error interviewing agent');
    return []
  }
}

export async function deleteAgent(name: string) {
  try {
    const response = await fetch('http://localhost:8080/agents/' + name, {
      method: 'DELETE',
      cache: 'no-store'
    });

    return await response.json();
  } catch (error) {
    console.error('Error deleting agent');
    return { success: false };
  }
}

export interface Characteristic {
  index: number;
  description: string;
}

export async function getCharacteristics(name: string): Promise<Characteristic[]> {
  try {
    const response = await fetch(
      'http://localhost:8080/agents/' + name + '/characteristics',
      { cache: 'no-store' }
    );

    if (!response.ok) {
      throw new Error('Failed to fetch characteristics.');
    }

    const result = await response.json();
    return result.characteristics;
  } catch (error) {
    console.error('Error fetching characteristics');
    return [];
  }
}

export async function addCharacteristic(name: string, description: string) {
  try {
    const response = await fetch(
      'http://localhost:8080/agents/' + name + '/characteristics',
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
    console.error('Error adding characteristic');
    return { success: false };
  }
}

export async function removeCharacteristic(name: string, index: number) {
  try {
    const response = await fetch(
      'http://localhost:8080/agents/' + name + '/characteristics/' + index,
      { method: 'DELETE', cache: 'no-store' }
    );

    return await response.json();
  } catch (error) {
    console.error('Error removing characteristic');
    return { success: false };
  }
}

export async function createLocation(name: string) {
  try {
    const response = await fetch('http://localhost:8080/locations', {
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
    console.error('Error creating location');
    return { success: false };
  }
}

export interface ConversationLine {
  name: string;
  message: string;
}

export interface ConversationGroup {
  talker: string;
  talkee: string;
  time: string | null;
  dialog: ConversationLine[];
}

export async function getAllConversations(): Promise<ConversationGroup[]> {
  try {
    const response = await fetch('http://localhost:8080/conversations', {
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Failed to fetch conversations.');
    }

    const result = await response.json();
    return result.conversations;
  } catch (error) {
    console.error('Error fetching conversations');
    return [];
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
    const response = await fetch(
      'http://localhost:8080/agents/' + name + '/diary',
      { cache: 'no-store' }
    );

    if (!response.ok) {
      throw new Error('Failed to fetch diary.');
    }

    const result = await response.json();
    return result.diary;
  } catch (error) {
    console.error('Error fetching diary');
    return [];
  }
}

export interface GeneratedCharacter {
  name: string;
  memories: string[];
}

export async function generateCharacter(): Promise<GeneratedCharacter | null> {
  try {
    const response = await fetch('http://localhost:8080/agents/generate', {
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
    console.error('Error generating character');
    return null;
  }
}

export async function createAgent(
  name: string,
  memories: string[],
  location: string,
  activity: string
) {
  try {
    const response = await fetch('http://localhost:8080/agents', {
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
    console.error('Error creating agent');
    return { success: false };
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
    agentCount: 0
  };

  try {
    const response = await fetch('http://localhost:8080/simulation/status', {
      cache: 'no-store'
    });

    if (!response.ok) {
      throw new Error('Failed to fetch simulation status.');
    }

    return await response.json();
  } catch (error) {
    console.error('Error fetching simulation status');
    return fallback;
  }
}

export async function startSimulation(intervalSeconds: number) {
  try {
    const response = await fetch('http://localhost:8080/simulation/start', {
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
    console.error('Error starting simulation');
    return { success: false };
  }
}

export async function stopSimulation() {
  try {
    const response = await fetch('http://localhost:8080/simulation/stop', {
      method: 'POST',
      cache: 'no-store'
    });

    return await response.json();
  } catch (error) {
    console.error('Error stopping simulation');
    return { success: false };
  }
}

export async function updateLocation(name: string, state: string) {
  try {
    const response = await fetch('http://localhost:8080/locations/' + name,{
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
    console.error('Error updating location');
    return []
  }
}