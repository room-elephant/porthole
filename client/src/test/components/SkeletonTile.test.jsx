import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import SkeletonTile from '../../components/SkeletonTile';

describe('SkeletonTile', () => {
  it('renders a hidden horizontal tile shape', () => {
    render(<SkeletonTile />);
    const tile = document.querySelector('.tile.skeleton-tile');
    expect(tile).toBeInTheDocument();
    expect(tile).toHaveAttribute('aria-hidden', 'true');
  });

  it('renders an icon block and two bars', () => {
    render(<SkeletonTile />);
    expect(document.querySelector('.skeleton-icon')).toBeInTheDocument();
    expect(document.querySelectorAll('.skeleton-bar')).toHaveLength(2);
  });
});
