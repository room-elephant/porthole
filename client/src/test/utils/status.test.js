import { describe, it, expect } from 'vitest';
import { containerStatus, getHealthStatus } from '../../utils/status';
import { createMockContainer } from '../testUtils';

const c = (o) => createMockContainer(o);

describe('getHealthStatus', () => {
  it('extracts the health state from a status string', () => {
    expect(getHealthStatus('Up 1h (healthy)')).toBe('healthy');
    expect(getHealthStatus('Up 1h (Unhealthy)')).toBe('unhealthy');
    expect(getHealthStatus('Up 5s (health: starting)')).toBe('health: starting');
    expect(getHealthStatus('Up 1h')).toBeUndefined();
    expect(getHealthStatus(undefined)).toBeUndefined();
  });
});

describe('containerStatus', () => {
  it.each([
    [{ state: 'running', status: 'Up 1h' }, true, false, { kind: 'ok', dot: 'ok', label: null, openable: true }],
    [{ state: 'running', status: 'Up 1h' }, true, true, { kind: 'update', dot: 'ok', label: 'Update', openable: true }],
    [{ state: 'running', status: 'Up 1h (unhealthy)' }, true, true, { kind: 'unhealthy', dot: 'bad', label: 'Unhealthy', openable: true }],
    [{ state: 'running', status: 'Up 5s (health: starting)' }, true, true, { kind: 'starting', dot: 'warn', label: 'Starting', openable: true }],
    [{ state: 'restarting', status: 'Restarting' }, true, false, { kind: 'restarting', dot: 'warn', label: 'Restarting', openable: true }],
    [{ state: 'restarting', status: 'Restarting (1) 2 seconds ago (unhealthy)' }, true, false, { kind: 'unhealthy', dot: 'bad', label: 'Unhealthy', openable: true }],
    [{ state: 'paused', status: 'Up 1h (Paused)' }, true, false, { kind: 'paused', dot: 'off', label: 'Paused', openable: false }],
    [{ state: 'exited', status: 'Exited (0)' }, true, true, { kind: 'stopped', dot: 'off', label: 'Stopped', openable: false }],
    [{ state: 'running', status: 'Up 1h (unhealthy)' }, false, true, { kind: 'offline', dot: 'off', label: 'Offline', openable: false }],
  ])('derives %o online=%s update=%s', (o, sourceOnline, showUpdate, expected) => {
    expect(containerStatus(c(o), { sourceOnline, showUpdate })).toEqual(expected);
  });
});
