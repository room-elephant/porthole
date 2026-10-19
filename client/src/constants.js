export const STORAGE_KEYS = {
  SHOW_ALL: 'porthole_showAll',
  SHOW_STOPPED: 'porthole_showStopped',
  GROUP_BY: 'porthole_groupBy',
  ENDPOINT_PREF: (sourceId, name) => `endpoint_pref_v2_${JSON.stringify([sourceId, name])}`,
  VERSION_CHECK: (sourceId, name) => `version_check_v2_${JSON.stringify([sourceId, name])}`,
};

export const GROUP_BY = { HOST_STACK: 'host-stack', HOST: 'host', STACK: 'stack' };

export const API = {
  CONTAINERS: '/api/containers',
  VERSION_REFRESH: '/api/versions/refresh',
  DOCKER_HEALTH: '/actuator/health/docker',
};

export const ASSETS = {
  PORTHOLE_ICON: 'porthole-mark.svg',
  FALLBACK_ICON: 'https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp/docker.webp',
};
