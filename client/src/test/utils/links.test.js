import { describe, it, expect } from 'vitest';
import { displayUrl, sortLinks, stackOptions, urlError } from '../../utils/links';

describe('displayUrl', () => {
  it('drops the scheme and trailing slash', () => {
    expect(displayUrl('http://192.168.50.1/')).toBe('192.168.50.1');
    expect(displayUrl('HTTPS://nas.lan:5001/a/')).toBe('nas.lan:5001/a');
  });

  it('keeps paths and queries', () => {
    expect(displayUrl('https://x.lan/a?b=1')).toBe('x.lan/a?b=1');
  });
});

describe('sortLinks', () => {
  it('sorts by name without changing the input', () => {
    const links = [{ name: 'Router' }, { name: 'nas' }, { name: 'Alpha' }];
    expect(sortLinks(links).map(link => link.name)).toEqual(['Alpha', 'nas', 'Router']);
    expect(links[0].name).toBe('Router');
  });
});

describe('stackOptions', () => {
  it('merges container and link stacks, sorted and without duplicates', () => {
    expect(stackOptions([{ project: 'net' }, { project: null }], [{ stack: 'base' }, { stack: 'net' }, { stack: null }]))
      .toEqual(['base', 'net']);
  });
});

describe('urlError', () => {
  it('asks for a scheme when one is missing', () => {
    expect(urlError('192.168.50.1')).toBe('Add http:// or https:// at the start.');
    expect(urlError('localhost:8080')).toBe('Add http:// or https:// at the start.');
    expect(urlError('')).toBe('Add http:// or https:// at the start.');
  });

  it('rejects other schemes and credentials', () => {
    expect(urlError('ftp://x/')).toBe('Enter an http or https address');
    expect(urlError('http://user:pw@x/')).toBe('Enter an http or https address');
  });

  it('accepts http and https', () => {
    expect(urlError('http://r/')).toBeNull();
    expect(urlError('HTTPS://nas.lan:5001/')).toBeNull();
  });
});
