import { describe, it, expect } from 'vitest';
import { STORAGE_KEYS, API, ASSETS } from '../constants';

describe('constants', () => {
  describe('STORAGE_KEYS', () => {
    it('should have correct static keys', () => {
      expect(STORAGE_KEYS.SHOW_ALL).toBe('porthole_showAll');
      expect(STORAGE_KEYS.SHOW_STOPPED).toBe('porthole_showStopped');
    });

    it('should generate endpoint preference keys', () => {
      expect(STORAGE_KEYS.ENDPOINT_PREF('nginx')).toBe('endpoint_pref_nginx');
    });

    it('should generate correct port preference key', () => {
      expect(STORAGE_KEYS.PORT_PREF('nginx')).toBe('port_pref_nginx');
      expect(STORAGE_KEYS.PORT_PREF('my-container')).toBe('port_pref_my-container');
    });

    it('should generate correct version check key', () => {
      expect(STORAGE_KEYS.VERSION_CHECK('nginx')).toBe('version_check_nginx');
      expect(STORAGE_KEYS.VERSION_CHECK('my-container')).toBe('version_check_my-container');
    });
  });

  describe('API', () => {
    it('should have correct static endpoints', () => {
      expect(API.CONTAINERS).toBe('/api/containers');
      expect(API.DOCKER_HEALTH).toBe('/actuator/health/docker');
    });


  });

  describe('ASSETS', () => {
    it('should have correct asset paths', () => {
      expect(ASSETS.PORTHOLE_ICON).toBe('porthole.webp');
      expect(ASSETS.FALLBACK_ICON).toBe('https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp/docker.webp');
    });
  });
});

