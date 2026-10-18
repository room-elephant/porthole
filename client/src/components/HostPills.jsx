export default function HostPills({ sources, counts, selected, onSelect }) {
  if (sources.length === 0) return null;
  const total = sources.reduce((sum, source) => sum + (counts[source.id] ?? 0), 0);
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
    </fieldset>
  );
}
