import { Loader2 } from 'lucide-react';
import Modal from './Modal';

function AppSettings({ 
    showStopped, 
    showAll, 
    onToggleShowStopped, 
    onToggleShowAll, 
    onClose,
    dockerStatus = 'UNKNOWN'
}) {
    const isChecking = dockerStatus === 'CHECKING';

    const getStatusClass = () => {
        switch (dockerStatus) {
            case 'UP': return 'status-running';
            case 'DOWN': return 'status-stopped';
            default: return '';
        }
    };

    const getStatusLabel = () => {
        switch (dockerStatus) {
            case 'UP': return 'Connected';
            case 'DOWN': return 'Unavailable';
            case 'CHECKING': return 'Checking...';
            default: return 'Unknown';
        }
    };

    return (
        <Modal title="Settings" onClose={onClose} className="app-settings-modal">
            <div className="settings-section">
                <h4 className="settings-section-title">Status</h4>
                <div className="status-row">
                    {isChecking ? (
                        <Loader2 size={14} className="status-spinner" />
                    ) : (
                        <span className={`status-indicator ${getStatusClass()}`}></span>
                    )}
                    <span className="status-label">Docker: {getStatusLabel()}</span>
                </div>
            </div>

            <div className="settings-section with-divider">
                <h4 className="settings-section-title">Containers</h4>
                
                <label className="toggle-switch settings-toggle">
                    <input
                        type="checkbox"
                        checked={showStopped}
                        onChange={(e) => onToggleShowStopped(e.target.checked)}
                    />
                    <span className="toggle-slider"></span>
                    <span className="toggle-label">Show stopped containers</span>
                </label>
                
                <label className="toggle-switch settings-toggle settings-toggle-spaced">
                    <input
                        type="checkbox"
                        checked={showAll}
                        onChange={(e) => onToggleShowAll(e.target.checked)}
                    />
                    <span className="toggle-slider"></span>
                    <span className="toggle-label">Show containers without endpoints</span>
                </label>
            </div>
        </Modal>
    );
}

export default AppSettings;
