import { useState, useMemo, useCallback } from 'react';
import Header from './components/Header';
import NoResults from './components/NoResults';
import SkeletonTile from './components/SkeletonTile';
import SourceSection, { EmptyState, OfflineBanner } from './components/SourceSection';
import AppSettings from './components/AppSettings';
import LinkSettings from './components/LinkSettings';
import { useDashboard } from './hooks/useDashboard';
import { useLocalStorage } from './hooks/useLocalStorage';
import { EXTERNAL, STORAGE_KEYS, GROUP_BY } from './constants';
import { buildSections } from './utils/containers';
import { stackOptions } from './utils/links';
import { isShown } from './utils/dashboard';

const EMPTY = { revision: null, versionChecks: { running: false, lastCompletedAt: null }, sources: [], containers: [], links: [] };
const LINK_ICON_FAILED = "Link added, but its icon couldn't be saved.";

const isOffline = source => source.status === 'DOWN' || source.status === 'ERROR';

/** The main area: loading, error, no matches, empty, or the sections. */
function Inventory({ isLoading, failed, query, sections, sectionList, sources, hostFilter, groupBy, showAll, showStopped, onClearQuery, onIncludeStopped }) {
  if (isLoading) {
    return (
      <>
        <div className="tile-grid">
          {Array.from({ length: 6 }, (_, index) => <SkeletonTile key={index} />)}
        </div>
        <output className="loading-status">Connecting…</output>
      </>
    );
  }
  if (failed) {
    return (
      <>
        <div className="error-message" role="alert">
          Failed to fetch containers. Ensure the server is running.
        </div>
        {sectionList}
      </>
    );
  }
  if (query.trim() !== '' && sections.every(section => section.containers.length + section.links.length === 0)) {
    return <NoResults query={query} showStopped={showStopped} onClear={onClearQuery} onIncludeStopped={onIncludeStopped} />;
  }
  if (sections.length === 0) return <EmptyState showAll={showAll} showStopped={showStopped} />;
  const banners = groupBy === GROUP_BY.STACK
    ? sources
      .filter(source => (hostFilter === 'all' || source.id === hostFilter) && isOffline(source))
      .map(source => <OfflineBanner key={source.id} source={source} />)
    : [];
  return (
    <>
      {banners}
      {sectionList}
    </>
  );
}

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
  const { data: fetched, isLoading, error } = useDashboard();
  const data = fetched ?? EMPTY;
  const { links } = data;
  const initialError = error && !fetched;
  const containers = useMemo(
    () => data.containers.filter(container => isShown(container, { showAll, showStopped, showSelf })),
    [data.containers, showAll, showStopped, showSelf]);
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
  const stacks = useMemo(() => stackOptions(data.containers, links), [data.containers, links]);
  const sourcesById = useMemo(() => Object.fromEntries(data.sources.map(source => [source.id, source])), [data.sources]);

  const linkBeingEdited = links.find(link => link.id === editingLink?.id);
  const openLinkSettings = useCallback(id => setEditingLink({ id, alert: null }), []);
  // Deleted elsewhere. Links aren't known yet while the first request is running.
  if (editingLink && fetched && !linkBeingEdited) setEditingLink(null);

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
        <Inventory
          isLoading={isLoading}
          failed={Boolean(initialError)}
          query={query}
          sections={sections}
          sectionList={sectionList}
          sources={data.sources}
          hostFilter={hostFilter}
          groupBy={groupBy}
          showAll={showAll}
          showStopped={showStopped}
          onClearQuery={() => setQuery('')}
          onIncludeStopped={() => setShowStopped(true)}
        />
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
          versionChecks={data.versionChecks}
          linksUnavailable={Boolean(initialError)}
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
