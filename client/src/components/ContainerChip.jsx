import { useState } from 'react';
import { useContainerPrefs } from '../hooks/useContainerPrefs';
import ContainerSettings from './ContainerSettings';
import { containerStatus } from '../utils/status';

/** Compact button for a container without a web UI; opens its settings. */
export default function ContainerChip({ container, sourceOnline = true, hostTag = null, allowLegacyMigration = false }) {
    const [showConfig, setShowConfig] = useState(false);
    const { selectedEndpointId, selectEndpoint, checkUpdates, setCheckUpdates } = useContainerPrefs(container, allowLegacyMigration);
    const showUpdate = Boolean(checkUpdates && container.version?.updateAvailable);
    const status = containerStatus(container, { sourceOnline, showUpdate });
    const title = status.kind === 'ok' || status.kind === 'update' ? 'Running' : status.label;

    return (
        <>
            <button type="button" className={`chip chip--${status.kind}`} aria-label={`${container.displayName} settings`} onClick={() => setShowConfig(true)}>
                <span className={`status-dot dot-${status.dot}`} title={title}></span>
                <span className="chip-name">{container.displayName}</span>
                {hostTag && <span className="chip-suffix">{` · ${hostTag}`}</span>}
                {status.label && <span className={`chip-suffix label-${status.kind}`}>{` · ${status.label}`}</span>}
            </button>

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
