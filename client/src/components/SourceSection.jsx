import { Package, AlertTriangle } from 'lucide-react';
import ContainerTile from './ContainerTile';
import { groupByProject } from '../utils/containers';

const STATUS_LABELS = { UP: 'Connected', DOWN: 'Unavailable', ERROR: 'Error' };

function emptyMessage(showAll, showStopped) {
  if (!showAll && !showStopped) {
    return 'No running containers with endpoints. Try enabling "Show stopped containers" or "Show containers without endpoints" in settings.';
  }
  if (!showStopped) {
    return 'No running containers found. Try enabling "Show stopped containers" in settings.';
  }
  if (!showAll) {
    return 'No containers with endpoints found. Try enabling "Show containers without endpoints" in settings.';
  }
  return 'Docker is running but no containers exist. Start some containers to see them here.';
}

function ContainerGrid({ containers, allowLegacyMigration }) {
  return (
    <div className="container-grid">
      {containers.map(container => (
        <ContainerTile
          key={JSON.stringify([container.sourceId, container.id])}
          container={container}
          allowLegacyMigration={allowLegacyMigration}
        />
      ))}
    </div>
  );
}

function SourceInventory({ source, containers, showAll, showStopped, allowLegacyMigration }) {
  if (source.status !== 'UP') {
    return (
      <div className="empty-state warning" role="alert">
        <AlertTriangle size={32} />
        <h3>{source.status === 'DOWN' ? 'Docker unavailable' : 'Could not load containers'}</h3>
        <p>{source.error}</p>
        <p>Retrying automatically.</p>
      </div>
    );
  }
  if (containers.length === 0) {
    return (
      <div className="empty-state">
        <Package size={40} strokeWidth={1} />
        <h3>No containers found</h3>
        <p>{emptyMessage(showAll, showStopped)}</p>
      </div>
    );
  }

  const groups = groupByProject(containers);
  const projects = [...groups.keys()]
    .filter(key => key !== null)
    .sort((left, right) => left.localeCompare(right));
  return (
    <>
      {groups.has(null) && (
        <ContainerGrid containers={groups.get(null)} allowLegacyMigration={allowLegacyMigration} />
      )}
      {projects.map(project => (
        <div key={project} className="project-section">
          <h3 className="project-title">{project}</h3>
          <ContainerGrid containers={groups.get(project)} allowLegacyMigration={allowLegacyMigration} />
        </div>
      ))}
    </>
  );
}

export default function SourceSection({ source, containers, showAll, showStopped, allowLegacyMigration }) {
  const connected = source.status === 'UP';
  return (
    <section className="source-section" aria-labelledby={`source-${source.id}`}>
      <header className="source-header">
        <h2 id={`source-${source.id}`} className="source-title">{source.name}</h2>
        <span className="source-status">
          <span className={`status-indicator ${connected ? 'status-running' : 'status-stopped'}`} />
          {STATUS_LABELS[source.status]}
          {connected && (
            <span className="source-count">
              {containers.length} {containers.length === 1 ? 'container' : 'containers'}
            </span>
          )}
        </span>
      </header>
      <SourceInventory
        source={source}
        containers={containers}
        showAll={showAll}
        showStopped={showStopped}
        allowLegacyMigration={allowLegacyMigration}
      />
    </section>
  );
}
