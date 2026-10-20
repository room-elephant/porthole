import { memo } from 'react';

function SkeletonTile() {
  return (
    <div className="tile skeleton-tile" aria-hidden="true">
      <span className="skeleton-icon"></span>
      <span className="skeleton-lines">
        <span className="skeleton-bar skeleton-bar--title"></span>
        <span className="skeleton-bar skeleton-bar--sub"></span>
      </span>
    </div>
  );
}

export default memo(SkeletonTile);
