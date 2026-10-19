import { describe, it, expect } from 'vitest';
import { containerStatus, getHealthStatus, statusDetail } from '../../utils/status';
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

describe('statusDetail', () => {
  const detail = (o, kind) => statusDetail(c(o), { kind });
  it.each([
    [{ state: 'running', status: 'Up 3 days (healthy)' }, 'ok', { tone: 'ok', title: 'Running', health: 'Healthy', time: 'Up 3 days', note: null }],
    [{ state: 'running', status: 'Up 3 days' }, 'update', { tone: 'ok', title: 'Running', health: null, time: 'Up 3 days', note: null }],
    [{ state: 'running', status: 'Up Less than a second' }, 'ok', { tone: 'ok', title: 'Running', health: null, time: 'Up Less than a second', note: null }],
    [{ state: 'running', status: 'Up 5 seconds (health: starting)' }, 'starting', { tone: 'warn', title: 'Starting', health: null, time: 'Up 5 seconds', note: null }],
    [{ state: 'restarting', status: 'Restarting (1) 2 seconds ago' }, 'restarting', { tone: 'warn', title: 'Restarting', health: null, time: null, note: null }],
    [{ state: 'running', status: 'Up 1h (unhealthy)' }, 'unhealthy', { tone: 'bad', title: 'Unhealthy', health: null, time: null, note: null }],
    [{ state: 'exited', status: 'Exited (0) 2 hours ago' }, 'stopped', { tone: 'off', title: 'Stopped', health: null, time: 'Since 2 hours ago', note: "The container isn't running, so the tile won't open until it's started." }],
    [{ state: 'created', status: 'Created' }, 'stopped', { tone: 'off', title: 'Stopped', health: null, time: null, note: "The container isn't running, so the tile won't open until it's started." }],
    [{ state: 'paused', status: 'Up 1h (Paused)' }, 'paused', { tone: 'off', title: 'Paused', health: null, time: null, note: "Processes are frozen, so the tile won't open until it's resumed." }],
    [{ state: 'running', status: 'Up 1h' }, 'offline', { tone: 'off', title: 'Offline', health: null, time: null, note: "Porthole can't reach this host, so the tile won't open until it's back." }],
  ])('maps %o (%s)', (o, kind, expected) => {
    expect(detail(o, kind)).toEqual(expected);
  });
});
