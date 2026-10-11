import { useId } from 'react';
import Modal from './Modal';
import { groupEndpointsBySource } from '../utils/endpoints';

function ContainerSettings({ containerName, endpoints, selectedEndpointId, checkUpdates, onClose, onSelectEndpoint, onToggleCheckUpdates }) {
    const selectionName = useId();
    const groups = groupEndpointsBySource(endpoints);

    return (
        <Modal title={containerName} onClose={onClose} className="settings-modal">
            {groups.map((group, index) => (
                <div key={group.source} className={`settings-section ${index > 0 ? 'with-divider' : ''}`}>
                    <h4 className="settings-section-title">{group.title}</h4>
                    <div className="endpoint-list">
                        {group.endpoints.map(endpoint => (
                            <label key={endpoint.id} className={`endpoint-option ${selectedEndpointId === endpoint.id ? 'selected' : ''}`}>
                                <input
                                    type="radio"
                                    name={selectionName}
                                    value={endpoint.id}
                                    checked={selectedEndpointId === endpoint.id}
                                    onChange={() => onSelectEndpoint(endpoint.id)}
                                />
                                <span className="endpoint-url">{endpoint.url}</span>
                                {selectedEndpointId === endpoint.id && <span className="checkmark" aria-hidden="true">✓</span>}
                            </label>
                        ))}
                    </div>
                </div>
            ))}

            <div className={`settings-section ${groups.length > 0 ? 'with-divider' : ''}`}>
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
