import { useEffect, useId, useRef } from 'react';
import { X } from 'lucide-react';

function Modal({ title, icon = null, subtitle = null, children, onClose, className = '' }) {
    const dialogRef = useRef(null);
    const modalRef = useRef(null);
    const titleId = useId();

    useEffect(() => {
        const handleEscape = (event) => {
            if (event.key === 'Escape') {
                event.preventDefault();
                onClose();
            }
        };

        document.addEventListener('keydown', handleEscape);
        return () => document.removeEventListener('keydown', handleEscape);
    }, [onClose]);

    useEffect(() => {
        const opener = document.activeElement;
        const dialog = dialogRef.current;
        dialog.showModal();
        modalRef.current?.focus();
        return () => {
            dialog.close();
            opener?.focus();
        };
    }, []);

    return (
        <dialog
            ref={dialogRef}
            className="modal-overlay"
            onCancel={event => {
                event.preventDefault();
                onClose();
            }}
            aria-modal="true"
            aria-labelledby={titleId}
        >
            <button type="button" className="modal-backdrop" aria-label="Close dialog" onClick={onClose} />
            <div 
                ref={modalRef}
                className={`modal-content ${className}`} 
                tabIndex={-1}
            >
                <span className="modal-handle" aria-hidden="true" />
                <div className="modal-header">
                    {icon}
                    <div className="modal-heading">
                        <h2 id={titleId} className="modal-title">{title}</h2>
                        {subtitle && <p className="modal-subtitle">{subtitle}</p>}
                    </div>
                    <button type="button" className="modal-close" aria-label="Close" onClick={onClose}>
                        <X size={20} aria-hidden="true" />
                    </button>
                </div>
                <div className="modal-body">{children}</div>
            </div>
        </dialog>
    );
}

export default Modal;
