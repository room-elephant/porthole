import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import LinkTile from '../../components/LinkTile';

const link = { id: 'a'.repeat(32), name: 'Router', url: 'http://192.168.50.1/', stack: null, iconUrl: 'https://cdn/router.webp' };
const renderTile = (props = {}) => {
  const onOpenSettings = vi.fn();
  const view = render(<LinkTile link={link} onOpenSettings={onOpenSettings} {...props} />);
  return { onOpenSettings, ...view };
};

describe('LinkTile', () => {
  it('opens the URL in a new tab', () => {
    renderTile();
    const anchor = screen.getByRole('link');
    expect(anchor).toHaveAttribute('href', 'http://192.168.50.1/');
    expect(anchor).toHaveAttribute('target', '_blank');
    expect(anchor).toHaveAttribute('rel', 'noopener');
  });

  it('shows the URL without its scheme, with the full URL as a tooltip', () => {
    renderTile();
    const subtitle = screen.getByText('192.168.50.1');
    expect(subtitle).toHaveAttribute('title', 'http://192.168.50.1/');
    expect(subtitle).toHaveClass('link-url');
  });

  it('has a chain badge and no status dot', () => {
    const { container } = renderTile();
    expect(screen.getByTitle('Link · not monitored')).toBeInTheDocument();
    expect(container.querySelector('.status-dot')).toBeNull();
  });

  it('shows the chosen icon', () => {
    const { container } = renderTile();
    expect(container.querySelector('img')).toHaveAttribute('src', 'https://cdn/router.webp');
  });

  it('shows the globe with no icon', () => {
    const { container } = renderTile({ link: { ...link, iconUrl: null } });
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('.link-icon-globe')).toBeInTheDocument();
  });

  it('falls back to the globe, not the Docker logo, when the icon fails to load', () => {
    const { container } = renderTile();
    fireEvent.error(container.querySelector('img'));
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('.link-icon-globe')).toBeInTheDocument();
  });

  it('tries a new icon after an earlier one failed', () => {
    const { container, rerender } = renderTile();
    fireEvent.error(container.querySelector('img'));
    rerender(<LinkTile link={{ ...link, iconUrl: 'https://cdn/other.webp' }} onOpenSettings={vi.fn()} />);
    expect(container.querySelector('img')).toHaveAttribute('src', 'https://cdn/other.webp');
  });

  it('opens settings from the menu', () => {
    const { onOpenSettings } = renderTile();
    const menu = screen.getByRole('button', { name: 'Router link settings' });
    expect(menu).toHaveAttribute('data-link-menu', link.id);
    fireEvent.click(menu);
    expect(onOpenSettings).toHaveBeenCalledWith(link.id);
  });

  it('shows a host tag only when given one', () => {
    renderTile({ hostTag: 'External' });
    expect(screen.getByText('External')).toBeInTheDocument();
  });
});
