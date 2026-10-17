import { useQuery } from '@tanstack/react-query';
import { fetchContainers } from '../api';

export const useContainers = ({ showAll, showStopped }) => {
  return useQuery({
    queryKey: ['containers', { showAll, showStopped }],
    queryFn: ({ signal }) => fetchContainers({ showAll, showStopped, signal }),
    retry: false,
    staleTime: 30 * 1000,
    refetchInterval: 30 * 1000,
  });
};
