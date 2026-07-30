'use client';

import {
  Card,
  Text,
  Title,
  Grid,
  Flex,
} from '@tremor/react';
import LocationsTable, { SmallvilleLocation } from './table';
import CreateLocationForm from './create-location-form';
import { getAllLocations, getInfo } from '../../lib/smallville';
import LocationVisitsChart from './location_visits';
import React, { useCallback, useEffect, useState } from 'react';

export default function LocationsPage(props: any) {
  const [data, setData] = useState({chartData: [], locations: [], analytics: {locationVisits: []}});

  const fetchData = useCallback(async () => {
    const locations: any = await getAllLocations()
    const info: any = await getInfo()

    const chartData = info.prompts.map((prompt: any, index: number) => ({
      "Response Time": Math.abs(prompt.responseTime),
      Month: index,
    }));

    setData({
      analytics: info,
      locations: locations,
      chartData: chartData
    })
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return (
    <main className="p-4 md:p-10 mx-auto max-w-7xl">
      <Flex justifyContent="between" alignItems="center">
        <div>
          <Title>Locations & Objects</Title>
          <Text>View and edit the location states of the simulation world</Text>
        </div>
        <CreateLocationForm locations={data.locations} onCreated={fetchData} />
      </Flex>
      <Grid numItemsSm={1} numItemsLg={2} className="gap-6 mt-6">
        <Card>
          <LocationsTable locations={data.locations}></LocationsTable>
        </Card>
        <Card key={'Visit Frequency'}>
          <LocationVisitsChart data={data.analytics.locationVisits}></LocationVisitsChart>
        </Card>
      </Grid>
    </main>
  );
}
