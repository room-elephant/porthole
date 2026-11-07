import { ArrowUp } from 'lucide-react';
import { useContainerCard } from '../hooks/useContainerCard';

/** Compact button for a container without a web UI; opens its settings. */
export default function ContainerChip({ container, sourceOnline = true, hostTag = null, sourceName, onSettingsOpenChange }) {
    const { status, showUpdate, openSettings, settingsDialog } = useContainerCard(container, {
        sourceOnline,
        sourceName,
        onSettingsOpenChange,
    });
    const title = status.kind === 'ok' || status.kind === 'update' ? 'Running' : status.label;
    const label = `${container.displayName} settings${showUpdate ? ', update available' : ''}`;

    return (
        <>
            <button type="button" className={`chip chip--${status.kind}`} aria-label={label} onClick={openSettings}>
                <span className={`status-dot dot-${status.dot}`} title={title}></span>
                <span className="chip-name">{container.displayName}</span>
                {hostTag && <span className="chip-suffix">{` · ${hostTag}`}</span>}
                {showUpdate && (
                    <span className="chip-update-badge" title="Update available">
                        <ArrowUp size={8} strokeWidth={4} aria-hidden="true" />
                    </span>
                )}
                {status.label && <span className="chip-suffix">{' · '}<span className={`label-${status.kind}`}>{status.label}</span></span>}
            </button>
            {settingsDialog}
        </>
    );
}
