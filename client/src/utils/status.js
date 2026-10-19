export const getHealthStatus = (status) => status?.match(/\((healthy|unhealthy|health: starting)\)/i)?.[1]?.toLowerCase();

const result = (kind, dot, label, openable = true) => ({ kind, dot, label, openable });

export const containerStatus = (container, { sourceOnline, showUpdate }) => {
  if (!sourceOnline) return result('offline', 'off', 'Offline', false);
  const state = container.state?.toLowerCase();
  if (state === 'paused') return result('paused', 'off', 'Paused', false);
  if (state !== 'running' && state !== 'restarting') return result('stopped', 'off', 'Stopped', false);
  const health = getHealthStatus(container.status);
  if (health === 'unhealthy') return result('unhealthy', 'bad', 'Unhealthy');
  if (state === 'restarting') return result('restarting', 'warn', 'Restarting');
  if (health === 'health: starting') return result('starting', 'warn', 'Starting');
  if (showUpdate) return result('update', 'ok', 'Update');
  return result('ok', 'ok', null);
};

const TONE = { ok: 'ok', update: 'ok', starting: 'warn', restarting: 'warn', unhealthy: 'bad' };
const TITLE = {
  ok: 'Running', update: 'Running', starting: 'Starting', restarting: 'Restarting', unhealthy: 'Unhealthy',
  stopped: 'Stopped', paused: 'Paused', offline: 'Offline',
};
const NOTES = {
  stopped: "The container isn't running, so the tile won't open until it's started.",
  paused: "Processes are frozen, so the tile won't open until it's resumed.",
  offline: "Porthole can't reach this host, so the tile won't open until it's back.",
};

export const statusDetail = (container, status) => {
  const kind = status.kind;
  const raw = container.status ?? '';
  let time = null;
  if (kind === 'ok' || kind === 'update' || kind === 'starting') {
    const m = raw.match(/^Up (.+?)(?: \(.*\))?$/)?.[1];
    time = m ? `Up ${m}` : null;
  } else if (kind === 'stopped') {
    const m = raw.match(/^Exited \(\d+\) (.+)$/)?.[1];
    time = m ? `Since ${m}` : null;
  }
  return {
    tone: TONE[kind] ?? 'off',
    title: TITLE[kind],
    health: (kind === 'ok' || kind === 'update') && getHealthStatus(raw) === 'healthy' ? 'Healthy' : null,
    time,
    note: NOTES[kind] ?? null,
  };
};
