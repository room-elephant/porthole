import { useQuery } from '@tanstack/react-query';
import { fetchDashboard } from '../api';
import { DASHBOARD_KEY, splitDashboard } from '../utils/dashboard';

/** The one read: sources, containers and links, split into the views components use. */
export const useDashboard = () => useQuery({
  queryKey: DASHBOARD_KEY,
  queryFn: ({ signal }) => fetchDashboard({ signal }),
  select: splitDashboard,
  retry: false,
  staleTime: 30 * 1000,
  refetchInterval: 30 * 1000,
});
