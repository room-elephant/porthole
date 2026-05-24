import { useState, useMemo, useEffect } from 'react';
import { Settings, Package, AlertTriangle } from 'lucide-react';
import ContainerTile from './components/ContainerTile';
import SkeletonTile from './components/SkeletonTile';
import AppSettings from './components/AppSettings';
import { useContainers } from './hooks/useContainers';
import { useDockerHealth } from './hooks/useDockerHealth';
import { useLocalStorage } from './hooks/useLocalStorage';
import { STORAGE_KEYS } from './constants';
import { groupByProject } from './utils/containers';

const isBadGateway = (error) => error?.response?.status === 502;

function App() {
  const [showSettings, setShowSettings] = useState(false);
  const [showAll, setShowAll] = useLocalStorage(STORAGE_KEYS.SHOW_ALL, false);
  const [showStopped, setShowStopped] = useLocalStorage(STORAGE_KEYS.SHOW_STOPPED, false);
  const {
    data: containers = [],
    isLoading,
    error: containersError,
    refetch: refetchContainers
  } = useContainers({ showAll, showStopped });

  const shouldCheckHealth = isBadGateway(containersError) || (containers.length === 0 && !isLoading && !containersError) || showSettings;

  const { data: dockerHealth, isLoading: isDockerHealthLoading, isError: dockerHealthError } = useDockerHealth({
    enabled: shouldCheckHealth,
    pollInterval: shouldCheckHealth ? 5000 : null
  });

  const isDockerDown = dockerHealth?.status === 'DOWN' || !!dockerHealthError || isBadGateway(containersError);

  useEffect(() => {
    if (dockerHealth?.status === 'UP') {
      refetchContainers();
    }
  }, [dockerHealth?.status, refetchContainers]);

  const dockerStatus = isDockerHealthLoading ? 'CHECKING' : (dockerHealth?.status || 'UNKNOWN');

  const { groupedContainers, standaloneContainers, projectNames } = useMemo(() => {
    const groups = groupByProject(containers);
    return {
      groupedContainers: groups,
      standaloneContainers: groups[null] || [],
      projectNames: Object.keys(groups)
        .filter(key => key !== 'null' && key !== null)
        .sort(),
    };
  }, [containers]);

  const renderContent = () => {
    if (isLoading) {
      return (
        <div className="container-grid">
          {[...Array(6)].map((_, i) => (
            <SkeletonTile key={i} />
          ))}
        </div>
      );
    }

    if (containersError && !isBadGateway(containersError)) {
      return (
        <div className="error-message">
          Failed to fetch containers. Ensure the server is running.
        </div>
      );
    }

    if (isDockerDown || isBadGateway(containersError)) {
      return (
        <div className="empty-state warning">
          <AlertTriangle size={64} strokeWidth={1} />
          <h2>Docker unavailable</h2>
          <p>Could not connect to Docker. Waiting for Docker to become available...</p>
        </div>
      );
    }

    if (containers.length === 0) {
      return (
        <div className="empty-state">
          <Package size={64} strokeWidth={1} />
          <h2>No containers found</h2>
          <p>
            {!showAll && !showStopped
              ? 'No running containers with exposed ports. Try enabling "Show stopped containers" or "Show containers without ports" in settings.'
              : !showStopped
              ? 'No containers with exposed ports found. Try enabling "Show stopped containers" in settings.'
              : !showAll
              ? 'No running containers found. Try enabling "Show containers without ports" in settings.'
              : 'Docker is running but no containers exist. Start some containers to see them here.'}
          </p>
        </div>
      );
    }

    return (
      <>
        {standaloneContainers.length > 0 && (
          <div className="container-grid">
            {standaloneContainers.map(container => (
              <ContainerTile key={container.id} container={container} />
            ))}
          </div>
        )}

        {projectNames.map(projectName => (
          <div key={projectName} className="project-section">
            <h2 className="project-title">{projectName}</h2>
            <div className="container-grid">
              {groupedContainers[projectName].map(container => (
                <ContainerTile key={container.id} container={container} />
              ))}
            </div>
          </div>
        ))}
      </>
    );
  };

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

        {renderContent()}
      </div>

      {showSettings && (
        <AppSettings
          showStopped={showStopped}
          showAll={showAll}
          onToggleShowStopped={setShowStopped}
          onToggleShowAll={setShowAll}
          onClose={() => setShowSettings(false)}
          dockerStatus={dockerStatus}
        />
      )}
    </div>
  );
}

export default App;
