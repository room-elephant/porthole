import { describe, it, expect, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import ContainerChip from '../../components/ContainerChip';
import { renderWithQuery, createMockContainer } from '../testUtils';

const headless = (overrides) => createMockContainer({ hasEndpoints: false, endpoints: [], ...overrides });

describe('ContainerChip', () => {
  beforeEach(() => localStorage.clear());

  it('renders a labelled button with a dot and the display name', () => {
    renderWithQuery(<ContainerChip container={headless()} />);
    const chip = screen.getByRole('button', { name: 'Test Container settings' });
    expect(chip).toHaveClass('chip');
    expect(chip.querySelector('.status-dot')).toHaveClass('dot-ok');
    expect(chip.querySelector('.status-dot')).toHaveAttribute('title', 'Running');
    expect(chip).toHaveTextContent('Test Container');
    expect(chip).not.toHaveTextContent('·');
  });

  it('adds the status label for non-ok statuses', () => {
    renderWithQuery(<ContainerChip container={headless({ status: 'Up 2 hours (unhealthy)' })} />);
    const chip = screen.getByRole('button', { name: 'Test Container settings' });
    expect(chip).toHaveTextContent('Test Container · Unhealthy');
    expect(chip.querySelector('.status-dot')).toHaveClass('dot-bad');
  });

  it('shows Offline when the host is down', () => {
    renderWithQuery(<ContainerChip container={headless()} sourceOnline={false} />);
    expect(screen.getByRole('button')).toHaveTextContent('Test Container · Offline');
  });

  it('puts the host tag after the name', () => {
    renderWithQuery(<ContainerChip container={headless()} hostTag="Walker" />);
    expect(screen.getByRole('button')).toHaveTextContent('Test Container · Walker');
  });

  describe('update badge', () => {
    const version = { currentVersion: '1.0', latestVersion: '2.0', updateAvailable: true };

    it('shows a badge after the name instead of a word and announces it', () => {
      renderWithQuery(<ContainerChip container={headless({ version })} />);
      const chip = screen.getByRole('button', { name: 'Test Container settings, update available' });
      const badge = chip.querySelector('.chip-update-badge');
      expect(badge).toHaveAttribute('title', 'Update available');
      expect(chip.firstElementChild).toHaveClass('status-dot');
      expect(chip.querySelector('.chip-name').compareDocumentPosition(badge) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(chip).toHaveTextContent(/^Test Container$/);
    });

    it('keeps the host suffix before the badge and the health label after it', () => {
      renderWithQuery(<ContainerChip container={headless({ version, status: 'Up 2 hours (unhealthy)' })} hostTag="Walker" />);
      const chip = screen.getByRole('button', { name: 'Test Container settings, update available' });
      expect(chip).toHaveTextContent('Test Container · Walker · Unhealthy');
      expect([...chip.children].map(el => el.className.split(' ')[0])).toEqual(['status-dot', 'chip-name', 'chip-suffix', 'chip-update-badge', 'chip-suffix']);
    });

    it('shows no badge when the update preference is off', () => {
      localStorage.setItem('version_check_v3_container:local:test-container', 'false');
      renderWithQuery(<ContainerChip container={headless({ version })} />);
      const chip = screen.getByRole('button', { name: 'Test Container settings' });
      expect(chip.querySelector('.chip-update-badge')).toBeNull();
    });
  });

  it('opens the settings dialog on click', () => {
    renderWithQuery(<ContainerChip container={headless()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
