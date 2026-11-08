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
  LINKS: '/api/links',
  SELECTED_URL: '/api/containers/selected-url',
  ICON: '/api/containers/icon',
  ICON_UPLOAD: '/api/containers/icon/upload',
  VERSION_REFRESH: '/api/versions/refresh',
};

export const EXTERNAL = { ID: '_external', NAME: 'External' };

export const UPLOADED_ICON_PATH = '/api/icons/';

export const ICON_LIBRARY = {
  METADATA_URL: 'https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/metadata.json',
  WEBP_BASE: 'https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp',
};

export const ASSETS = {
  PORTHOLE_ICON: 'porthole-mark.svg',
  PORTHOLE_FULL: 'porthole-full.webp',
  FALLBACK_ICON: 'https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp/docker.webp',
};
