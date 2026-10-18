import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Header from '../../components/Header';

const walker = { id: 'walker', name: 'Walker', status: 'UP', error: null };
const mike = { id: 'mike', name: 'Mike', status: 'DOWN', error: 'x' };

const renderHeader = (props = {}) => render(
  <>
    <input aria-label="Other" />
    <Header
      query=""
      onQueryChange={vi.fn()}
      sources={[walker, mike]}
      counts={{ walker: 3, mike: 0 }}
      hostFilter="all"
      onHostFilterChange={vi.fn()}
      onOpenSettings={vi.fn()}
      {...props}
    />
  </>
);

describe('Header', () => {
  it('shows the wordmark, filter and settings button', () => {
    renderHeader();
    expect(screen.getByText('Porthole')).toHaveClass('wordmark');
    expect(screen.getByRole('searchbox', { name: 'Filter containers' })).toHaveAttribute('placeholder', 'Filter containers, images, hosts…');
    expect(screen.getByRole('button', { name: 'Settings' })).toBeInTheDocument();
  });

  it('opens settings from the gear', () => {
    const onOpenSettings = vi.fn();
    renderHeader({ onOpenSettings });
    fireEvent.click(screen.getByRole('button', { name: 'Settings' }));
    expect(onOpenSettings).toHaveBeenCalled();
  });

  it('focuses the filter on "/" but not while typing in an input', async () => {
    const user = userEvent.setup();
    const onQueryChange = vi.fn();
    renderHeader({ onQueryChange });
    const filter = screen.getByRole('searchbox', { name: 'Filter containers' });

    await user.keyboard('/');
    expect(filter).toHaveFocus();
    expect(onQueryChange).not.toHaveBeenCalled();

    await user.keyboard('/');
    expect(onQueryChange).toHaveBeenCalledWith('/');

    await user.click(screen.getByLabelText('Other'));
    await user.keyboard('/');
    expect(filter).not.toHaveFocus();
  });

  it('ignores "/" with a modifier key held', async () => {
    const user = userEvent.setup();
    renderHeader();
    await user.keyboard('{Control>}/{/Control}');
    expect(screen.getByRole('searchbox', { name: 'Filter containers' })).not.toHaveFocus();
  });

  it('stops listening for "/" after unmount', () => {
    const removed = vi.spyOn(window, 'removeEventListener');
    const view = renderHeader();
    view.unmount();
    expect(removed).toHaveBeenCalledWith('keydown', expect.any(Function));
    removed.mockRestore();
  });

  it('clears the filter on Escape', async () => {
    const user = userEvent.setup();
    const onQueryChange = vi.fn();
    renderHeader({ query: 'plex', onQueryChange });
    await user.click(screen.getByRole('searchbox', { name: 'Filter containers' }));
    await user.keyboard('{Escape}');
    expect(onQueryChange).toHaveBeenCalledWith('');
  });

  it('marks the selected host pill pressed and shows offline hosts', () => {
    renderHeader();
    expect(screen.getByRole('button', { name: 'All 3' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: /Walker 3/ })).toHaveAttribute('aria-pressed', 'false');
    const offline = screen.getByRole('button', { name: /Mike offline/ });
    expect(offline).toHaveClass('pill--offline');
    expect(offline.querySelector('.pill-dot')).toBeInTheDocument();
  });

  it('selects a host from its pill', () => {
    const onHostFilterChange = vi.fn();
    renderHeader({ onHostFilterChange });
    fireEvent.click(screen.getByRole('button', { name: /Walker 3/ }));
    expect(onHostFilterChange).toHaveBeenCalledWith('walker');
  });
});
