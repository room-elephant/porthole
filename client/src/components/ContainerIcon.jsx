import { ArrowUp } from 'lucide-react';
import { ASSETS } from '../constants';

/** Container image with a health dot and an optional purple update badge. */
export default function ContainerIcon({ container, status, showUpdate = false, size = 40 }) {
  const src = container.name.includes('porthole') ? ASSETS.PORTHOLE_ICON : container.iconUrl;
  return (
    <span className="container-icon" style={{ '--icon-size': `${size}px` }}>
      <img
        src={src}
        alt=""
        loading="lazy"
        onError={(e) => { e.target.src = ASSETS.FALLBACK_ICON; }}
      />
      {showUpdate && (
        <span className="update-badge" title="Update available">
          <ArrowUp size={9} strokeWidth={4} aria-hidden="true" />
        </span>
      )}
      <span
        className={`status-dot dot-${status.dot}`}
        title={status.kind === 'ok' || status.kind === 'update' ? 'Running' : status.label}
      ></span>
    </span>
  );
}
