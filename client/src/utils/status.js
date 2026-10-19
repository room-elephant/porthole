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
