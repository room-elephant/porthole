import { useState } from 'react';
import { Upload } from 'lucide-react';
import { isUploadedIcon, validateIconFile } from '../utils/icons';

function IconUploadTab({ iconUrl, onUpload, uploadingFile, isSaving }) {
    const [rejection, setRejection] = useState(null);
    const uploaded = isUploadedIcon(iconUrl);
    const uploading = isSaving && uploadingFile;

    const choose = (event) => {
        const file = event.target.files?.[0];
        // Choosing the same file again after an error must fire another change event.
        event.target.value = '';
        if (!file) return;
        const problem = validateIconFile(file);
        setRejection(problem);
        if (!problem) onUpload(file);
    };

    return (
        <div role="tabpanel" aria-label="Upload" className="icon-tab-panel">
            {uploaded && (
                <section className="icon-upload-current">
                    <span className="icon-upload-preview">
                        <img src={iconUrl} alt="" />
                        <span className="icon-current-tag">Current</span>
                    </span>
                    <div className="icon-upload-current-text">
                        <h3 className="settings-heading">Your uploaded icon</h3>
                        <span className="endpoint-helper">Shown on the tile and in settings.</span>
                    </div>
                </section>
            )}

            {uploading ? (
                <div className="icon-drop icon-drop--busy" aria-busy="true">
                    <span className="icon-spinner" aria-hidden="true" />
                    <span className="icon-drop-title">Uploading {uploadingFile.name}…</span>
                </div>
            ) : (
                <label className="icon-drop">
                    <input type="file" accept="image/png,image/webp,image/jpeg" onChange={choose} />
                    <Upload size={24} aria-hidden="true" />
                    <span className="icon-drop-title">{uploaded ? 'Replace: drop a new image, or browse' : 'Drop an image here, or browse'}</span>
                    <span className="endpoint-helper">PNG, WebP or JPEG, up to 1 MB. Saves straight away.</span>
                </label>
            )}

            {rejection && <p role="alert" className="icon-alert">{rejection}</p>}
        </div>
    );
}

export default IconUploadTab;
