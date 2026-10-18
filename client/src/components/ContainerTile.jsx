import { useState, memo } from 'react';
import { ArrowUpRight, MoreVertical } from 'lucide-react';
import { useContainerPrefs } from '../hooks/useContainerPrefs';
import ContainerIcon from './ContainerIcon';
import ContainerSettings from './ContainerSettings';
import { endpointLabel } from '../utils/endpoints';
import { containerStatus, getHealthStatus } from '../utils/status';

const arraysEqual = (a, b) =>
    a.length === b.length && a.every((v, i) => v.id === b[i].id && v.source === b[i].source && v.url === b[i].url);

const hostText = (container, selectedEndpoint) => {
    if (selectedEndpoint) return endpointLabel(selectedEndpoint.url);
    return container.endpoints.length > 0 ? 'Choose a URL' : 'No web UI';
};

function ContainerTile({ container, sourceOnline = true, hostTag = null, allowLegacyMigration = false }) {
    const [showConfig, setShowConfig] = useState(false);
    const { selectedEndpoint, selectedEndpointId, selectEndpoint, checkUpdates, setCheckUpdates } = useContainerPrefs(container, allowLegacyMigration);
    const showUpdate = Boolean(checkUpdates && container.version?.updateAvailable);
    const status = containerStatus(container, { sourceOnline, showUpdate });
    const isLink = status.openable && Boolean(selectedEndpoint);
    const settingsLabel = `${container.displayName} settings`;

    const content = (
        <>
            <ContainerIcon container={container} status={status} showUpdate={showUpdate} size={40} />
            <span className="tile-text">
                <span className="tile-name" title={container.name}>{container.displayName}</span>
                <span className="tile-sub">
                    {hostTag && <span className="tile-tag">{hostTag}</span>}
                    {status.label && (
                        <>
                            <span className={`tile-label label-${status.kind}`}>{status.label}</span>
                            <span className="tile-dot" aria-hidden="true">·</span>
                        </>
                    )}
                    <span className="tile-host">{hostText(container, selectedEndpoint)}</span>
                    <span className="tile-image" title={container.image}>{container.image}</span>
                </span>
            </span>
        </>
    );

    return (
        <>
            <div className={`tile tile--${status.kind}${isLink ? '' : ' tile--dashed'}`}>
                {isLink ? (
                    <a className="tile-main" href={selectedEndpoint.url} target="_blank" rel="noopener">
                        {content}
                        <ArrowUpRight className="tile-go" size={16} aria-hidden="true" />
                    </a>
                ) : (
                    <button type="button" className="tile-main" aria-label={settingsLabel} onClick={() => setShowConfig(true)}>
                        {content}
                    </button>
                )}
                <button type="button" className="tile-menu" aria-label={settingsLabel} onClick={() => setShowConfig(true)}>
                    <MoreVertical size={18} aria-hidden="true" />
                </button>
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
        prev.sourceId === next.sourceId &&
        prevProps.allowLegacyMigration === nextProps.allowLegacyMigration &&
        prevProps.sourceName === nextProps.sourceName &&
        prevProps.sourceOnline === nextProps.sourceOnline &&
        prevProps.hostTag === nextProps.hostTag &&
        prev.name === next.name &&
        prev.displayName === next.displayName &&
        prev.state === next.state &&
        getHealthStatus(prev.status) === getHealthStatus(next.status) &&
        prev.image === next.image &&
        prev.iconUrl === next.iconUrl &&
        prev.hasEndpoints === next.hasEndpoints &&
        prev.version?.currentVersion === next.version?.currentVersion &&
        prev.version?.latestVersion === next.version?.latestVersion &&
        prev.version?.updateAvailable === next.version?.updateAvailable &&
        arraysEqual(prev.endpoints, next.endpoints)
    );
});
