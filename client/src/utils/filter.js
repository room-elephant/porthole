import { EXTERNAL } from '../constants';

export const matchesFilter = (container, query, sourceName) => {
  const needle = query.trim().toLowerCase();
  if (!needle) return true;
  const haystack = [
    container.displayName,
    container.name,
    container.image,
    sourceName,
    ...(container.endpoints ?? []).map(endpoint => endpoint.url),
  ];
  return haystack.some(value => value?.toLowerCase().includes(needle));
};

export const matchesLinkFilter = (link, query) => {
  const needle = query.trim().toLowerCase();
  if (!needle) return true;
  return [link.name, link.url, EXTERNAL.NAME].some(value => value.toLowerCase().includes(needle));
};
