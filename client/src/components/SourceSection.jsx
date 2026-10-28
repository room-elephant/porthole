import { useId, useState, useRef, useCallback, useEffect } from 'react';
import { useIsMutating, useQueryClient } from '@tanstack/react-query';
import ContainerTile from './ContainerTile';
import ContainerChip from './ContainerChip';
import { isHeadless } from '../utils/endpoints';
import { plural } from '../utils/containers';
import { ASSETS } from '../constants';


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

const DOCS_URL = 'https://github.com/room-elephant/porthole/blob/main/docs/CONFIGURATION.md';

export function EmptyState({ showAll, showStopped, hostName }) {
  const nothingHidden = showAll && showStopped;
  return (
    <div className="empty-state">
      <img src={ASSETS.PORTHOLE_FULL} alt="" width="112" height="112" />
      <h3>{hostName ? `Nothing running on ${hostName} yet` : 'No containers found'}</h3>
      <p>
        {nothingHidden && hostName
          ? 'Start a container and it shows up here automatically, with its Traefik URL or published port.'
          : emptyMessage(showAll, showStopped)}
      </p>
      <a href={DOCS_URL} target="_blank" rel="noopener">Add more Docker hosts <span aria-hidden="true">↗</span></a>
    </div>
  );
}

export function OfflineBanner({ source }) {
  return (
    <div className="banner-offline" role="alert">
      <p><strong>{`Can't reach ${source.name}`}</strong></p>
      {source.error && <p>{source.error}</p>}
      <p>Retrying automatically.</p>
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

const keyOf = container => JSON.stringify([container.sourceId, container.id]);

function Group({ group, section, sourcesById }) {
  const groupRef = useRef(null);
  const queryClient = useQueryClient();
  // A failed or same-partition save must also release a closed dialog's focus handoff.
  useIsMutating({ mutationKey: ['selected-endpoint'] });
  // While a settings dialog is open its card keeps its tile/chip slot, so the dialog
  // (owned by the card) is not unmounted when the selection changes its partition.
  const frozenRef = useRef(null);
  const [frozen, setFrozen] = useState(null);
  const focusKeyRef = useRef(null);

  const handleSettingsOpenChange = useCallback((container, open) => {
    if (open) {
      focusKeyRef.current = null;
      frozenRef.current = { key: keyOf(container), headless: isHeadless(container) };
      setFrozen(frozenRef.current);
      return;
    }
    const held = frozenRef.current;
    frozenRef.current = null;
    setFrozen(null);
    if (!groupRef.current) return;
    const mutationKey = ['selected-endpoint', container.sourceId, container.name];
    const pending = queryClient.isMutating({ mutationKey, exact: true }) > 0;
    if (held && (pending || isHeadless(container) !== held.headless)) {
      focusKeyRef.current = {
        key: held.key,
        originalHeadless: held.headless,
        container,
        mutationKey,
        pending,
        slot: [...groupRef.current.querySelectorAll('[data-container-key]')].find(el => el.dataset.containerKey === held.key),
      };
    }
  }, [queryClient]);

  useEffect(() => {
    const relinquishFocus = event => {
      const target = focusKeyRef.current;
      // The modal may restore focus to the old card. Any later move elsewhere cancels the handoff.
      if (target?.pending && !target.slot?.contains(event.target)) focusKeyRef.current = null;
    };
    document.addEventListener('focusin', relinquishFocus);
    return () => document.removeEventListener('focusin', relinquishFocus);
  }, []);

  // After the card has moved, put focus on its settings control (the dialog restored it to a stale node).
  useEffect(() => {
    const target = focusKeyRef.current;
    if (target === null || !groupRef.current) return;
    const confirmed = queryClient.getQueriesData({ queryKey: ['containers'] })
      .flatMap(([, inventory]) => inventory?.containers ?? [])
      .find(candidate => candidate.sourceId === target.container.sourceId && candidate.name === target.container.name);
    const headless = isHeadless(confirmed ?? target.container);
    if (headless === target.originalHeadless) {
      if (!queryClient.isMutating({ mutationKey: target.mutationKey, exact: true })) focusKeyRef.current = null;
      return;
    }
    const slot = [...groupRef.current.querySelectorAll('[data-container-key]')].find(el => el.dataset.containerKey === target.key);
    // Wait if the cache confirmation has not reached this group's inventory props yet.
    if (slot && Boolean(slot.querySelector('.chip')) !== headless) return;
    focusKeyRef.current = null;
    slot?.querySelector('.chip, .tile-menu')?.focus();
  });

  const headlessOf = container => (frozen?.key === keyOf(container) ? frozen.headless : isHeadless(container));
  const withUi = group.containers.filter(container => !headlessOf(container));
  const headless = group.containers.filter(headlessOf);
  if (group.containers.length === 0) return null;

  const common = container => ({
    container,
    sourceOnline: sourcesById[container.sourceId]?.status === 'UP',
    hostTag: section.kind === 'stack' ? sourcesById[container.sourceId]?.name ?? null : null,
    onSettingsOpenChange: handleSettingsOpenChange,
  });

  return (
    <div className="stack-group" ref={groupRef}>
      {group.label && (
        <div className="stack-label">
          <span>{group.label}</span>
          <span className="stack-count">{group.containers.length}</span>
        </div>
      )}
      {withUi.length > 0 && (
        <div className="tile-grid">
          {withUi.map(container => (
            <span key={keyOf(container)} className="card-slot" data-container-key={keyOf(container)}>
              <ContainerTile sourceName={sourcesById[container.sourceId]?.name} {...common(container)} />
            </span>
          ))}
        </div>
      )}
      {headless.length > 0 && (
        <div className="chip-row">
          <span className="chip-row-label">No web UI</span>
          {headless.map(container => (
            <span key={keyOf(container)} className="card-slot" data-container-key={keyOf(container)}>
              <ContainerChip sourceName={sourcesById[container.sourceId]?.name} {...common(container)} />
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

export default function SourceSection({ section, sourcesById, showAll, showStopped }) {
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
        <EmptyState showAll={showAll} showStopped={showStopped} hostName={source?.name ?? section.title} />
      ) : (
        section.groups.map(group => (
          <Group
            key={group.key ?? '\0standalone'}
            group={group}
            section={section}
            sourcesById={sourcesById}
          />
        ))
      )}
    </section>
  );
}
