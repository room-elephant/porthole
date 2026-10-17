import { useState } from 'react';
import { refreshVersions } from '../api';
import { Loader2 } from 'lucide-react';
import Modal from './Modal';

function AppSettings({ 
    showStopped, 
    showAll, 
    onToggleShowStopped, 
    onToggleShowAll, 
    onClose,
    sources = []
}) {
    const [refreshState, setRefreshState] = useState('idle');
    const checkVersions = async () => {
        if (refreshState === 'pending') return;
        setRefreshState('pending');
        try {
            await refreshVersions();
            setRefreshState('started');
        } catch {
            setRefreshState('error');
        }
    };
    const statusLabel = status => ({UP:'Connected',DOWN:'Unavailable',ERROR:'Error',CHECKING:'Checking...'}[status] || 'Unknown');
    return (
        <Modal title="Settings" onClose={onClose} className="app-settings-modal">
            <div className="settings-section">
                <h4 className="settings-section-title">Status</h4>
                {sources.length === 0 && <div className="status-row"><Loader2 size={14} className="status-spinner"/><span className="status-label">Connections: Unknown</span></div>}
                {sources.map(source => <div key={source.id} className="status-row">
                    {source.status === 'CHECKING' ? <Loader2 size={14} className="status-spinner"/> : <span className={`status-indicator ${source.status === 'UP' ? 'status-running' : 'status-stopped'}`}/>}
                    <span className="status-label">{source.name}: {statusLabel(source.status)}</span>
                </div>)}
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
            <div className="settings-section with-divider">
                <h4 className="settings-section-title">Version updates</h4>
                <p className="settings-description">Check all containers for updates now. Results appear automatically when ready.</p>
                <button type="button" className="settings-btn-primary" onClick={checkVersions} disabled={refreshState === 'pending'}>
                    {refreshState === 'pending' && <Loader2 size={16} className="status-spinner" aria-hidden="true" />}
                    {refreshState === 'pending' ? 'Starting check…' : 'Check versions now'}
                </button>
                {refreshState === 'started' && <p className="settings-description" role="status">Version check started.</p>}
                {refreshState === 'error' && <p className="settings-description version-refresh-error" role="alert">Could not start version check. Please try again.</p>}
            </div>
        </Modal>
    );
}

export default AppSettings;
