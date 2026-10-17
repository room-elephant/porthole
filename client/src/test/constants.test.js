import {describe,it,expect} from 'vitest';
import {STORAGE_KEYS} from '../constants';
describe('scoped preference keys', () => {
 it('separates sources and encodes names without delimiter collisions', () => {
  expect(STORAGE_KEYS.ENDPOINT_PREF('walker','same')).not.toBe(STORAGE_KEYS.ENDPOINT_PREF('mike','same'));
  expect(STORAGE_KEYS.VERSION_CHECK('a','b:c')).not.toBe(STORAGE_KEYS.VERSION_CHECK('a:b','c'));
  expect(STORAGE_KEYS.ENDPOINT_PREF('local','__proto__')).toBe('endpoint_pref_v2_["local","__proto__"]');
 });
});
