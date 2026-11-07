import { Link as LinkIcon } from 'lucide-react';
import { EXTERNAL } from '../constants';

export default function HostPills({ sources, counts, linkCount = 0, selected, onSelect }) {
  if (sources.length === 0 && linkCount === 0) return null;
  const total = sources.reduce((sum, source) => sum + (counts[source.id] ?? 0), linkCount);
  return (
    <fieldset className="host-pills" aria-label="Show host">
      <button type="button" className="pill" aria-pressed={selected === 'all'} onClick={() => onSelect('all')}>
        All <span className="pill-count">{total}</span>
      </button>
      {sources.map(source => {
        const online = source.status === 'UP';
        return (
          <button
            key={source.id}
            type="button"
            className={online ? 'pill' : 'pill pill--offline'}
            aria-pressed={selected === source.id}
            onClick={() => onSelect(source.id)}
          >
            <span className="pill-dot" aria-hidden="true"></span>
            {source.name}{' '}
            <span className="pill-count">{online ? (counts[source.id] ?? 0) : 'offline'}</span>
          </button>
        );
      })}
      {linkCount > 0 && (
        <button type="button" className="pill" aria-pressed={selected === EXTERNAL.ID} onClick={() => onSelect(EXTERNAL.ID)}>
          <LinkIcon size={12} strokeWidth={2.4} className="pill-link" aria-hidden="true" />
          {EXTERNAL.NAME}{' '}
          <span className="pill-count">{linkCount}</span>
        </button>
      )}
    </fieldset>
  );
}
