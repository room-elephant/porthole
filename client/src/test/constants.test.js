import {describe,it,expect} from 'vitest';
import {STORAGE_KEYS,ASSETS} from '../constants';
describe('scoped preference keys', () => {
 it('separates sources and encodes names without delimiter collisions', () => {
  expect(STORAGE_KEYS.ENDPOINT_PREF('walker','same')).not.toBe(STORAGE_KEYS.ENDPOINT_PREF('mike','same'));
  expect(STORAGE_KEYS.VERSION_CHECK('a','b:c')).not.toBe(STORAGE_KEYS.VERSION_CHECK('a:b','c'));
  expect(STORAGE_KEYS.ENDPOINT_PREF('local','__proto__')).toBe('endpoint_pref_v2_["local","__proto__"]');
 });
});

describe('assets', () => {
 it('uses the porthole mark for in-app icons', () => {
  expect(ASSETS.PORTHOLE_ICON).toBe('porthole-mark.svg');
 });
});
