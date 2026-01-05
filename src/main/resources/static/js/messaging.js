// messaging.js
import { state, dom, currentUser } from './state.js';
import { getAvatarHtml, getFileIcon, scrollToBottom } from './utils.js';
import { cancelFileUpload } from './upload.js';
import { EmojiButton } from 'https://cdn.jsdelivr.net/npm/@joeattardi/emoji-button@4.6.4/dist/index.min.js';

// --- BIẾN TOÀN CỤC CHO GHI ÂM ---
let mediaRecorder = null;
let audioChunks = [];
let recordingInterval = null;
let recordingStartTime = null;

// --- KHỞI TẠO ---
export function initMessagingFeatures() {
    initEmojiPicker();
    initVoiceRecording();
}

// 1. CHỨC NĂNG EMOJI
function initEmojiPicker() {
    try {
        const picker = new EmojiButton({
            position: 'top-start',
            zIndex: 1000,
            autoHide: false
        });
        const trigger = document.getElementById('emoji-btn');

        if (trigger) {
            picker.on('emoji', selection => {
                const input = document.getElementById('message');
                input.value += selection.emoji;
                input.focus();
                input.dispatchEvent(new Event('input'));
            });

            trigger.addEventListener('click', () => picker.togglePicker(trigger));
        }
    } catch (e) {
        console.error("Lỗi khởi tạo Emoji Picker:", e);
    }
}

// 2. CHỨC NĂNG GHI ÂM
function initVoiceRecording() {
    const micBtn = document.getElementById('mic-btn');
    const cancelBtn = document.getElementById('btn-cancel-record');
    const sendRecordBtn = document.getElementById('btn-send-record');

    if (micBtn) micBtn.addEventListener('click', startRecording);
    if (cancelBtn) cancelBtn.addEventListener('click', cancelRecording);
    if (sendRecordBtn) sendRecordBtn.addEventListener('click', stopAndSendRecording);
}

async function startRecording() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        mediaRecorder = new MediaRecorder(stream);
        audioChunks = [];
        mediaRecorder.ondataavailable = event => { audioChunks.push(event.data); };
        mediaRecorder.start();
        toggleRecordingUI(true);
        startTimer();
    } catch (error) {
        alert("Không thể truy cập microphone. Vui lòng cấp quyền.");
        console.error(error);
    }
}

function cancelRecording() {
    if (mediaRecorder) {
        mediaRecorder.stop();
        mediaRecorder.stream.getTracks().forEach(track => track.stop());
    }
    toggleRecordingUI(false);
    stopTimer();
    audioChunks = [];
}

function stopAndSendRecording() {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.onstop = async () => {
            const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
            const audioFile = new File([audioBlob], "voice-message.webm", { type: 'audio/webm' });
            await uploadAndSendAudio(audioFile);
            mediaRecorder.stream.getTracks().forEach(track => track.stop());
            toggleRecordingUI(false);
            stopTimer();
        };
        mediaRecorder.stop();
    }
}

function toggleRecordingUI(isRecording) {
    const recordingUI = document.getElementById('recording-ui');
    const messageForm = document.getElementById('messageForm');
    if (isRecording) {
        recordingUI.style.display = 'flex';
        messageForm.style.display = 'none';
    } else {
        recordingUI.style.display = 'none';
        messageForm.style.display = 'flex';
    }
}

function startTimer() {
    const timerElement = document.getElementById('recording-timer');
    recordingStartTime = Date.now();
    recordingInterval = setInterval(() => {
        const elapsed = Math.floor((Date.now() - recordingStartTime) / 1000);
        const minutes = Math.floor(elapsed / 60).toString().padStart(2, '0');
        const seconds = (elapsed % 60).toString().padStart(2, '0');
        timerElement.textContent = `${minutes}:${seconds}`;
    }, 1000);
}

function stopTimer() {
    clearInterval(recordingInterval);
    document.getElementById('recording-timer').textContent = "00:00";
}

async function uploadAndSendAudio(file) {
    const formData = new FormData();
    formData.append('file', file);
    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    try {
        const response = await fetch('/api/chat/upload', {
            method: 'POST',
            headers: { [csrfHeader]: csrfToken },
            body: formData
        });
        if (response.ok) {
            const data = await response.json();
            sendWebSocketMessage(null, 'AUDIO', data);
        } else {
            console.error("Lỗi upload audio:", response.status);
            alert('Lỗi khi gửi ghi âm.');
        }
    } catch (error) { console.error("Upload error:", error); }
}

function sendWebSocketMessage(content, type, fileData = null) {
    if (state.stompClient && state.currentRoomId && state.isConnected) {
        const sendMessageDto = {
            roomId: state.currentRoomId,
            content: content || '',
            type: type,
            filePath: fileData ? fileData.filePath : null,
            fileName: fileData ? fileData.fileName : null,
            fileSize: fileData ? fileData.fileSize : null,
            mimeType: fileData ? fileData.mimeType : null
        };
        state.stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(sendMessageDto));
        scrollToBottom(true);
    }
}

export function onMessageSubmit(event) {
    event.preventDefault();
    const messageContent = dom.messageInput.value.trim();

    if (state.uploadedFilePath) {
        const type = state.selectedFile.type.startsWith('image/') ? 'IMAGE' : 'FILE';
        const fileData = {
            filePath: state.uploadedFilePath,
            fileName: state.selectedFile.name,
            fileSize: state.selectedFile.size,
            mimeType: state.selectedFile.type
        };
        sendWebSocketMessage(messageContent, type, fileData);
    } else if (messageContent) {
        sendWebSocketMessage(messageContent, 'TEXT', null);
    }

    dom.messageInput.value = '';
    cancelFileUpload();
    sendTypingEvent(false);
    if(window.toggleSendButton) window.toggleSendButton();
}

// --- XỬ LÝ TIN NHẮN NHẬN ĐƯỢC ---
export function onMessageReceived(payload) {
    const messageDto = JSON.parse(payload.body);
    const incomingRoomId = String(messageDto.roomId);
    const currentRoomId = state.currentRoomId ? String(state.currentRoomId) : null;

    if (currentRoomId === incomingRoomId) {
        // Đang xem phòng này -> hiển thị tin nhắn
        const existingElement = document.querySelector(`.msg-row[data-message-id="${messageDto.id}"]`);
        if (existingElement) {
            if (messageDto.isRecalled) {
                const contentDiv = existingElement.querySelector('.msg-content');
                if (contentDiv) {
                    contentDiv.className = 'msg-content recalled';
                    contentDiv.innerHTML = 'Tin nhắn đã được thu hồi';
                }
                const actions = existingElement.querySelector('.msg-actions');
                if(actions) actions.remove();
            }
        } else {
            displayMessage(messageDto);
            const isMyMessage = String(messageDto.senderId) === String(currentUser.id);
            scrollToBottom(isMyMessage);
        }
    } else {
        // Đang ở phòng khác -> Hiện dấu chấm đỏ bên cạnh tên
        showUnreadDot(incomingRoomId);
    }

    // [ĐÃ XÓA] Không gọi updateSidebarPreview nữa để giữ nguyên Online/Offline
}

// Hiện dấu chấm đỏ (unread) cho 1 room + badge ở navbar
// Export để socket.js có thể gọi khi nhận được sự kiện /user/queue/chat.
export function showUnreadDot(roomId) {
    // Lưu trạng thái unread để không bị mất khi sidebar bị re-render (ví dụ do realtime events)
    try {
        state.unreadRooms.add(String(roomId));
    } catch (e) {}

    const navBadge = document.getElementById('nav-chat-badge');
    if (navBadge) {
        navBadge.style.display = 'block';
        playNotificationSound();
    }

    // Ở đây cần ID mà chúng ta vừa thêm vào rooms.js
    const roomElement = document.getElementById(`room-item-${roomId}`);
    if (roomElement) {
        const nameElement = roomElement.querySelector('.user-name');
        if (nameElement && !nameElement.querySelector('.unread-dot')) {
            const dot = document.createElement('span');
            dot.className = 'unread-dot';
            nameElement.appendChild(dot);
        }
        // Đẩy lên đầu danh sách
        const parentList = roomElement.parentNode;
        if(parentList) parentList.prepend(roomElement);
    }
}
function playNotificationSound() {
    // Project only ships notification.wav
    const audio = new Audio('/sounds/notification.wav');
    audio.play().catch(e => {});
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
        } else if (messageDto.type === 'AUDIO') {
            innerContent = `<div class="msg-audio"><audio controls controlsList="nodownload"><source src="/view-file/${messageDto.filePath}" type="${messageDto.mimeType || 'audio/webm'}"></audio></div>`;
        } else if (messageDto.type === 'FILE') {
            const fileSizeMB = messageDto.fileSize ? (messageDto.fileSize / 1024 / 1024).toFixed(2) + ' MB' : '';
            innerContent = `<div class="msg-file"><span style="font-size: 24px;">${getFileIcon(messageDto.mimeType || '')}</span><div class="ms-2"><div style="font-weight:600; font-size: 14px;">${messageDto.fileName}</div><div style="font-size: 11px; opacity: 0.8;">${fileSizeMB}</div></div><a href="/download/${messageDto.filePath}" target="_blank" class="ms-auto text-dark">⬇</a></div>`;
        } else {
            if (messageDto.content.includes("đã rời khỏi nhóm") || messageDto.content.includes("đã thêm") || messageDto.content.includes("đã mời")) {
                innerContent = `<em class="text-muted small">${messageDto.content}</em>`;
            } else {
                innerContent = messageDto.content;
            }
        }
        if (messageDto.content && messageDto.type !== 'TEXT' && !innerContent.includes("em class") && messageDto.type !== 'AUDIO') {
            innerContent += `<div class="mt-1 small">${messageDto.content}</div>`;
        }
        let formattedTime = '';
        try { formattedTime = new Date(messageDto.timestamp).toLocaleTimeString('vi-VN', {hour: '2-digit', minute:'2-digit'}); } catch(e){}
        contentHtml = `<div class="msg-content" title="${formattedTime}">${innerContent}</div>`;
    }

    let avatarHtml = '';
    if (!isSent) avatarHtml = getAvatarHtml(messageDto.senderAvatarUrl, messageDto.senderName, 'msg-avatar-small');
    let actionsHtml = '';
    if (isSent && !messageDto.isRecalled) actionsHtml = `<div class="msg-actions"><button type="button" class="btn-option">⋮</button><div class="action-popup"><div class="action-item btn-confirm-recall">Thu hồi</div></div></div>`;
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