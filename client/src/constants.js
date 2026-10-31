export const STORAGE_KEYS = {
  SHOW_ALL: 'porthole_showAll',
  SHOW_STOPPED: 'porthole_showStopped',
  SHOW_SELF: 'porthole_showSelf',
  GROUP_BY: 'porthole_groupBy',
  VERSION_CHECK: (sourceId, name) => `version_check_v2_${JSON.stringify([sourceId, name])}`,
};

export const GROUP_BY = { HOST_STACK: 'host-stack', HOST: 'host', STACK: 'stack' };

export const API = {
  CONTAINERS: '/api/containers',
  SELECTED_URL: '/api/containers/selected-url',
  VERSION_REFRESH: '/api/versions/refresh',
  DOCKER_HEALTH: '/actuator/health/docker',
};

export const ASSETS = {
  PORTHOLE_ICON: 'porthole-mark.svg',
  PORTHOLE_FULL: 'porthole-full.webp',
  FALLBACK_ICON: 'https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp/docker.webp',
};
