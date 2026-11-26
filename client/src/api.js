import axios from 'axios';
import { API, ICON_LIBRARY } from './constants';

const api = axios.create({
  timeout: 10000,
});

api.interceptors.response.use(
  response => response,
  error => {
    if (error.code !== 'ERR_CANCELED') {
      console.error('API Error:', error.message);
    }
    return Promise.reject(error);
  }
);

/** Every source and every entry, unfiltered; the browser decides what to show. */
export const fetchDashboard = async ({ signal }) => {
  const { data } = await api.get(API.DASHBOARD, { timeout: 0, signal });
  return data;
};

export const refreshVersions = async () => {
  await api.post(API.VERSION_REFRESH);
};

const MERGE_PATCH = 'application/merge-patch+json';
const entryUrl = id => `${API.ENTRIES}/${encodeURIComponent(id)}`;

/** Changes an entry with a JSON Merge Patch: a missing field is kept and null removes the override. */
export const patchEntry = async (id, patch) => {
  await api.patch(entryUrl(id), patch, { headers: { 'Content-Type': MERGE_PATCH } });
};

/** The PATCH value for an endpoint choice the settings dialog made; null restores automatic selection. */
export const toEndpointPatch = (selection) => {
  if (selection == null) return null;
  if (selection.id === 'none') return 'none';
  if (selection.source === 'custom') return { url: selection.url };
  return { id: selection.id };
};

export const selectContainerEndpoint = ({ entryId, selectedEndpoint }) =>
  patchEntry(entryId, { endpoint: toEndpointPatch(selectedEndpoint) });

/** Uploads the image as the raw body and returns its ID. */
export const uploadIcon = async (file) => {
  const { data } = await api.post(API.ICONS, file, {
    headers: { 'Content-Type': file.type || 'application/octet-stream' },
    timeout: 30000,
  });
  return data.id;
};

export const selectEntryIcon = ({ entryId, name }) => patchEntry(entryId, { icon: { library: name } });

export const uploadEntryIcon = async ({ entryId, file }) => {
  const upload = await uploadIcon(file);
  await patchEntry(entryId, { icon: { upload } });
};

/** Adds a link and returns it as the dashboard shows it. */
export const createLink = async (details) => {
  const { data } = await api.post(API.ENTRIES, details);
  return data;
};

export const updateLink = (id, { name, url, stack }) => patchEntry(id, { name, endpoint: { url }, stack });

export const deleteEntry = async (id) => {
  await api.delete(entryUrl(id));
};

export const fetchIconLibrary = async ({ signal }) => {
  const { data } = await api.get(ICON_LIBRARY.METADATA_URL, { signal });
  return data;
};
