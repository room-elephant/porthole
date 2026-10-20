import { useId, useState } from 'react';
import { refreshVersions } from '../api';
import { Loader2 } from 'lucide-react';
import Modal from './Modal';
import { ASSETS, GROUP_BY } from '../constants';
import { plural } from '../utils/containers';

const STATUS_TEXT = { UP: 'Connected', DOWN: 'Unreachable', ERROR: 'Error', CHECKING: 'Checking…' };
const GROUP_OPTIONS = [
    { value: GROUP_BY.HOST_STACK, label: 'Host › Stack' },
    { value: GROUP_BY.HOST, label: 'Host' },
    { value: GROUP_BY.STACK, label: 'Stack' },
];

function ToggleSetting({ label, help, checked, onChange }) {
    const id = useId();
    const helpId = `${id}-help`;
    return (
        <div className="setting-toggle">
            <div className="setting-toggle-text">
                <label htmlFor={id}>{label}</label>
                <span id={helpId} className="setting-toggle-help">{help}</span>
            </div>
            <span className="setting-toggle-control">
                <input id={id} type="checkbox" checked={checked} aria-describedby={helpId} onChange={e => onChange(e.target.checked)} />
                <span className="toggle-slider" aria-hidden="true"></span>
            </span>
        </div>
    );
}

function HostRow({ source, count }) {
    const checking = source.status === 'CHECKING';
    const up = source.status === 'UP';
    let statusClass = '';
    if (checking) statusClass = 'host-status--checking';
    else if (!up) statusClass = 'host-status--bad';
    return (
        <div className="host-row">
            {checking ? <Loader2 size={14} className="status-spinner" aria-hidden="true" /> : <span className={`host-dot${up ? '' : ' host-dot--bad'}`} />}
            <span className="host-name">{source.name}</span>
            <span className="host-count">{plural(count, 'container')}</span>
            <span className={`host-status ${statusClass}`}>{STATUS_TEXT[source.status] ?? 'Unknown'}</span>
        </div>
    );
}

function AppSettings({
    showStopped,
    showAll,
    onToggleShowStopped,
    onToggleShowAll,
    showSelf = true,
    onToggleShowSelf,
    groupBy,
    onGroupByChange,
    counts = {},
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
    return (
        <Modal title="Settings" icon={<img src={ASSETS.PORTHOLE_ICON} alt="" />} onClose={onClose} className="app-settings-modal">
            <section className="settings-block">
                <h3 className="settings-heading-sm">Hosts</h3>
                {sources.length === 0 && <div className="status-row"><Loader2 size={14} className="status-spinner" aria-hidden="true" /><span>Connections: Unknown</span></div>}
                {sources.map(source => <HostRow key={source.id} source={source} count={counts[source.id] ?? 0} />)}
            </section>

            <div className="settings-divider" />

            <section className="settings-block">
                <h3 className="settings-heading-sm">Layout</h3>
                <div className="segmented-field">
                    <span>Group containers by</span>
                    <fieldset aria-label="Group containers by" className="segmented">
                        {GROUP_OPTIONS.map(option => (
                            <button key={option.value} type="button" aria-pressed={groupBy === option.value} onClick={() => onGroupByChange(option.value)}>
                                {option.label}
                            </button>
                        ))}
                    </fieldset>
                </div>
            </section>

            <div className="settings-divider" />

            <section className="settings-block">
                <h3 className="settings-heading-sm">Containers</h3>
                <ToggleSetting label="Show stopped containers" help="Shown with a dashed outline below running ones." checked={showStopped} onChange={onToggleShowStopped} />
                <ToggleSetting label="Show containers without a web UI" help="Shown as small chips under their group." checked={showAll} onChange={onToggleShowAll} />
                <ToggleSetting label="Show Porthole itself" help="Turn off to hide this Porthole instance from the dashboard." checked={showSelf} onChange={onToggleShowSelf} />
            </section>

            <div className="settings-divider" />

            <section className="settings-block">
                <h3 className="settings-heading-sm">Version updates</h3>
                <p className="settings-description">Check all containers for updates now. Results appear automatically when ready.</p>
                <button type="button" className="settings-btn-primary" onClick={checkVersions} disabled={refreshState === 'pending'}>
                    {refreshState === 'pending' && <Loader2 size={16} className="status-spinner" aria-hidden="true" />}
                    {refreshState === 'pending' ? 'Starting check…' : 'Check versions now'}
                </button>
                {refreshState === 'started' && <output className="settings-description">Version check started.</output>}
                {refreshState === 'error' && <p className="settings-description version-refresh-error" role="alert">Could not start version check. Please try again.</p>}
            </section>
        </Modal>
    );
}

export default AppSettings;
