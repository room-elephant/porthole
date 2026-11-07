import { MoreVertical } from 'lucide-react';
import LinkIcon from './LinkIcon';
import { displayUrl } from '../utils/links';

export default function LinkTile({ link, hostTag = null, onOpenSettings }) {
  return (
    <div className="tile">
      <a className="tile-main" href={link.url} target="_blank" rel="noopener">
        <LinkIcon iconUrl={link.iconUrl} />
        <span className="tile-text">
          <span className="tile-name-row">
            <span className="tile-name" title={link.name}>{link.name}</span>
            {hostTag && <span className="tile-tag">{hostTag}</span>}
          </span>
          <span className="tile-sub">
            <span className="link-url" title={link.url}>{displayUrl(link.url)}</span>
          </span>
        </span>
      </a>
      <button type="button" className="tile-menu" aria-label={`${link.name} link settings`} data-link-menu={link.id} onClick={() => onOpenSettings(link.id)}>
        <MoreVertical size={18} aria-hidden="true" />
      </button>
    </div>
  );
}
