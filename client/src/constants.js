export const STORAGE_KEYS = {
  SHOW_ALL: 'porthole_showAll',
  SHOW_STOPPED: 'porthole_showStopped',
  ENDPOINT_PREF: (sourceId, name) => `endpoint_pref_v2_${JSON.stringify([sourceId, name])}`,
  PORT_PREF: (name) => `port_pref_${name}`,
  VERSION_CHECK: (sourceId, name) => `version_check_v2_${JSON.stringify([sourceId, name])}`,
};

export const API = {
  CONTAINERS: '/api/containers',
  DOCKER_HEALTH: '/actuator/health/docker',
};

export const ASSETS = {
  PORTHOLE_ICON: 'porthole.webp',
  FALLBACK_ICON: 'https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp/docker.webp',
};
