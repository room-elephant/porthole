import { useState } from 'react';
import { Globe, Link } from 'lucide-react';

/** A link's chosen icon, or a globe, with the chain badge meaning "your link, not monitored". */
export default function LinkIcon({ iconUrl, size = 40, badge = true }) {
  const [failedUrl, setFailedUrl] = useState(null);
  return (
    <span className="container-icon" style={{ '--icon-size': `${size}px` }}>
      {iconUrl && iconUrl !== failedUrl
        ? <img src={iconUrl} alt="" loading="lazy" onError={() => setFailedUrl(iconUrl)} />
        : <span className="link-icon-globe"><Globe size={Math.round(size * 0.55)} aria-hidden="true" /></span>}
      {badge && (
        <span className="link-badge" title="Link · not monitored">
          <Link size={8} strokeWidth={3} aria-hidden="true" />
        </span>
      )}
    </span>
  );
}
