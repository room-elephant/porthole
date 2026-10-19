import { useId } from 'react';
import { ArrowRight, Check } from 'lucide-react';
import Modal from './Modal';
import ContainerIcon from './ContainerIcon';
import { statusDetail } from '../utils/status';
import { NO_ENDPOINT_ID, endpointTag, groupEndpointsBySource } from '../utils/endpoints';

function EndpointOption({ id, label, tag = null, source = null, helper = null, selectionName, selectedEndpointId, onSelectEndpoint }) {
    const selected = selectedEndpointId === id;
    const detailId = `${selectionName}-${id}-detail`;

    return (
        <label className={`endpoint-option ${selected ? 'selected' : ''}`}>
            <input
                type="radio"
                name={selectionName}
                value={id}
                aria-label={label}
                aria-describedby={detailId}
                checked={selected}
                onChange={() => onSelectEndpoint(id)}
            />
            {helper ? (
                <span className="endpoint-text">
                    <span className="endpoint-label">{label}</span>
                    <span id={detailId} className="endpoint-helper">{helper}</span>
                </span>
            ) : (
                <span className="endpoint-url">{label}</span>
            )}
            {tag && <span id={detailId} className={`endpoint-tag tag-${source}`}>{tag}</span>}
        </label>
    );
}

function ImageCard({ container }) {
    const { version } = container;
    const updateAvailable = Boolean(version?.updateAvailable);
    const versionKnown = Boolean(version) && version.latestVersion != null;

    return (
        <div className={`image-card ${updateAvailable ? 'image-card--update' : ''}`}>
            <div className="image-card-header">
                {updateAvailable && <span className="image-status image-status--update">Update available</span>}
                {!updateAvailable && versionKnown && (
                    <span className="image-status image-status--ok">
                        <Check size={16} aria-hidden="true" />
                        Up to date
                    </span>
                )}
                {!versionKnown && !updateAvailable && <span className="image-status image-status--unknown">Version unknown</span>}
            </div>
            <div className="image-card-body">
                <span className="image-name">{container.image}</span>
                <div className={`version-row ${updateAvailable ? 'version-row--update' : ''}`}>
                    <div className="version-box">
                        <span className="version-label">Running</span>
                        <span className="version-value">{version?.currentVersion || 'unknown'}</span>
                    </div>
                    {updateAvailable && (
                        <>
                            <ArrowRight size={18} className="version-arrow" aria-hidden="true" />
                            <div className="version-box version-box--latest">
                                <span className="version-label">Latest</span>
                                <span className="version-value">{version.latestVersion}</span>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}

function StatusSection({ container, status }) {
    const { tone, title, health, time, note } = statusDetail(container, status);

    return (
        <section className="settings-section">
            <h3 className="settings-heading status-heading">Status</h3>
            <div role="status" className={`status-row status-row--${tone}`}>
                <span className={`status-row-dot dot-${status.dot}`} aria-hidden="true"></span>
                <span className="status-row-title">{title}</span>
                {health && <span className="status-row-health">· {health}</span>}
                {time && <span className="status-row-time">{time}</span>}
            </div>
            {note && <p className="status-row-note">{note}</p>}
        </section>
    );
}

function ContainerSettings({ container, sourceName, status, showUpdate, selectedEndpointId, checkUpdates, onClose, onSelectEndpoint, onToggleCheckUpdates }) {
    const selectionName = useId();
    const updateToggleId = useId();
    const endpoints = groupEndpointsBySource(container.endpoints).flatMap(group => group.endpoints);
    const effectiveEndpointId = endpoints.length === 0 ? NO_ENDPOINT_ID : selectedEndpointId;
    const subtitle = [container.name, sourceName].filter(Boolean).join(' · ') + (container.project ? ` / ${container.project}` : '');

    return (
        <Modal
            title={container.displayName}
            icon={<ContainerIcon container={container} status={status} showUpdate={showUpdate} size={44} />}
            subtitle={subtitle}
            onClose={onClose}
            className="settings-modal"
        >
            <StatusSection container={container} status={status} />
            <div className="settings-divider" />

            <fieldset className="settings-section endpoint-selection">
                <legend className="settings-heading">Tile opens</legend>
                {endpoints.map(endpoint => (
                    <EndpointOption
                        key={endpoint.id}
                        id={endpoint.id}
                        label={endpoint.url}
                        source={endpoint.source}
                        tag={endpointTag(endpoint.source)}
                        selectionName={selectionName}
                        selectedEndpointId={selectedEndpointId}
                        onSelectEndpoint={onSelectEndpoint}
                    />
                ))}
                <EndpointOption
                    id={NO_ENDPOINT_ID}
                    label="No web UI"
                    helper="Shown as a small chip instead of a tile"
                    selectionName={selectionName}
                    selectedEndpointId={effectiveEndpointId}
                    onSelectEndpoint={onSelectEndpoint}
                />
            </fieldset>

            <div className="settings-section">
                <h3 className="settings-heading">Image</h3>
                <ImageCard container={container} />
                <div className="update-toggle">
                    <span className="update-toggle-text">
                        <label htmlFor={updateToggleId} className="update-toggle-label">Show update badge</label>
                        <span id={`${updateToggleId}-help`} className="endpoint-helper">Checks keep running either way; this only hides the badge.</span>
                    </span>
                    <input
                        id={updateToggleId}
                        type="checkbox"
                        checked={checkUpdates}
                        aria-describedby={`${updateToggleId}-help`}
                        onChange={(e) => onToggleCheckUpdates(e.target.checked)}
                    />
                    <span className="toggle-slider" aria-hidden="true"></span>
                </div>
            </div>
        </Modal>
    );
}

export default ContainerSettings;
