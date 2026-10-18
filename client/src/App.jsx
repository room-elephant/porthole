import { useState, useMemo } from 'react';
import Header from './components/Header';
import NoResults from './components/NoResults';
import SkeletonTile from './components/SkeletonTile';
import SourceSection, { EmptyState, OfflineBanner } from './components/SourceSection';
import AppSettings from './components/AppSettings';
import { useContainers } from './hooks/useContainers';
import { useLastKnownContainers } from './hooks/useLastKnownContainers';
import { useLocalStorage } from './hooks/useLocalStorage';
import { STORAGE_KEYS, GROUP_BY } from './constants';
import { buildSections } from './utils/containers';

const EMPTY = { sources: [], containers: [] };
const isOffline = source => source.status === 'DOWN' || source.status === 'ERROR';

function App() {
  const [showSettings, setShowSettings] = useState(false);
  const [query, setQuery] = useState('');
  const [selectedHost, setHostFilter] = useState('all');
  const [showAll, setShowAll] = useLocalStorage(STORAGE_KEYS.SHOW_ALL, false);
  const [showStopped, setShowStopped] = useLocalStorage(STORAGE_KEYS.SHOW_STOPPED, false);
  const [storedGroupBy, setGroupBy] = useLocalStorage(STORAGE_KEYS.GROUP_BY, GROUP_BY.HOST_STACK);
  const groupBy = Object.values(GROUP_BY).includes(storedGroupBy) ? storedGroupBy : GROUP_BY.HOST_STACK;
  const { data = EMPTY, isLoading, error } = useContainers({ showAll, showStopped });
  const containers = useLastKnownContainers(data);
  const hostFilter = data.sources.some(source => source.id === selectedHost) ? selectedHost : 'all';
  const counts = useMemo(() => {
    const result = Object.fromEntries(data.sources.map(source => [source.id, 0]));
    containers.forEach(container => { result[container.sourceId] = (result[container.sourceId] ?? 0) + 1; });
    return result;
  }, [data.sources, containers]);
  const sections = useMemo(
    () => buildSections({ sources: data.sources, containers, groupBy, hostFilter, query }),
    [data.sources, containers, groupBy, hostFilter, query]);
  const sourcesById = useMemo(() => Object.fromEntries(data.sources.map(source => [source.id, source])), [data.sources]);

  let inventory;
  if (isLoading) {
    inventory = (
      <>
        <div className="tile-grid">
          {Array.from({ length: 6 }, (_, index) => <SkeletonTile key={index} />)}
        </div>
        <p className="loading-status" role="status">Connecting…</p>
      </>
    );
  } else if (error) {
    inventory = (
      <div className="error-message" role="alert">
        Failed to fetch containers. Ensure the server is running.
      </div>
    );
  } else if (query.trim() !== '' && sections.every(section => section.containers.length === 0)) {
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
        {sections.map(section => (
          <SourceSection
            key={section.id}
            section={section}
            sourcesById={sourcesById}
            showAll={showAll}
            showStopped={showStopped}
            allowLegacyMigration={data.sources.length === 1}
          />
        ))}
      </>
    );
  }

  return (
    <div>
      <Header
        query={query}
        onQueryChange={setQuery}
        sources={error ? [] : data.sources}
        counts={counts}
        hostFilter={hostFilter}
        onHostFilterChange={setHostFilter}
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
          groupBy={groupBy}
          onGroupByChange={setGroupBy}
          counts={counts}
          onClose={() => setShowSettings(false)}
          sources={error ? [] : data.sources}
        />
      )}
    </div>
  );
}

export default App;
