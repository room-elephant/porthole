import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import Modal from '../../components/Modal';
import userEvent from '@testing-library/user-event';

describe('Modal', () => {
  const defaultProps = {
    title: 'Test Modal',
    onClose: vi.fn(),
    children: <div>Modal content</div>,
  };

  it('should render with title and children', () => {
    render(<Modal {...defaultProps} />);

    expect(screen.getByText('Test Modal')).toBeInTheDocument();
    expect(screen.getByText('Modal content')).toBeInTheDocument();
  });

  it('should call onClose when overlay is clicked', async () => {
    const onClose = vi.fn();
    render(<Modal {...defaultProps} onClose={onClose} />);

    const overlay = screen.getByRole('button', { name: 'Close dialog' });
    fireEvent.click(overlay);

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('should not call onClose when modal content is clicked', async () => {
    const onClose = vi.fn();
    render(<Modal {...defaultProps} onClose={onClose} />);

    fireEvent.click(screen.getByText('Modal content'));

    expect(onClose).not.toHaveBeenCalled();
  });

  it('should call onClose when Escape key is pressed', async () => {
    const onClose = vi.fn();
    render(<Modal {...defaultProps} onClose={onClose} />);

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('should have correct accessibility attributes', () => {
    render(<Modal {...defaultProps} />);

    const dialog = screen.getByRole('dialog');
    expect(dialog.tagName).toBe('DIALOG');
    expect(dialog).toHaveAttribute('open');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAttribute('aria-labelledby');
    expect(screen.getByRole('dialog', { name: 'Test Modal' })).toBe(dialog);
  });

  it('allows keyboard dismissal through the backdrop button', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<Modal {...defaultProps} onClose={onClose} />);

    screen.getByRole('button', { name: 'Close dialog' }).focus();
    await user.keyboard('{Enter}');

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('handles the native dialog cancel event', () => {
    const onClose = vi.fn();
    render(<Modal {...defaultProps} onClose={onClose} />);

    fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('restores focus to the opener when dismissed', () => {
    const opener = document.createElement('button');
    document.body.append(opener);
    opener.focus();
    const { unmount } = render(<Modal {...defaultProps} />);
    expect(opener).not.toHaveFocus();

    unmount();

    expect(opener).toHaveFocus();
    opener.remove();
  });

  it('should apply custom className', () => {
    render(<Modal {...defaultProps} className="custom-modal" />);

    const modalContent = screen.getByText('Modal content').closest('.modal-content');
    expect(modalContent).toHaveClass('custom-modal');
    expect(screen.getByText('Modal content').parentElement).toHaveClass('modal-body');
  });

  it('labels the dialog by its title element', () => {
    render(<Modal {...defaultProps} />);

    const title = screen.getByRole('heading', { level: 2, name: 'Test Modal' });
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-labelledby', title.id);
  });

  it('calls onClose from the close button', () => {
    const onClose = vi.fn();
    render(<Modal {...defaultProps} onClose={onClose} />);

    fireEvent.click(screen.getByRole('button', { name: 'Close' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('renders the icon and subtitle in the header', () => {
    render(<Modal {...defaultProps} icon={<span data-testid="icon" />} subtitle="Sub text" />);

    expect(screen.getByTestId('icon')).toBeInTheDocument();
    expect(screen.getByText('Sub text')).toHaveClass('modal-subtitle');
  });

  it('renders a decorative drag handle', () => {
    const { container } = render(<Modal {...defaultProps} />);

    expect(container.ownerDocument.querySelector('.modal-handle')).toHaveAttribute('aria-hidden', 'true');
  });
});
