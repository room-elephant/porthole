import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import StackSelect from '../../components/StackSelect';

const select = () => screen.getByRole('combobox', { name: 'Stack' });
const newInput = () => screen.getByRole('textbox', { name: 'New stack name' });
const renderSelect = (props = {}) => {
  const handlers = { onChange: vi.fn(), onNewChange: vi.fn(), onCommitNew: vi.fn() };
  const view = render(
    <>
      <label htmlFor="s">Stack</label>
      <StackSelect id="s" value={null} options={['base', 'home']} {...handlers} {...props} />
    </>,
  );
  return { ...handlers, ...view };
};
const labels = () => [...select().options].map(option => option.textContent);

describe('StackSelect', () => {
  it('lists no stack, the stacks and new stack', () => {
    renderSelect();
    expect(labels()).toEqual(['No stack', 'base', 'home', 'New stack…']);
    expect(select().selectedOptions[0]).toHaveTextContent('No stack');
  });

  it('selects the current stack, listing it even if the options lack it', () => {
    renderSelect({ value: 'hidden' });
    expect(labels()).toContain('hidden');
    expect(select().selectedOptions[0]).toHaveTextContent('hidden');
  });

  it('reports an existing choice and none', () => {
    const { onChange } = renderSelect({ value: 'base' });
    fireEvent.change(select(), { target: { value: 's:home' } });
    expect(onChange).toHaveBeenLastCalledWith('home');
    fireEvent.change(select(), { target: { value: 'none' } });
    expect(onChange).toHaveBeenLastCalledWith(null);
  });

  it('treats stacks named like the special options as normal stacks', () => {
    const { onChange } = renderSelect({ options: ['New stack…', 'No stack'] });
    const literal = [...select().options].filter(option => option.textContent === 'New stack…')[0];
    fireEvent.change(select(), { target: { value: literal.value } });
    expect(onChange).toHaveBeenCalledWith('New stack…');
    expect(screen.queryByRole('textbox', { name: 'New stack name' })).not.toBeInTheDocument();

    const noStackLiteral = [...select().options].filter(option => option.textContent === 'No stack')[1];
    fireEvent.change(select(), { target: { value: noStackLiteral.value } });
    expect(onChange).toHaveBeenLastCalledWith('No stack');
  });

  it('reveals an input for a new stack, reporting what is typed and committing on blur', () => {
    const { onNewChange, onCommitNew } = renderSelect();
    fireEvent.change(select(), { target: { value: 'new' } });
    expect(newInput()).toHaveFocus();
    expect(onNewChange).toHaveBeenLastCalledWith('');

    fireEvent.change(newInput(), { target: { value: ' media ' } });
    expect(onNewChange).toHaveBeenLastCalledWith(' media ');
    fireEvent.blur(newInput());

    expect(onCommitNew).toHaveBeenCalledWith('media');
  });

  it('does not commit an empty new stack', () => {
    const { onCommitNew } = renderSelect();
    fireEvent.change(select(), { target: { value: 'new' } });
    fireEvent.change(newInput(), { target: { value: '   ' } });
    fireEvent.blur(newInput());
    expect(onCommitNew).not.toHaveBeenCalled();
  });

  it('returns to the dropdown once the new stack is saved', () => {
    const props = { onChange: vi.fn(), onCommitNew: vi.fn() };
    const { rerender } = render(<StackSelect id="s" value={null} options={['base']} ariaLabel="Stack" {...props} />);
    fireEvent.change(select(), { target: { value: 'new' } });
    fireEvent.change(newInput(), { target: { value: 'media' } });
    fireEvent.blur(newInput());
    rerender(<StackSelect id="s" value="media" options={['base', 'media']} ariaLabel="Stack" {...props} />);
    expect(screen.queryByRole('textbox', { name: 'New stack name' })).not.toBeInTheDocument();
    expect(select().selectedOptions[0]).toHaveTextContent('media');
  });

  it('does not list what is being typed as an option', () => {
    const props = { onChange: vi.fn(), onNewChange: vi.fn() };
    const { rerender } = render(<StackSelect id="s" value={null} options={['base']} ariaLabel="Stack" {...props} />);
    fireEvent.change(select(), { target: { value: 'new' } });
    rerender(<StackSelect id="s" value="" options={['base']} ariaLabel="Stack" {...props} />);
    expect(labels()).toEqual(['No stack', 'base', 'New stack…']);
    rerender(<StackSelect id="s" value="med" options={['base']} ariaLabel="Stack" {...props} />);
    expect(labels()).toEqual(['No stack', 'base', 'New stack…']);
  });

  it('hides the input when choosing an existing stack instead', () => {
    renderSelect();
    fireEvent.change(select(), { target: { value: 'new' } });
    fireEvent.change(select(), { target: { value: 's:base' } });
    expect(screen.queryByRole('textbox', { name: 'New stack name' })).not.toBeInTheDocument();
  });
});
