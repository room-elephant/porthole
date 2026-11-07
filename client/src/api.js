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

export const fetchContainers = async ({ showAll, showStopped, signal }) => {
  const { data } = await api.get(API.CONTAINERS, {
    params: { includeWithoutEndpoints: showAll, includeStopped: showStopped },
    timeout: 0,
    signal,
  });
  return data;
};

export const fetchDockerHealth = async ({ signal } = {}) => {
  const { data } = await api.get(API.DOCKER_HEALTH, { signal });
  return data;
};

export const refreshVersions = async () => {
  await api.post(API.VERSION_REFRESH);
};

export const selectContainerEndpoint = async ({ sourceId, containerName, selectedEndpoint }) => {
  await api.put(API.SELECTED_URL, { sourceId, containerName, selectedEndpoint });
};

export const selectContainerIcon = async ({ sourceId, containerName, name }) => {
  await api.put(API.ICON, { sourceId, containerName, name });
};

export const uploadContainerIcon = async ({ sourceId, containerName, file }) => {
  const body = new FormData();
  body.append('sourceId', sourceId);
  body.append('containerName', containerName);
  body.append('file', file);
  await api.post(API.ICON_UPLOAD, body, { timeout: 30000 });
};

export const fetchLinks = async ({ signal }) => {
  const { data } = await api.get(API.LINKS, { signal });
  return data;
};

export const createLink = async (details) => {
  const { data } = await api.post(API.LINKS, details);
  return data;
};

export const updateLink = async (id, details) => {
  await api.put(`${API.LINKS}/${id}`, details);
};

export const deleteLink = async (id) => {
  await api.delete(`${API.LINKS}/${id}`);
};

export const selectLinkIcon = async (id, name) => {
  await api.put(`${API.LINKS}/${id}/icon`, { name });
};

export const uploadLinkIcon = async (id, file) => {
  const body = new FormData();
  body.append('file', file);
  await api.post(`${API.LINKS}/${id}/icon/upload`, body, { timeout: 30000 });
};

export const fetchIconLibrary = async ({ signal }) => {
  const { data } = await api.get(ICON_LIBRARY.METADATA_URL, { signal });
  return data;
};
