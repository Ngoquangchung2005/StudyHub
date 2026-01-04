// messaging.js
import { state, dom, currentUser } from './state.js';
import { getAvatarHtml, getFileIcon, scrollToBottom } from './utils.js';
import { cancelFileUpload } from './upload.js';

export function onMessageSubmit(event) {
    event.preventDefault();
    const messageContent = dom.messageInput.value.trim();
    if (!messageContent && !state.uploadedFilePath) return;

    if (state.stompClient && state.currentRoomId && state.isConnected) {
        const sendMessageDto = {
            roomId: state.currentRoomId,
            content: messageContent || '',
            type: state.uploadedFilePath ? (state.selectedFile.type.startsWith('image/') ? 'IMAGE' : 'FILE') : 'TEXT',
            filePath: state.uploadedFilePath,
            fileName: state.selectedFile ? state.selectedFile.name : null,
            fileSize: state.selectedFile ? state.selectedFile.size : null,
            mimeType: state.selectedFile ? state.selectedFile.type : null
        };

        state.stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(sendMessageDto));

        dom.messageInput.value = '';
        cancelFileUpload();
        sendTypingEvent(false);
        scrollToBottom(true);
    }
}

export function onMessageReceived(payload) {
    const messageDto = JSON.parse(payload.body);
    if (state.currentRoomId && String(messageDto.roomId) === String(state.currentRoomId)) {
        const existingElement = document.querySelector(`.msg-row[data-message-id="${messageDto.id}"]`);

        if (existingElement) {
            if (messageDto.isRecalled) {
                const contentDiv = existingElement.querySelector('.msg-content');
                if (contentDiv) {
                    contentDiv.className = 'msg-content recalled';
                    contentDiv.innerHTML = 'Tin nhắn đã được thu hồi';
                    contentDiv.removeAttribute('style');
                    contentDiv.removeAttribute('title');
                }
                const actions = existingElement.querySelector('.msg-actions');
                if(actions) actions.remove();
            }
        } else {
            displayMessage(messageDto);
            const isMyMessage = String(messageDto.senderId) === String(currentUser.id);
            scrollToBottom(isMyMessage);
        }
    }
}

export function displayMessage(messageDto) {
    const messageRow = document.createElement('div');
    messageRow.classList.add('msg-row');
    messageRow.setAttribute('data-message-id', messageDto.id);

    const isSent = String(messageDto.senderId) === String(currentUser.id);
    messageRow.classList.add(isSent ? 'sent' : 'received');

    let contentHtml = '';
    if (messageDto.isRecalled) {
        contentHtml = `<div class="msg-content recalled">Tin nhắn đã được thu hồi</div>`;
    } else {
        let innerContent = '';
        if (messageDto.type === 'IMAGE') {
            innerContent = `<img src="/view-file/${messageDto.filePath}" class="msg-image" onclick="window.open(this.src)" title="Xem ảnh gốc">`;
        } else if (messageDto.type === 'FILE') {
            const fileSizeMB = messageDto.fileSize ? (messageDto.fileSize / 1024 / 1024).toFixed(2) + ' MB' : '';
            innerContent = `
                <div class="msg-file">
                    <span style="font-size: 24px;">${getFileIcon(messageDto.mimeType || '')}</span>
                    <div class="ms-2">
                        <div style="font-weight:600; font-size: 14px;">${messageDto.fileName}</div>
                        <div style="font-size: 11px; opacity: 0.8;">${fileSizeMB}</div>
                    </div>
                    <a href="/download/${messageDto.filePath}" target="_blank" class="ms-auto text-dark">⬇</a>
                </div>`;
        } else {
            if (messageDto.content.includes("đã rời khỏi nhóm") || messageDto.content.includes("đã thêm") || messageDto.content.includes("đã mời")) {
                innerContent = `<em class="text-muted small">${messageDto.content}</em>`;
            } else {
                innerContent = messageDto.content;
            }
        }
        if (messageDto.content && messageDto.type !== 'TEXT' && !innerContent.includes("em class")) {
            innerContent += `<div class="mt-1 small">${messageDto.content}</div>`;
        }
        let formattedTime = '';
        try { formattedTime = new Date(messageDto.timestamp).toLocaleTimeString('vi-VN', {hour: '2-digit', minute:'2-digit'}); } catch(e){}

        if (innerContent.includes("em class")) {
            contentHtml = `<div class="msg-content" style="background: #f8f9fa; color: #555; border: 1px solid #eee; box-shadow:none;" title="${formattedTime}">${innerContent}</div>`;
        } else {
            contentHtml = `<div class="msg-content" title="${formattedTime}">${innerContent}</div>`;
        }
    }

    let avatarHtml = '';
    if (!isSent) {
        avatarHtml = getAvatarHtml(messageDto.senderAvatarUrl, messageDto.senderName, 'msg-avatar-small');
    }

    let actionsHtml = '';
    if (isSent && !messageDto.isRecalled) {
        actionsHtml = `<div class="msg-actions"><button type="button" class="btn-option">⋮</button><div class="action-popup"><div class="action-item btn-confirm-recall">Thu hồi</div></div></div>`;
    }

    messageRow.innerHTML = `${avatarHtml}${contentHtml}${actionsHtml}`;

    if (isSent && !messageDto.isRecalled) {
        const btnOption = messageRow.querySelector('.btn-option');
        const popup = messageRow.querySelector('.action-popup');
        const btnRecall = messageRow.querySelector('.btn-confirm-recall');

        if (btnOption) btnOption.addEventListener('click', (e) => {
            e.stopPropagation();
            document.querySelectorAll('.action-popup.show').forEach(el => { if(el !== popup) el.classList.remove('show'); });
            popup.classList.toggle('show');
        });

        if (btnRecall) btnRecall.addEventListener('click', (e) => {
            e.stopPropagation();
            recallMessage(messageDto.id);
            popup.classList.remove('show');
        });
    }

    dom.messageArea.appendChild(messageRow);
}

// Typing
export function onTypingInput() {
    sendTypingEvent(true);
    clearTimeout(state.typingTimeout);
    state.typingTimeout = setTimeout(() => sendTypingEvent(false), 3000);
}

function sendTypingEvent(isTyping) {
    if (state.stompClient && state.currentRoomId) {
        state.stompClient.send("/app/chat.typing", {}, JSON.stringify({ roomId: state.currentRoomId, isTyping: isTyping }));
    }
}

export function onTypingReceived(payload) {
    const typingDto = JSON.parse(payload.body);
    if (typingDto.username === currentUser.username) return;
    if (typingDto.isTyping) state.typingUsers.set(typingDto.username, new Date());
    else state.typingUsers.delete(typingDto.username);
    updateTypingIndicator();
}

function updateTypingIndicator() {
    const now = new Date();
    state.typingUsers.forEach((time, username) => { if (now - time > 5000) state.typingUsers.delete(username); });
    const names = Array.from(state.typingUsers.keys());
    if (names.length === 0) dom.typingIndicator.textContent = "";
    else if (names.length === 1) dom.typingIndicator.textContent = `${names[0]} đang gõ...`;
    else dom.typingIndicator.textContent = "Nhiều người đang gõ...";
}

// Recall
export function recallMessage(messageId) {
    state.messageIdToRecall = messageId;
    const modalElement = document.getElementById('recallConfirmationModal');
    const modal = new bootstrap.Modal(modalElement);
    modal.show();
}

export function executeRecall() {
    if (!state.messageIdToRecall) return;
    if (state.stompClient && state.currentRoomId) {
        state.stompClient.send("/app/chat.recallMessage", {}, JSON.stringify({ messageId: state.messageIdToRecall, roomId: state.currentRoomId }));
    }
    const modalElement = document.getElementById('recallConfirmationModal');
    const modal = bootstrap.Modal.getInstance(modalElement);
    modal.hide();
    state.messageIdToRecall = null;
}