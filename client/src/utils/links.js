import { isValidCustomUrl } from './endpoints';

/** A link's URL as its tile shows it, without the scheme or a trailing slash. */
export const displayUrl = url => url.replace(/^https?:\/\//i, '').replace(/\/$/, '');

export const sortLinks = links => links.toSorted((a, b) => a.name.localeCompare(b.name));

/** Stacks a link can join: every stack in the current containers and links. */
export const stackOptions = (containers, links) => {
  const stacks = new Set([...containers.map(container => container.project), ...links.map(link => link.stack)]);
  stacks.delete(null);
  stacks.delete(undefined);
  return [...stacks].sort((a, b) => a.localeCompare(b));
};

/** The message for an unusable URL, or null. The server enforces the real rule. */
export const urlError = (value) => {
  if (isValidCustomUrl(value)) return null;
  return /^[a-z][a-z0-9+.-]*:\/\//i.test(value) ? 'Enter an http or https address' : 'Add http:// or https:// at the start.';
};
