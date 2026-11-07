import { useEffect, useRef, useState } from 'react';
import IconLibraryTab from './IconLibraryTab';
import IconUploadTab from './IconUploadTab';
import { imageSearchTerm, isUploadedIcon } from '../utils/icons';

const TABS = [{ id: 'library', label: 'Library' }, { id: 'upload', label: 'Upload' }];

/** Rejections that repeating the same request can never fix. */
const PERMANENT_REJECTIONS = {
    400: "That file isn't a PNG, WebP, JPEG or SVG image.",
    413: 'That file is over 1 MB. Choose an image up to 1 MB.',
    507: 'Porthole has reached its limit of 500 uploaded icons.',
};

/** Chooses a container's icon. Every choice saves straight away through `icon`. */
function IconPicker({ container, icon, onSaved }) {
    const [startingSearch] = useState(() => imageSearchTerm(container.image));
    const [tab, setTab] = useState(() => (isUploadedIcon(container.iconUrl) ? 'upload' : 'library'));
    const [query, setQuery] = useState(startingSearch);
    const [pickedName, setPickedName] = useState(null);
    const [uploadingFile, setUploadingFile] = useState(null);
    const rootRef = useRef(null);
    const searchRef = useRef(null);

    useEffect(() => {
        icon.clearError();
        // Focusing the search on a touch device opens a keyboard over the grid.
        const touch = window.matchMedia?.('(pointer: coarse)').matches;
        if (searchRef.current && !touch) {
            searchRef.current.focus();
            searchRef.current.select();
        } else {
            rootRef.current?.focus();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps -- runs once when the picker opens.
    }, []);

    // A file dropped beside the drop zone would otherwise navigate the browser away from the app.
    useEffect(() => {
        const keepPage = (event) => {
            if (event.target?.type !== 'file') event.preventDefault();
        };
        document.addEventListener('dragover', keepPage);
        document.addEventListener('drop', keepPage);
        return () => {
            document.removeEventListener('dragover', keepPage);
            document.removeEventListener('drop', keepPage);
        };
    }, []);

    const rejection = PERMANENT_REJECTIONS[icon.saveError?.response?.status];

    const pick = (name) => {
        setPickedName(name);
        icon.selectIcon(name, { onSaved });
    };
    const upload = (file) => {
        setUploadingFile(file);
        icon.uploadIcon(file, { onSaved });
    };

    return (
        <div ref={rootRef} className="icon-picker" tabIndex={-1}>
            <div role="tablist" aria-label="Icon source" className="icon-tabs">
                {TABS.map(({ id, label }) => (
                    <button key={id} type="button" role="tab" aria-selected={tab === id} disabled={icon.isSaving}
                        className={`icon-tab ${tab === id ? 'icon-tab--active' : ''}`} onClick={() => setTab(id)}>
                        {label}
                    </button>
                ))}
            </div>

            {icon.saveError && (
                <div role="alert" className="icon-alert">
                    <span>{rejection ?? "Couldn't save the icon."}</span>
                    {!rejection && <button type="button" className="icon-alert-retry" onClick={icon.retry}>Try again</button>}
                </div>
            )}

            {tab === 'library' ? (
                <IconLibraryTab iconUrl={container.iconUrl} query={query} startingSearch={startingSearch} searchRef={searchRef}
                    onQueryChange={setQuery} onPick={pick} pickedName={pickedName} isSaving={icon.isSaving}
                    onUploadInstead={() => setTab('upload')} />
            ) : (
                <IconUploadTab iconUrl={container.iconUrl} onUpload={upload} uploadingFile={uploadingFile} isSaving={icon.isSaving} />
            )}
        </div>
    );
}

export default IconPicker;
