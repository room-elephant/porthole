import { useEffect, useId, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, Check, Pencil, Plus } from 'lucide-react';
import Modal from './Modal';
import ContainerIcon from './ContainerIcon';
import IconPicker from './IconPicker';
import { statusDetail } from '../utils/status';
import { NO_ENDPOINT_ID, endpointTag, groupEndpointsBySource, isValidCustomUrl } from '../utils/endpoints';

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
                onChange={() => onSelectEndpoint({ id, source })}
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

function CustomUrlRow({ url, selected, selectionName, radioRef, onSelect, onEdit }) {
    const urlId = useId();
    return (
        <div className={`endpoint-option endpoint-option--custom ${selected ? 'selected' : ''}`}>
            <label className="endpoint-option-main">
                <input ref={radioRef} type="radio" name={selectionName} value="custom" aria-label="Custom URL"
                    aria-describedby={urlId} checked={selected} onChange={onSelect} />
                <span id={urlId} className="endpoint-url endpoint-url--truncate" title={url}>{url}</span>
                <span className="endpoint-tag tag-custom">{endpointTag('custom')}</span>
            </label>
            <button type="button" className="endpoint-edit" aria-label="Edit custom URL" onClick={onEdit}>
                <Pencil size={16} aria-hidden="true" />
            </button>
        </div>
    );
}

function CustomUrlPanel({ initialUrl, onSave, onCancel, onRemove }) {
    const inputId = useId();
    const errorId = `${inputId}-error`;
    const inputRef = useRef(null);
    const [url, setUrl] = useState(initialUrl);
    const [invalid, setInvalid] = useState(false);
    useEffect(() => inputRef.current?.focus(), []);
    const save = (event) => {
        event.preventDefault();
        const value = url.trim();
        if (!isValidCustomUrl(value)) {
            setInvalid(true);
            inputRef.current?.focus();
            return;
        }
        onSave(value);
    };
    return (
        <form className="custom-url-panel" onSubmit={save} noValidate>
            <label htmlFor={inputId}>Custom URL</label>
            <input ref={inputRef} id={inputId} type="url" autoComplete="url" value={url}
                placeholder="https://app.example.com" aria-invalid={invalid}
                aria-describedby={invalid ? errorId : undefined}
                onChange={event => { setUrl(event.target.value); setInvalid(false); }} />
            {invalid && <p id={errorId} className="selection-error" role="alert">Enter an http or https address</p>}
            <div className="custom-url-actions">
                <button type="submit" className="custom-url-save">Save</button>
                <button type="button" className="custom-url-cancel" onClick={onCancel}>Cancel</button>
                {onRemove && <button type="button" className="custom-url-remove" onClick={onRemove}>Remove</button>}
            </div>
        </form>
    );
}

function EndpointSelection({ container, selectedEndpointId, onSelectEndpoint, isSaving, saveError, refreshError }) {
    const selectionName = useId();
    const serverUrl = container.endpoints.find(endpoint => endpoint.source === 'custom')?.url ?? null;
    const [editing, setEditing] = useState(false);
    const [submittedUrl, setSubmittedUrl] = useState(null);
    // The server keeps a custom URL only while it is selected. Hold it here so the
    // row survives choosing another option, until this dialog closes.
    const [rememberedUrl, setRememberedUrl] = useState(serverUrl);
    const [previousServerUrl, setPreviousServerUrl] = useState(serverUrl);
    if (serverUrl !== previousServerUrl) {
        setPreviousServerUrl(serverUrl);
        if (serverUrl) setRememberedUrl(serverUrl);
        if (serverUrl === submittedUrl) {
            setSubmittedUrl(null);
            setEditing(false);
        }
    }
    const customUrl = serverUrl ?? rememberedUrl;
    const customSelected = selectedEndpointId === 'custom';

    const radioRef = useRef(null);
    const addRef = useRef(null);
    const focusTarget = useRef(null);
    // The panel's controls unmount, and are disabled during a save, so focus moves
    // once the target control exists and the save has settled.
    useEffect(() => {
        const target = focusTarget.current?.current;
        if (!target || editing || isSaving) return;
        focusTarget.current = null;
        target.focus();
    });

    const endpoints = groupEndpointsBySource(container.endpoints.filter(endpoint => endpoint.source !== 'custom')).flatMap(group => group.endpoints);
    const effectiveEndpointId = endpoints.length === 0 && !customSelected ? NO_ENDPOINT_ID : selectedEndpointId;

    const closePanel = () => {
        setEditing(false);
        setSubmittedUrl(null);
    };
    const select = selection => {
        focusTarget.current = null;
        closePanel();
        onSelectEndpoint(selection);
    };
    const save = url => {
        focusTarget.current = radioRef;
        if (url === serverUrl) {
            closePanel();
            return;
        }
        setSubmittedUrl(url);
        onSelectEndpoint({ id: 'custom', source: 'custom', url });
    };
    const cancel = () => {
        focusTarget.current = customUrl ? radioRef : addRef;
        closePanel();
    };
    const remove = () => {
        focusTarget.current = addRef;
        closePanel();
        setRememberedUrl(null);
        if (!customSelected) return;
        const fallback = endpoints[0];
        onSelectEndpoint(fallback ? { id: fallback.id, source: fallback.source } : { id: NO_ENDPOINT_ID, source: 'none' });
    };
    const panel = (
        <CustomUrlPanel initialUrl={customUrl ?? ''} onSave={save} onCancel={cancel} onRemove={customUrl ? remove : null} />
    );

    return (
        <fieldset className="settings-section endpoint-selection" disabled={isSaving} aria-busy={isSaving}>
            <legend className="settings-heading">Tile opens</legend>
            {endpoints.map(endpoint => (
                <EndpointOption key={endpoint.id} id={endpoint.id} label={endpoint.url} source={endpoint.source}
                    tag={endpointTag(endpoint.source)} selectionName={selectionName}
                    selectedEndpointId={effectiveEndpointId} onSelectEndpoint={select} />
            ))}
            {customUrl && (editing ? panel : (
                <CustomUrlRow url={customUrl} selected={effectiveEndpointId === 'custom'} selectionName={selectionName}
                    radioRef={radioRef} onSelect={() => select({ id: 'custom', source: 'custom', url: customUrl })}
                    onEdit={() => setEditing(true)} />
            ))}
            <EndpointOption id={NO_ENDPOINT_ID} source="none" label="No web UI"
                helper="Shown as a small chip instead of a tile" selectionName={selectionName}
                selectedEndpointId={effectiveEndpointId} onSelectEndpoint={select} />
            {!customUrl && (editing ? panel : (
                <button ref={addRef} type="button" className="custom-url-add" onClick={() => setEditing(true)}>
                    <Plus size={18} aria-hidden="true" />
                    Add custom URL
                </button>
            ))}
            {isSaving && <p className="selection-pending" role="status">Saving selection…</p>}
            {refreshError && <p className="selection-error" role="alert">Selection saved, but containers could not be refreshed. Reload to see the change.</p>}
            {saveError && <p className="selection-error" role="alert">Could not save selection: {saveError.message}</p>}
        </fieldset>
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
            <output className={`status-row status-row--${tone}`}>
                <span className={`status-row-dot dot-${status.dot}`} aria-hidden="true"></span>
                <span className="status-row-title">{title}</span>
                {health && <span className="status-row-health">· {health}</span>}
                {time && <span className="status-row-time">{time}</span>}
            </output>
            {note && <p className="status-row-note">{note}</p>}
        </section>
    );
}

const ICON_UPDATED_NOTICE_MS = 4000;

function ContainerSettings({ container, sourceName, status, showUpdate, selectedEndpointId, checkUpdates, onClose, onSelectEndpoint, onToggleCheckUpdates, icon, isSaving = false, saveError = null, refreshError = null }) {
    const updateToggleId = useId();
    const subtitle = [container.name, sourceName].filter(Boolean).join(' · ') + (container.project ? ` / ${container.project}` : '');
    const [choosingIcon, setChoosingIcon] = useState(false);
    const [iconUpdated, setIconUpdated] = useState(false);
    const iconButtonRef = useRef(null);
    const focusIconButton = useRef(false);

    // The icon button only exists again once the settings view has rendered.
    useEffect(() => {
        if (!choosingIcon && focusIconButton.current) {
            focusIconButton.current = false;
            iconButtonRef.current?.focus();
        }
    }, [choosingIcon]);
    useEffect(() => {
        if (!iconUpdated) return undefined;
        const timer = setTimeout(() => setIconUpdated(false), ICON_UPDATED_NOTICE_MS);
        return () => clearTimeout(timer);
    }, [iconUpdated]);

    const openIconPicker = () => {
        setIconUpdated(false);
        setChoosingIcon(true);
    };
    const closeIconPicker = () => {
        focusIconButton.current = true;
        setChoosingIcon(false);
    };
    const iconSaved = () => {
        setIconUpdated(true);
        closeIconPicker();
    };

    if (choosingIcon) {
        return (
            <Modal
                title="Choose icon"
                icon={(
                    <button type="button" className="modal-back" aria-label="Back to settings" disabled={icon.isSaving} onClick={closeIconPicker}>
                        <ArrowLeft size={20} aria-hidden="true" />
                    </button>
                )}
                subtitle={subtitle}
                onClose={onClose}
                className="settings-modal"
            >
                <IconPicker container={container} icon={icon} onSaved={iconSaved} />
            </Modal>
        );
    }

    return (
        <Modal
            title={container.displayName}
            icon={(
                <button ref={iconButtonRef} type="button" className="icon-edit" aria-label={`Change icon for ${container.displayName}`} title="Change icon" onClick={openIconPicker}>
                    <ContainerIcon container={container} status={status} showUpdate={showUpdate} size={44} />
                    <span className="icon-edit-pencil" aria-hidden="true"><Pencil size={9} strokeWidth={2.6} /></span>
                </button>
            )}
            subtitle={iconUpdated && !icon.refreshError
                ? <span role="status" className="icon-updated"><Check size={12} strokeWidth={3} aria-hidden="true" />Icon updated</span>
                : subtitle}
            onClose={onClose}
            className="settings-modal"
        >
            {iconUpdated && icon.refreshError && (
                <p role="alert" className="selection-error">Icon saved, but containers could not be refreshed. Reload to see the change.</p>
            )}
            <StatusSection container={container} status={status} />
            <div className="settings-divider" />

            <EndpointSelection container={container} selectedEndpointId={selectedEndpointId}
                onSelectEndpoint={onSelectEndpoint} isSaving={isSaving} saveError={saveError} refreshError={refreshError} />
            <div className="settings-divider" />

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
