import { useId } from 'react';
import ContainerTile from './ContainerTile';
import ContainerChip from './ContainerChip';
import { useStorageRevision } from '../hooks/useLocalStorage';
import { isHeadless } from '../utils/endpoints';
import { ASSETS } from '../constants';

const plural = (count, noun) => `${count} ${noun}${count === 1 ? '' : 's'}`;

function emptyMessage(showAll, showStopped) {
  if (!showAll && !showStopped) {
    return 'No running containers with endpoints. Try enabling "Show stopped containers" or "Show containers without a web UI" in settings.';
  }
  if (!showStopped) {
    return 'No running containers found. Try enabling "Show stopped containers" in settings.';
  }
  if (!showAll) {
    return 'No containers with endpoints found. Try enabling "Show containers without a web UI" in settings.';
  }
  return 'Docker is running but no containers exist. Start some containers to see them here.';
}

export function OfflineBanner({ source }) {
  return (
    <div className="banner-offline" role="alert">
      <strong>{`Can't reach ${source.name}`}</strong>
      {source.error && <span>{source.error}</span>}
      <span>Retrying automatically.</span>
    </div>
  );
}

function HostMeta({ section }) {
  const status = section.source.status;
  if (status === 'UP') {
    const stacks = section.groups.filter(group => group.key !== null).length;
    return (
      <span className="section-meta">
        {plural(section.containers.length, 'container')}
        {stacks > 0 && ` · ${plural(stacks, 'stack')}`}
        {' · Connected'}
      </span>
    );
  }
  if (status === 'DOWN' || status === 'ERROR') {
    return (
      <span className="section-meta meta-offline">
        <span className="meta-dot" aria-hidden="true"></span>Unreachable
      </span>
    );
  }
  return <span className="section-meta">Checking…</span>;
}

function Group({ group, section, sourcesById, allowLegacyMigration }) {
  useStorageRevision();
  const withUi = group.containers.filter(container => !isHeadless(container));
  const headless = group.containers.filter(container => isHeadless(container));
  if (group.containers.length === 0) return null;

  const keyOf = container => JSON.stringify([container.sourceId, container.id]);
  const common = container => ({
    container,
    sourceOnline: sourcesById[container.sourceId]?.status === 'UP',
    hostTag: section.kind === 'stack' ? sourcesById[container.sourceId]?.name ?? null : null,
    allowLegacyMigration,
  });

  return (
    <div className="stack-group">
      {group.label && (
        <div className="stack-label">
          <span>{group.label}</span>
          <span className="stack-count">{group.containers.length}</span>
        </div>
      )}
      {withUi.length > 0 && (
        <div className="tile-grid">
          {withUi.map(container => (
            <ContainerTile key={keyOf(container)} sourceName={sourcesById[container.sourceId]?.name} {...common(container)} />
          ))}
        </div>
      )}
      {headless.length > 0 && (
        <div className="chip-row">
          <span className="chip-row-label">No web UI</span>
          {headless.map(container => (
            <ContainerChip key={keyOf(container)} {...common(container)} />
          ))}
        </div>
      )}
    </div>
  );
}

export default function SourceSection({ section, sourcesById, showAll, showStopped, allowLegacyMigration }) {
  const headingId = useId();
  const isHost = section.kind === 'host';
  const source = section.source;
  const offline = isHost && (source.status === 'DOWN' || source.status === 'ERROR');
  const showEmpty = isHost && source.status === 'UP' && section.containers.length === 0;

  return (
    <section className="source-section" aria-labelledby={headingId}>
      <header className="section-header">
        <h2 id={headingId} className="section-title">{section.title}</h2>
        {isHost ? (
          <HostMeta section={section} />
        ) : (
          <span className="section-meta">
            {`${plural(section.containers.length, 'container')} · ${section.hostNames.join(', ')}`}
          </span>
        )}
      </header>
      {offline && <OfflineBanner source={source} />}
      {showEmpty ? (
        <div className="empty-state">
          <img src={ASSETS.PORTHOLE_ICON} alt="" width="40" height="40" />
          <h3>No containers found</h3>
          <p>{emptyMessage(showAll, showStopped)}</p>
        </div>
      ) : (
        section.groups.map(group => (
          <Group
            key={group.key ?? '\0standalone'}
            group={group}
            section={section}
            sourcesById={sourcesById}
            allowLegacyMigration={allowLegacyMigration}
          />
        ))
      )}
    </section>
  );
}
