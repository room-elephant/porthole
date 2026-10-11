import { useId } from 'react';
import Modal from './Modal';
import { NO_ENDPOINT_ID, groupEndpointsBySource } from '../utils/endpoints';

function EndpointOption({ id, label, selectionName, selectedEndpointId, onSelectEndpoint }) {
    const selected = selectedEndpointId === id;

    return (
        <label className={`endpoint-option ${selected ? 'selected' : ''}`}>
            <input
                type="radio"
                name={selectionName}
                value={id}
                checked={selected}
                onChange={() => onSelectEndpoint(id)}
            />
            <span className="endpoint-url">{label}</span>
            {selected && <span className="checkmark" aria-hidden="true">✓</span>}
        </label>
    );
}

function ContainerSettings({ containerName, endpoints, selectedEndpointId, checkUpdates, onClose, onSelectEndpoint, onToggleCheckUpdates }) {
    const selectionName = useId();
    const groups = groupEndpointsBySource(endpoints);

    return (
        <Modal title={containerName} onClose={onClose} className="settings-modal">
            <fieldset className="settings-section endpoint-selection">
                <legend className="settings-section-title">Endpoint</legend>
                <p className="settings-description endpoint-description">
                    Choose a URL to open, or no endpoint for containers without a web UI.
                </p>
                <EndpointOption
                    id={NO_ENDPOINT_ID}
                    label="No endpoint"
                    selectionName={selectionName}
                    selectedEndpointId={selectedEndpointId}
                    onSelectEndpoint={onSelectEndpoint}
                />
                {groups.map(group => (
                    <div key={group.source} className="endpoint-source">
                        <h5 className="endpoint-source-title">{group.title}</h5>
                        <div className="endpoint-list">
                            {group.endpoints.map(endpoint => (
                                <EndpointOption
                                    key={endpoint.id}
                                    id={endpoint.id}
                                    label={endpoint.url}
                                    selectionName={selectionName}
                                    selectedEndpointId={selectedEndpointId}
                                    onSelectEndpoint={onSelectEndpoint}
                                />
                            ))}
                        </div>
                    </div>
                ))}
            </fieldset>

            <div className="settings-section with-divider">
                <h4 className="settings-section-title">Version Updates</h4>
                <label className="toggle-switch settings-toggle">
                    <input
                        type="checkbox"
                        checked={checkUpdates}
                        onChange={(e) => onToggleCheckUpdates(e.target.checked)}
                    />
                    <span className="toggle-slider"></span>
                    <span className="toggle-label">Check for updates</span>
                </label>
                <p className="settings-description">
                    When enabled, Porthole will check if newer versions are available for this container.
                </p>
            </div>
        </Modal>
    );
}

export default ContainerSettings;
