import { useEffect, useRef } from 'react';

function Modal({ title, children, onClose, className = '' }) {
    const dialogRef = useRef(null);
    const modalRef = useRef(null);

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
            aria-labelledby="modal-title"
        >
            <button type="button" className="modal-backdrop" aria-label="Close dialog" onClick={onClose} />
            <div 
                ref={modalRef}
                className={`modal-content ${className}`} 
                tabIndex={-1}
            >
                <h3 id="modal-title" className="modal-title">{title}</h3>
                {children}
            </div>
        </dialog>
    );
}

export default Modal;
