import { useState, useMemo, useCallback } from 'react';
import Header from './components/Header';
import NoResults from './components/NoResults';
import SkeletonTile from './components/SkeletonTile';
import SourceSection, { EmptyState, OfflineBanner } from './components/SourceSection';
import AppSettings from './components/AppSettings';
import LinkSettings from './components/LinkSettings';
import { useContainers } from './hooks/useContainers';
import { useLinks } from './hooks/useLinks';
import { useLastKnownContainers } from './hooks/useLastKnownContainers';
import { useLocalStorage } from './hooks/useLocalStorage';
import { EXTERNAL, STORAGE_KEYS, GROUP_BY } from './constants';
import { buildSections } from './utils/containers';
import { stackOptions } from './utils/links';

const EMPTY = { sources: [], containers: [] };
const NO_LINKS = [];
const LINK_ICON_FAILED = "Link added, but its icon couldn't be saved.";

const isOffline = source => source.status === 'DOWN' || source.status === 'ERROR';

function App() {
  const [showSettings, setShowSettings] = useState(false);
  const [query, setQuery] = useState('');
  const [selectedHost, setSelectedHost] = useState('all');
  // The dialog lives here, not in the tile, because saving can move the tile to another group.
  const [editingLink, setEditingLink] = useState(null);
  const [showAll, setShowAll] = useLocalStorage(STORAGE_KEYS.SHOW_ALL, false);
  const [showStopped, setShowStopped] = useLocalStorage(STORAGE_KEYS.SHOW_STOPPED, false);
  const [showSelf, setShowSelf] = useLocalStorage(STORAGE_KEYS.SHOW_SELF, true);
  const [storedGroupBy, setGroupBy] = useLocalStorage(STORAGE_KEYS.GROUP_BY, GROUP_BY.HOST_STACK);
  const groupBy = Object.values(GROUP_BY).includes(storedGroupBy) ? storedGroupBy : GROUP_BY.HOST_STACK;
  const { data: fetchedInventory, isLoading, error } = useContainers({ showAll, showStopped });
  const { data: fetchedLinks, isError: linksFailed } = useLinks();
  const links = fetchedLinks ?? NO_LINKS;
  const data = fetchedInventory ?? EMPTY;
  const initialError = error && !fetchedInventory;
  const lastKnown = useLastKnownContainers(data);
  const containers = useMemo(() => (showSelf ? lastKnown : lastKnown.filter(container => !container.self)), [lastKnown, showSelf]);
  const filterExists = data.sources.some(source => source.id === selectedHost) || (selectedHost === EXTERNAL.ID && links.length > 0);
  const hostFilter = filterExists ? selectedHost : 'all';
  const counts = useMemo(() => {
    const result = Object.fromEntries(data.sources.map(source => [source.id, 0]));
    containers.forEach(container => { result[container.sourceId] = (result[container.sourceId] ?? 0) + 1; });
    return result;
  }, [data.sources, containers]);
  const sections = useMemo(
    () => buildSections({ sources: data.sources, containers, links, groupBy, hostFilter, query }),
    [data.sources, containers, links, groupBy, hostFilter, query]);
  const stacks = useMemo(() => stackOptions(lastKnown, links), [lastKnown, links]);
  const sourcesById = useMemo(() => Object.fromEntries(data.sources.map(source => [source.id, source])), [data.sources]);

  const linkBeingEdited = links.find(link => link.id === editingLink?.id);
  const openLinkSettings = useCallback(id => setEditingLink({ id, alert: null }), []);
  // Deleted elsewhere. Links aren't known yet while the first request is running.
  if (editingLink && fetchedLinks && !linkBeingEdited) setEditingLink(null);

  const sectionList = sections.map(section => (
    <SourceSection
      key={section.id}
      section={section}
      sourcesById={sourcesById}
      showAll={showAll}
      showStopped={showStopped}
      onOpenLinkSettings={openLinkSettings}
    />
  ));

  let inventory;
  if (isLoading) {
    inventory = (
      <>
        <div className="tile-grid">
          {Array.from({ length: 6 }, (_, index) => <SkeletonTile key={index} />)}
        </div>
        <output className="loading-status">Connecting…</output>
      </>
    );
  } else if (initialError) {
    inventory = (
      <div className="error-message" role="alert">
        Failed to fetch containers. Ensure the server is running.
      </div>
    );
    inventory = <>{inventory}{sectionList}</>;
  } else if (query.trim() !== '' && sections.every(section => section.containers.length + section.links.length === 0)) {
    inventory = (
      <NoResults
        query={query}
        showStopped={showStopped}
        onClear={() => setQuery('')}
        onIncludeStopped={() => setShowStopped(true)}
      />
    );
  } else if (sections.length === 0) {
    inventory = <EmptyState showAll={showAll} showStopped={showStopped} />;
  } else {
    const banners = groupBy === GROUP_BY.STACK
      ? data.sources
        .filter(source => (hostFilter === 'all' || source.id === hostFilter) && isOffline(source))
        .map(source => <OfflineBanner key={source.id} source={source} />)
      : [];
    inventory = (
      <>
        {banners}
        {sectionList}
      </>
    );
  }

  return (
    <div>
      <Header
        query={query}
        onQueryChange={setQuery}
        sources={initialError ? [] : data.sources}
        counts={counts}
        linkCount={links.length}
        hostFilter={hostFilter}
        onHostFilterChange={setSelectedHost}
        onOpenSettings={() => setShowSettings(true)}
      />
      <main className="page">
        {inventory}
      </main>
      {showSettings && (
        <AppSettings
          showStopped={showStopped}
          showAll={showAll}
          onToggleShowStopped={setShowStopped}
          onToggleShowAll={setShowAll}
          showSelf={showSelf}
          onToggleShowSelf={setShowSelf}
          groupBy={groupBy}
          onGroupByChange={setGroupBy}
          counts={counts}
          onClose={() => setShowSettings(false)}
          sources={initialError ? [] : data.sources}
          linkCount={links.length}
          linksUnavailable={linksFailed && !fetchedLinks}
          stacks={stacks}
          onLinkIconFailed={(id) => { setShowSettings(false); setEditingLink({ id, alert: LINK_ICON_FAILED }); }}
        />
      )}
      {linkBeingEdited && (
        <LinkSettings
          key={linkBeingEdited.id}
          link={linkBeingEdited}
          stacks={stacks}
          initialAlert={editingLink.alert}
          onClose={() => setEditingLink(null)}
          onDeleted={() => setEditingLink(null)}
        />
      )}
    </div>
  );
}

export default App;
