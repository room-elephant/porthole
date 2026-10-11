import { useState, memo } from 'react';
import { Settings, Loader2 } from 'lucide-react';
import { useContainerVersion } from '../hooks/useContainerVersion';
import { useLocalStorage } from '../hooks/useLocalStorage';
import { STORAGE_KEYS, ASSETS } from '../constants';
import { useEndpointSelection } from '../hooks/useEndpointSelection';
import ContainerSettings from './ContainerSettings';

const arraysEqual = (a, b) => 
    a.length === b.length && a.every((v, i) => v.id === b[i].id && v.source === b[i].source && v.url === b[i].url);

function ContainerTile({ container }) {
    const [showConfig, setShowConfig] = useState(false);
    const hasEndpoints = container.hasEndpoints;

    const { selectedEndpoint, selectEndpoint } = useEndpointSelection(container.name, container.endpoints);
    const [checkUpdates, setCheckUpdates] = useLocalStorage(STORAGE_KEYS.VERSION_CHECK(container.name), true);

    const { data: versionInfo, isLoading: versionLoading } = useContainerVersion(
        container.id,
        { enabled: checkUpdates }
    );

    const getStatusClass = () => {
        const state = container.state?.toLowerCase();
        if (state === 'running') return 'status-running';
        if (state === 'paused' || state === 'restarting') return 'status-warning';
        return 'status-stopped';
    };

    const handleTileClick = () => {
        if (!hasEndpoints) return;

        if (selectedEndpoint) {
            window.open(selectedEndpoint.url, '_blank');
        } else {
            setShowConfig(true);
        }
    };

    const handleConfigClick = (e) => {
        e.stopPropagation();
        setShowConfig(true);
    };

    return (
        <>
            <div className={`card ${!hasEndpoints ? 'disabled' : ''}`} onClick={handleTileClick}>
                <div className="card-actions">
                    <span 
                        className={`status-indicator ${getStatusClass()}`} 
                        title={container.status || container.state}
                    ></span>
                    <button className="config-btn" onClick={handleConfigClick} title="Container Settings">
                        <Settings size={20} />
                    </button>
                </div>
                <img
                    src={container.name.includes("porthole") ? ASSETS.PORTHOLE_ICON : container.iconUrl}
                    alt={container.name}
                    className="container-icon"
                    loading="lazy"
                    onError={(e) => { e.target.src = ASSETS.FALLBACK_ICON; }}
                />
                <div className="container-info">
                    <h3 title={container.name}>{container.displayName}</h3>
                    <p className="container-image" title={container.image}>
                        {checkUpdates && versionLoading && (
                            <Loader2 size={14} className="version-loading" title="Checking for updates..." />
                        )}
                        {checkUpdates && !versionLoading && versionInfo?.updateAvailable && (
                            <span className="update-warning" title={`Update available: ${versionInfo.latestVersion}`}>
                                ⚠️
                            </span>
                        )}
                        {container.image}
                    </p>
                </div>
            </div>

            {showConfig && (
                <ContainerSettings
                    containerName={container.name}
                    endpoints={container.endpoints}
                    selectedEndpointId={selectedEndpoint?.id ?? null}
                    checkUpdates={checkUpdates}
                    onClose={() => setShowConfig(false)}
                    onSelectEndpoint={selectEndpoint}
                    onToggleCheckUpdates={setCheckUpdates}
                />
            )}
        </>
    );
}

/**
 * Memoized ContainerTile component.
 * Status is intentionally excluded from comparison as it contains uptime
 * which changes constantly but doesn't affect visual rendering.
 */
export default memo(ContainerTile, (prevProps, nextProps) => {
    const prev = prevProps.container;
    const next = nextProps.container;
    return (
        prev.id === next.id &&
        prev.name === next.name &&
        prev.displayName === next.displayName &&
        prev.state === next.state &&
        prev.image === next.image &&
        prev.iconUrl === next.iconUrl &&
        prev.hasEndpoints === next.hasEndpoints &&
        arraysEqual(prev.endpoints, next.endpoints)
    );
});
