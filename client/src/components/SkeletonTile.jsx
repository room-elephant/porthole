import { memo } from 'react';

function SkeletonTile() {
  return (
    <div className="card skeleton-card" aria-hidden="true">
      <div className="card-actions">
        <span className="skeleton-status"></span>
        <span className="skeleton-button"></span>
      </div>
      <div className="skeleton-icon"></div>
      <div className="container-info">
        <div className="skeleton-text skeleton-title"></div>
        <div className="skeleton-text skeleton-subtitle"></div>
      </div>
    </div>
  );
}

export default memo(SkeletonTile);

