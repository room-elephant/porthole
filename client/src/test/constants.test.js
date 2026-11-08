import {describe,it,expect} from 'vitest';
import {STORAGE_KEYS,ASSETS} from '../constants';
describe('scoped preference keys', () => {
 it('keys update preferences by entry ID', () => {
  expect(STORAGE_KEYS.VERSION_CHECK('container:a:b')).toBe('version_check_v3_container:a:b');
  expect(STORAGE_KEYS.VERSION_CHECK('container:a:b')).not.toBe(STORAGE_KEYS.VERSION_CHECK('container:a:c'));
 });
});

describe('showSelf key', () => {
 it('uses the porthole_showSelf storage key', () => {
  expect(STORAGE_KEYS.SHOW_SELF).toBe('porthole_showSelf');
 });
});

describe('groupBy key', () => {
 it('uses the porthole_groupBy storage key', () => {
  expect(STORAGE_KEYS.GROUP_BY).toBe('porthole_groupBy');
 });
});

describe('assets', () => {
 it('uses the porthole mark for in-app icons', () => {
  expect(ASSETS.PORTHOLE_ICON).toBe('porthole-mark.svg');
  expect(ASSETS.PORTHOLE_FULL).toBe('porthole-full.webp');
 });
});
