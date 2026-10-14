import { useState, memo } from 'react';
import { Settings, Loader2 } from 'lucide-react';
import { useContainerVersion } from '../hooks/useContainerVersion';
import { useLocalStorage } from '../hooks/useLocalStorage';
import { STORAGE_KEYS, ASSETS } from '../constants';
import { useEndpointSelection } from '../hooks/useEndpointSelection';
import ContainerSettings from './ContainerSettings';
import { NO_ENDPOINT_ID } from '../utils/endpoints';

const arraysEqual = (a, b) => 
    a.length === b.length && a.every((v, i) => v.id === b[i].id && v.source === b[i].source && v.url === b[i].url);

const getHealthStatus = (status) => status?.match(/\((healthy|unhealthy|health: starting)\)/i)?.[1]?.toLowerCase();

function ContainerTile({ container }) {
    const [showConfig, setShowConfig] = useState(false);
    const { selectedEndpoint, selectedEndpointId, selectEndpoint } = useEndpointSelection(container.name, container.endpoints);
    const hasEndpoints = container.hasEndpoints && selectedEndpointId !== NO_ENDPOINT_ID;
    const [checkUpdates, setCheckUpdates] = useLocalStorage(STORAGE_KEYS.VERSION_CHECK(container.name), true);

    const { data: versionInfo, isLoading: versionLoading } = useContainerVersion(
        container.id,
        { enabled: checkUpdates }
    );

    const getStatusClass = () => {
        const state = container.state?.toLowerCase();
        if (state === 'running') {
            const health = getHealthStatus(container.status);
            if (health === 'unhealthy') return 'status-stopped';
            if (health === 'health: starting') return 'status-warning';
            return 'status-running';
        }
        if (state === 'paused' || state === 'restarting') return 'status-warning';
        return 'status-stopped';
    };

    const handleTileClick = () => {
        if (selectedEndpoint) {
            window.open(selectedEndpoint.url, '_blank');
        } else {
            setShowConfig(true);
        }
    };

    const handleConfigClick = () => {
        setShowConfig(true);
    };

    return (
        <>
            <div className={`card container-card ${!hasEndpoints ? 'disabled' : ''}`}>
                <button
                    type="button"
                    className="card-launch"
                    aria-label={`Open ${container.displayName}`}
                    disabled={!hasEndpoints}
                    onClick={handleTileClick}
                >
                    <img
                        src={container.name.includes("porthole") ? ASSETS.PORTHOLE_ICON : container.iconUrl}
                        alt={container.name}
                        className="container-icon"
                        loading="lazy"
                        onError={(e) => { e.target.src = ASSETS.FALLBACK_ICON; }}
                    />
                    <span className="container-info">
                        <span className="container-title" title={container.name}>{container.displayName}</span>
                        <span className="container-image" title={container.image}>
                            {checkUpdates && versionLoading && (
                                <Loader2 size={14} className="version-loading" title="Checking for updates..." />
                            )}
                            {checkUpdates && !versionLoading && versionInfo?.updateAvailable && (
                                <span className="update-warning" title={`Update available: ${versionInfo.latestVersion}`}>
                                    ⚠️
                                </span>
                            )}
                            {container.image}
                        </span>
                    </span>
                </button>
                <div className="card-actions">
                    <span
                        className={`status-indicator ${getStatusClass()}`}
                        title={container.status || container.state}
                    ></span>
                    <button type="button" className="config-btn" onClick={handleConfigClick} title="Container Settings">
                        <Settings size={20} />
                    </button>
                </div>
            </div>

            {showConfig && (
                <ContainerSettings
                    containerName={container.name}
                    endpoints={container.endpoints}
                    selectedEndpointId={selectedEndpointId}
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
 * Ignore uptime-only changes, but refresh when Docker health changes.
 */
export default memo(ContainerTile, (prevProps, nextProps) => {
    const prev = prevProps.container;
    const next = nextProps.container;
    return (
        prev.id === next.id &&
        prev.name === next.name &&
        prev.displayName === next.displayName &&
        prev.state === next.state &&
        getHealthStatus(prev.status) === getHealthStatus(next.status) &&
        prev.image === next.image &&
        prev.iconUrl === next.iconUrl &&
        prev.hasEndpoints === next.hasEndpoints &&
        arraysEqual(prev.endpoints, next.endpoints)
    );
});
