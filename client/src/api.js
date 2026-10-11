import axios from 'axios';
import { API } from './constants';

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
    signal,
  });
  return data;
};

export const fetchContainerVersion = async ({ containerId, signal }) => {
  const { data } = await api.get(API.CONTAINER_VERSION(containerId), { signal });
  return data;
};

export const fetchDockerHealth = async ({ signal } = {}) => {
  const { data } = await api.get(API.DOCKER_HEALTH, { signal });
  return data;
};
