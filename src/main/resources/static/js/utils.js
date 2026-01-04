// utils.js
import { dom, currentUser } from './state.js';

export function getAvatarHtml(avatarUrl, name, sizeClass = 'user-avatar') {
    if (avatarUrl) {
        return `<img src="/view-file/${avatarUrl}" class="${sizeClass}" style="object-fit: cover; background: white;">`;
    } else {
        const initial = name ? name.charAt(0).toUpperCase() : '?';
        return `<div class="${sizeClass}">${initial}</div>`;
    }
}

export function getFileIcon(mimeType) {
    if (!mimeType) return '📁';
    if (mimeType.startsWith('image/')) return '🖼️';
    if (mimeType.includes('pdf')) return '📄';
    if (mimeType.includes('word')) return '📝';
    if (mimeType.includes('excel') || mimeType.includes('spreadsheet')) return '📊';
    return '📁';
}

export function scrollToBottom(force = false) {
    if (!dom.messageArea) return;

    const threshold = 150;
    const currentScroll = dom.messageArea.scrollTop + dom.messageArea.clientHeight;
    const maxScroll = dom.messageArea.scrollHeight;

    if (force || (maxScroll - currentScroll < threshold)) {
        dom.messageArea.scrollTop = dom.messageArea.scrollHeight;
        if (dom.newMessageAlertEl) dom.newMessageAlertEl.style.display = 'none';
    } else {
        if (dom.newMessageAlertEl) dom.newMessageAlertEl.style.display = 'block';
    }
}

export function toggleInputState(enable) {
    if(dom.messageSendBtn) dom.messageSendBtn.disabled = !enable;
    if(dom.messageInput) dom.messageInput.disabled = !enable;
    if(dom.fileBtn) dom.fileBtn.disabled = !enable;
    if(dom.imageBtn) dom.imageBtn.disabled = !enable;
}