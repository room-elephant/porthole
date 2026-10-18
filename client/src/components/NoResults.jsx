import { SearchX } from 'lucide-react';

export default function NoResults({ query, showStopped, onClear, onIncludeStopped }) {
  return (
    <div className="no-results">
      <span className="no-results-icon"><SearchX size={24} aria-hidden="true" /></span>
      <h3>{`No containers match “${query.trim()}”`}</h3>
      <p>
        Searched names, images and hosts.
        {!showStopped && ' Stopped containers are hidden.'}
      </p>
      <div className="no-results-actions">
        <button type="button" className="btn-secondary" onClick={onClear}>Clear filter</button>
        {!showStopped && <button type="button" className="btn-ghost" onClick={onIncludeStopped}>Include stopped</button>}
      </div>
    </div>
  );
}
