import { useQuery } from '@tanstack/react-query';
import { fetchDashboard } from '../api';
import { DASHBOARD_KEY, splitDashboard } from '../utils/dashboard';
import { useDashboardEvents } from './useDashboardEvents';

/** Change events drive refreshes; this poll only covers a stream that is down. */
export const SAFETY_POLL = 5 * 60 * 1000;

/** The one read: sources, containers and links, split into the views components use, kept current by events. */
export const useDashboard = () => {
  useDashboardEvents();
  return useQuery({
    queryKey: DASHBOARD_KEY,
    queryFn: ({ signal }) => fetchDashboard({ signal }),
    select: splitDashboard,
    retry: false,
    staleTime: 30 * 1000,
    refetchInterval: SAFETY_POLL,
  });
};
