import { useEffect, useRef } from 'react';
import { Search, Settings } from 'lucide-react';
import HostPills from './HostPills';
import { ASSETS } from '../constants';

const isTypingTarget = target =>
  target instanceof HTMLElement && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));

export default function Header({ query, onQueryChange, sources, counts, hostFilter, onHostFilterChange, onOpenSettings }) {
  const inputRef = useRef(null);

  useEffect(() => {
    const handleKeyDown = event => {
      if (event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey || isTypingTarget(event.target)) return;
      event.preventDefault();
      inputRef.current?.focus();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  return (
    <header className="app-bar">
      <div className="app-bar-inner">
        <div className="brand">
          <img src={ASSETS.PORTHOLE_ICON} alt="" width="32" height="32" />
          <h1 className="wordmark">Porthole</h1>
        </div>
        <label className="filter">
          <Search size={16} aria-hidden="true" />
          <input
            ref={inputRef}
            type="search"
            aria-label="Filter containers"
            placeholder="Filter containers, images, hosts…"
            value={query}
            onChange={event => onQueryChange(event.target.value)}
            onKeyDown={event => { if (event.key === 'Escape') onQueryChange(''); }}
          />
          <kbd>/</kbd>
        </label>
        <div className="app-bar-end">
          <HostPills sources={sources} counts={counts} selected={hostFilter} onSelect={onHostFilterChange} />
          <button type="button" className="icon-btn" aria-label="Settings" onClick={onOpenSettings}>
            <Settings size={18} aria-hidden="true" />
          </button>
        </div>
      </div>
    </header>
  );
}
