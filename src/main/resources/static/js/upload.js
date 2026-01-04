// upload.js
import { state, dom } from './state.js';
import { getFileIcon } from './utils.js';

export function handleFileSelect(event) {
    const file = event.target.files[0];
    if (!file) return;
    if (file.size > 50 * 1024 * 1024) { alert('File quá lớn! Tối đa 50MB.'); return; }

    state.selectedFile = file;
    const fileSize = (file.size / 1024 / 1024).toFixed(2) + ' MB';
    const fileIcon = getFileIcon(file.type);

    const previewName = document.querySelector('#preview-file-name');
    const previewSize = document.querySelector('#preview-file-size');
    const previewIcon = document.querySelector('#preview-file-icon');

    if (previewName) previewName.textContent = file.name;
    if (previewSize) previewSize.textContent = fileSize;
    if (previewIcon) previewIcon.textContent = fileIcon;
    if (dom.filePreview) dom.filePreview.style.display = 'flex';

    uploadFile(file);
}

async function uploadFile(file) {
    const formData = new FormData();
    formData.append('file', file);
    const csrfMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    const headers = {};
    if (csrfHeaderMeta && csrfMeta) headers[csrfHeaderMeta.getAttribute('content')] = csrfMeta.getAttribute('content');

    try {
        const response = await fetch('/api/chat/upload', { method: 'POST', headers: headers, body: formData });
        if (!response.ok) throw new Error('Upload thất bại');
        const data = await response.json();
        state.uploadedFilePath = data.filePath;
    } catch (error) {
        console.error(error);
        alert('Lỗi upload file!');
        cancelFileUpload();
    }
}

export function cancelFileUpload() {
    state.selectedFile = null;
    state.uploadedFilePath = null;
    if (dom.fileInput) dom.fileInput.value = '';
    if (dom.filePreview) dom.filePreview.style.display = 'none';
}