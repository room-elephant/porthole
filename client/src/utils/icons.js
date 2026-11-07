import { ICON_LIBRARY, UPLOADED_ICON_PATH } from '../constants';

const MAX_ICON_BYTES = 1024 * 1024;
const ACCEPTED_ICON_TYPES = ['image/png', 'image/webp', 'image/jpeg'];

const normalize = value => value.toLowerCase().replace(/[^a-z0-9]/g, '');

export const isUploadedIcon = iconUrl => Boolean(iconUrl?.startsWith(UPLOADED_ICON_PATH));

/** The library name in use, read from the last path segment of the inventory's iconUrl. */
export const currentIconName = (iconUrl) => {
  if (!iconUrl || isUploadedIcon(iconUrl)) return null;
  return iconUrl.split('/').pop().replace(/\.[^.]*$/, '');
};

/** A search starting point: the image name without its registry, path, tag or digest. */
export const imageSearchTerm = (image) => {
  if (!image || image.startsWith('sha256:')) return '';
  const name = image.split('@')[0].split('/').pop().split(':')[0];
  return name === 'Unknown' ? '' : name;
};

export const parseIconLibrary = (metadata) => {
  const names = metadata && typeof metadata === 'object' && !Array.isArray(metadata) ? Object.keys(metadata) : [];
  if (names.length === 0) throw new Error('Icon library metadata is not usable');
  return names.sort().map(name => ({ name, aliases: metadata[name]?.aliases ?? [] }));
};

export const searchIcons = (icons, query) => {
  const term = normalize(query);
  if (!term) return icons;
  return icons.filter(icon => [icon.name, ...icon.aliases].some(value => normalize(value).includes(term)));
};

export const libraryIconUrl = name => `${ICON_LIBRARY.WEBP_BASE}/${name}.webp`;

/** The browser's own check, for quick feedback. The server enforces the real rules. */
export const validateIconFile = (file) => {
  if (!ACCEPTED_ICON_TYPES.includes(file.type)) return `${file.name} isn't supported. Use PNG, WebP or JPEG.`;
  if (file.size > MAX_ICON_BYTES) return `That file is ${(file.size / MAX_ICON_BYTES).toFixed(1)} MB. Choose an image up to 1 MB.`;
  return null;
};
