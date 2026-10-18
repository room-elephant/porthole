import { useContainerCard } from '../hooks/useContainerCard';

/** Compact button for a container without a web UI; opens its settings. */
export default function ContainerChip({ container, sourceOnline = true, hostTag = null, sourceName, allowLegacyMigration = false, onSettingsOpenChange }) {
    const { status, openSettings, settingsDialog } = useContainerCard(container, {
        sourceOnline,
        allowLegacyMigration,
        sourceName,
        onSettingsOpenChange,
    });
    const title = status.kind === 'ok' || status.kind === 'update' ? 'Running' : status.label;

    return (
        <>
            <button type="button" className={`chip chip--${status.kind}`} aria-label={`${container.displayName} settings`} onClick={openSettings}>
                <span className={`status-dot dot-${status.dot}`} title={title}></span>
                <span className="chip-name">{container.displayName}</span>
                {hostTag && <span className="chip-suffix">{` · ${hostTag}`}</span>}
                {status.label && <span className={`chip-suffix label-${status.kind}`}>{` · ${status.label}`}</span>}
            </button>
            {settingsDialog}
        </>
    );
}
