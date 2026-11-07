import { useQuery } from '@tanstack/react-query';
import { fetchIconLibrary } from '../api';
import { parseIconLibrary } from '../utils/icons';

/** The Dashboard Icons catalogue, fetched once per session when the picker first needs it. */
export const useIconLibrary = enabled => useQuery({
  queryKey: ['icon-library'],
  queryFn: async ({ signal }) => parseIconLibrary(await fetchIconLibrary({ signal })),
  enabled,
  retry: false,
  staleTime: Infinity,
  gcTime: Infinity,
});
