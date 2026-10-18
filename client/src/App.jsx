import { useState, useMemo } from 'react';
import { Settings } from 'lucide-react';
import SkeletonTile from './components/SkeletonTile';
import SourceSection from './components/SourceSection';
import AppSettings from './components/AppSettings';
import { useContainers } from './hooks/useContainers';
import { useLocalStorage } from './hooks/useLocalStorage';
import { STORAGE_KEYS } from './constants';
import { groupBySource } from './utils/containers';

const EMPTY = { sources: [], containers: [] };

function App() {
  const [showSettings, setShowSettings] = useState(false);
  const [showAll, setShowAll] = useLocalStorage(STORAGE_KEYS.SHOW_ALL, false);
  const [showStopped, setShowStopped] = useLocalStorage(STORAGE_KEYS.SHOW_STOPPED, false);
  const { data = EMPTY, isLoading, error } = useContainers({ showAll, showStopped });
  const grouped = useMemo(() => groupBySource(data.containers), [data.containers]);

  let inventory;
  if (isLoading) {
    inventory = (
      <div className="container-grid">
        {Array.from({ length: 6 }, (_, index) => <SkeletonTile key={index} />)}
      </div>
    );
  } else if (error) {
    inventory = (
      <div className="error-message" role="alert">
        Failed to fetch containers. Ensure the server is running.
      </div>
    );
  } else {
    inventory = data.sources.map(source => (
      <SourceSection
        key={source.id}
        source={source}
        containers={grouped.get(source.id) || []}
        showAll={showAll}
        showStopped={showStopped}
      />
    ));
  }

  return (
    <div>
      <div className="app-container">
        <header className="app-header">
          <div className="header-title">
            <h1>Porthole</h1>
            <button className="settings-btn" onClick={() => setShowSettings(true)} title="Settings">
              <Settings size={24} />
            </button>
          </div>
        </header>
        {inventory}
      </div>
      {showSettings && (
        <AppSettings
          showStopped={showStopped}
          showAll={showAll}
          onToggleShowStopped={setShowStopped}
          onToggleShowAll={setShowAll}
          onClose={() => setShowSettings(false)}
          sources={error ? [] : data.sources}
        />
      )}
    </div>
  );
}

export default App;
