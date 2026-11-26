import { useId, useMemo, useState } from 'react';
import { X } from 'lucide-react';
import { useIconLibrary } from '../hooks/useIconLibrary';
import { libraryIconUrl, searchIcons } from '../utils/icons';

const ICON_PAGE = 100;
const LOAD_MORE_DISTANCE_PX = 200;

const formatCount = count => count.toLocaleString('en-US');

const resultsHeading = (shown, total) => {
    if (shown === total) return `${formatCount(total)} icons`;
    const noun = shown === 1 ? 'match' : 'matches';
    return `${formatCount(shown)} ${noun} of ${formatCount(total)}`;
};

function IconTile({ name, current, busy, disabled, onPick }) {
    return (
        <button type="button" className={`icon-tile ${current || busy ? 'icon-tile--marked' : ''} ${busy ? 'icon-tile--busy' : ''}`}
            aria-label={current ? `${name}, current icon` : name} aria-current={current ? 'true' : undefined}
            aria-busy={busy ? 'true' : undefined} title={name} disabled={disabled} onClick={() => onPick(name)}>
            {current && <span className="icon-current-tag">Current</span>}
            <img src={libraryIconUrl(name)} alt="" loading="lazy" />
            <span className="icon-tile-name">{name}</span>
            {busy && <span className="icon-spinner icon-tile-spinner" aria-hidden="true" />}
        </button>
    );
}

function IconLibraryTab({ iconChoice, query, startingSearch, searchRef, onQueryChange, onPick, pickedName, isSaving, onUploadInstead }) {
    const searchId = useId();
    const library = useIconLibrary(true);
    const [visibleCount, setVisibleCount] = useState(ICON_PAGE);
    const results = useMemo(() => (library.data ? searchIcons(library.data, query) : []), [library.data, query]);
    const current = iconChoice?.library ?? null;
    const hasQuery = query.length > 0;

    const changeQuery = (value) => {
        onQueryChange(value);
        setVisibleCount(ICON_PAGE);
    };
    const loadMoreNearEnd = ({ currentTarget: grid }) => {
        if (grid.scrollHeight - grid.scrollTop - grid.clientHeight < LOAD_MORE_DISTANCE_PX) {
            setVisibleCount(count => count + ICON_PAGE);
        }
    };

    return (
        <div role="tabpanel" aria-label="Library" className="icon-tab-panel">
            <div className="icon-search">
                <label htmlFor={searchId} className="icon-search-label">Search icons</label>
                <div className="icon-search-field">
                    <input ref={searchRef} id={searchId} type="search" autoComplete="off" value={query}
                        onChange={event => changeQuery(event.target.value)} />
                    {hasQuery && (
                        <button type="button" className="icon-search-clear" aria-label="Clear search" onClick={() => changeQuery('')}>
                            <X size={16} aria-hidden="true" />
                        </button>
                    )}
                </div>
                {hasQuery && query === startingSearch && <span className="endpoint-helper">Prefilled from the image name.</span>}
            </div>

            {library.isPending && <p className="icon-loading">Loading icons…</p>}

            {library.isError && (
                <div className="icon-empty">
                    <output>Couldn't load the icon library.</output>
                    <div className="icon-empty-actions">
                        <button type="button" className="icon-empty-action" onClick={() => library.refetch()}>Retry</button>
                        <button type="button" className="icon-empty-action icon-empty-action--primary" onClick={onUploadInstead}>Upload your own</button>
                    </div>
                </div>
            )}

            {library.data && results.length === 0 && (
                <div className="icon-empty">
                    <output>No icons match “{query}”.</output>
                    <div className="icon-empty-actions">
                        <button type="button" className="icon-empty-action" onClick={() => changeQuery('')}>Clear search</button>
                        <button type="button" className="icon-empty-action icon-empty-action--primary" onClick={onUploadInstead}>Upload your own</button>
                    </div>
                </div>
            )}

            {results.length > 0 && (
                <section className="icon-results">
                    <h3 className="settings-heading"><output>{resultsHeading(results.length, library.data.length)}</output></h3>
                    <div className="icon-grid" onScroll={loadMoreNearEnd}>
                        {results.slice(0, visibleCount).map(({ name }) => {
                            const busy = isSaving && name === pickedName;
                            return <IconTile key={name} name={name} current={name === current} busy={busy}
                                disabled={isSaving && !busy} onPick={onPick} />;
                        })}
                    </div>
                </section>
            )}

            <p className="icon-credit">Icons from <a href="https://dashboardicons.com" target="_blank" rel="noreferrer">Dashboard Icons</a></p>
        </div>
    );
}

export default IconLibraryTab;
