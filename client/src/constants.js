export const STORAGE_KEYS = {
  SHOW_ALL: 'porthole_showAll',
  SHOW_STOPPED: 'porthole_showStopped',
  SHOW_SELF: 'porthole_showSelf',
  GROUP_BY: 'porthole_groupBy',
  VERSION_CHECK: entryId => `version_check_v3_${entryId}`,
};

export const GROUP_BY = { HOST_STACK: 'host-stack', HOST: 'host', STACK: 'stack' };

export const API = {
  DASHBOARD: '/api/dashboard',
  ENTRIES: '/api/entries',
  ICONS: '/api/icons',
  EVENTS: '/api/events',
  VERSION_CHECKS: '/api/version-checks',
};

export const EXTERNAL = { ID: '_external', NAME: 'External' };

export const ICON_LIBRARY = {
  METADATA_URL: 'https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/metadata.json',
  WEBP_BASE: 'https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp',
};

export const ASSETS = {
  PORTHOLE_ICON: 'porthole-mark.svg',
  PORTHOLE_FULL: 'porthole-full.webp',
  FALLBACK_ICON: 'https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp/docker.webp',
};
