'use strict';

// --- Biến toàn cục ---
const messageForm = document.querySelector('#messageForm');
const messageInput = document.querySelector('#message');
const messageSendBtn = messageForm.querySelector('button[type="submit"]');
const messageArea = document.querySelector('#chat-messages-window');
const chatRoomList = document.querySelector('#chat-room-list');
const chatMainWindow = document.querySelector('#chat-main-window');
const chatWelcomeScreen = document.querySelector('#chat-welcome-screen');
const chatMainHeader = document.querySelector('#chat-main-header');
const typingIndicator = document.querySelector('#typing-indicator-area');
const newChatBtn = document.querySelector('#new-chat-btn');
const newUserChatList = document.querySelector('#new-chat-user-list');

const fileInput = document.querySelector('#file-input');
const fileBtn = document.querySelector('#file-btn');
const imageBtn = document.querySelector('#image-btn');
const filePreview = document.querySelector('#file-preview');
const cancelFileBtn = document.querySelector('#cancel-file-btn');

const currentUserId = document.querySelector('#current-user-id').value;
const currentUsername = document.querySelector('#current-username').value;
let stompClient = null;
let currentRoomId = null;
let subscriptions = new Map();
let typingTimeout = null;
let presenceStatus = new Map();
let typingUsers = new Map();

let selectedFile = null;
let uploadedFilePath = null;

// ===========================================
// === KẾT NỐI VÀ KHỞI TẠO
// ===========================================
function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, onConnected, onError);
}

async function onConnected() {
    stompClient.subscribe('/topic/presence', onPresenceMessageReceived);
    stompClient.subscribe(`/user/${currentUsername}/queue/notify`, onNotificationReceived);

    try {
        const response = await fetch('/api/chat/online-users');
        const onlineUsernames = await response.json();
        onlineUsernames.forEach(username => {
            presenceStatus.set(username, "ONLINE");
        });
    } catch (error) {
        console.error("Không thể tải danh sách online:", error);
    }

    loadChatRooms();
    messageForm.addEventListener('submit', onMessageSubmit, true);
    messageInput.addEventListener('input', onTypingInput);
    newChatBtn.addEventListener('click', loadUsersForNewChat);

    fileBtn.addEventListener('click', () => {
        fileInput.setAttribute('accept', '*/*');
        fileInput.click();
    });

    imageBtn.addEventListener('click', () => {
        fileInput.setAttribute('accept', 'image/*');
        fileInput.click();
    });

    fileInput.addEventListener('change', handleFileSelect);
    cancelFileBtn.addEventListener('click', cancelFileUpload);

    checkUrlForRedirect();
}

function onError(error) {
    console.error('Không thể kết nối WebSocket: ' + error);
}

async function checkUrlForRedirect() {
    const urlParams = new URLSearchParams(window.location.search);
    const userIdToChat = urlParams.get('withUser');

    if (userIdToChat) {
        try {
            const response = await fetch(`/api/chat/room/with/${userIdToChat}`);
            if (!response.ok) throw new Error('Không thể tạo phòng chat từ redirect');
            const roomDto = await response.json();
            await loadChatRooms();
            selectRoom(roomDto.id, roomDto.oneToOnePartnerName);
            history.replaceState(null, '', window.location.pathname);
        } catch (error) {
            console.error(error);
            history.replaceState(null, '', window.location.pathname);
        }
    }
}

// ===========================================
// === XỬ LÝ UPLOAD FILE
// ===========================================
function handleFileSelect(event) {
    const file = event.target.files[0];
    if (!file) return;

    if (file.size > 50 * 1024 * 1024) {
        alert('File quá lớn! Tối đa 50MB.');
        return;
    }

    selectedFile = file;

    const fileName = file.name;
    const fileSize = (file.size / 1024 / 1024).toFixed(2) + ' MB';
    const fileIcon = getFileIcon(file.type);

    document.querySelector('#preview-file-name').textContent = fileName;
    document.querySelector('#preview-file-size').textContent = fileSize;
    document.querySelector('#preview-file-icon').textContent = fileIcon;

    filePreview.style.display = 'flex';

    uploadFile(file);
}

function getFileIcon(mimeType) {
    if (mimeType.startsWith('image/')) return '🖼️';
    if (mimeType.includes('pdf')) return '📄';
    if (mimeType.includes('word')) return '📝';
    if (mimeType.includes('excel') || mimeType.includes('spreadsheet')) return '📊';
    if (mimeType.includes('presentation')) return '📽️';
    if (mimeType.includes('zip') || mimeType.includes('rar')) return '📦';
    return '📁';
}

async function uploadFile(file) {
    const formData = new FormData();
    formData.append('file', file);

    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    try {
        const response = await fetch('/api/chat/upload', {
            method: 'POST',
            headers: {
                [csrfHeader]: csrfToken
            },
            body: formData
        });

        if (!response.ok) throw new Error('Upload thất bại');

        const data = await response.json();
        uploadedFilePath = data.filePath;

    } catch (error) {
        console.error('Lỗi upload:', error);
        alert('Lỗi upload file! Vui lòng thử lại.');
        cancelFileUpload();
    }
}

function cancelFileUpload() {
    selectedFile = null;
    uploadedFilePath = null;
    fileInput.value = '';
    filePreview.style.display = 'none';
}

// ===========================================
// === GỬI TIN NHẮN
// ===========================================
function onMessageSubmit(event) {
    event.preventDefault();

    const messageContent = messageInput.value.trim();

    if (!messageContent && !uploadedFilePath) {
        return;
    }

    if (stompClient && currentRoomId) {
        const sendMessageDto = {
            roomId: currentRoomId,
            content: messageContent || '',
            type: uploadedFilePath ? (selectedFile.type.startsWith('image/') ? 'IMAGE' : 'FILE') : 'TEXT',
            filePath: uploadedFilePath,
            fileName: selectedFile ? selectedFile.name : null,
            fileSize: selectedFile ? selectedFile.size : null,
            mimeType: selectedFile ? selectedFile.type : null
        };

        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(sendMessageDto));

        messageInput.value = '';
        cancelFileUpload();
        sendTypingEvent(false);
    }
}

// ===========================================
// === THU HỒI TIN NHẮN
// ===========================================
function recallMessage(messageId) {
    if (!confirm('Bạn có chắc muốn thu hồi tin nhắn này?')) return;

    updateMessageToRecalled(messageId);

    if (stompClient && currentRoomId) {
        const recallDto = {
            messageId: messageId,
            roomId: currentRoomId
        };
        stompClient.send("/app/chat.recallMessage", {}, JSON.stringify(recallDto));
    }
}

function updateMessageToRecalled(messageId) {
    const msgContainer = document.querySelector(`.msg-container[data-message-id="${messageId}"]`);

    if (msgContainer) {
        const bubble = msgContainer.querySelector('.msg-bubble');
        bubble.innerHTML = '<em>Tin nhắn đã được thu hồi</em>';
        bubble.className = 'msg-bubble recalled';
        bubble.removeAttribute('style');

        const actions = msgContainer.querySelector('.msg-actions');
        if (actions) {
            actions.remove();
        }
    }
}

// ===========================================
// === HIỂN THỊ TIN NHẮN
// ===========================================
function displayMessage(messageDto) {
    const messageContainer = document.createElement('div');
    messageContainer.classList.add('msg-container');
    messageContainer.setAttribute('data-message-id', messageDto.id);

    const type = String(messageDto.senderId) === String(currentUserId) ? 'sent' : 'received';
    messageContainer.classList.add(type);

    let formattedTime = '';
    try {
        const time = new Date(messageDto.timestamp);
        formattedTime = time.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    } catch (e) {}

    let bubbleContent = '';
    if (messageDto.isRecalled) {
        messageContainer.innerHTML = `
            <div class="msg-bubble recalled"><em>Tin nhắn đã được thu hồi</em></div>
            <div class="msg-time">${formattedTime}</div>
        `;
        messageArea.appendChild(messageContainer);
        return;
    }

    if (messageDto.type === 'IMAGE') {
        bubbleContent = `<img src="/view-file/${messageDto.filePath}" style="max-width: 300px; border-radius: 10px; cursor: pointer;" onclick="window.open('/view-file/${messageDto.filePath}')">`;
    } else if (messageDto.type === 'FILE') {
        const fileSize = messageDto.fileSize ? (messageDto.fileSize / 1024 / 1024).toFixed(2) + ' MB' : '';
        bubbleContent = `
            <div class="file-message">
                <span style="font-size: 2rem;">${getFileIcon(messageDto.mimeType || '')}</span>
                <div class="ms-2"><strong>${messageDto.fileName}</strong><br><small>${fileSize}</small></div>
                <a href="/download/${messageDto.filePath}" class="btn btn-sm btn-primary ms-2">Tải</a>
            </div>
        `;
    } else {
        bubbleContent = messageDto.content;
    }

    if (messageDto.content && messageDto.type !== 'TEXT') {
        bubbleContent += `<p class="mt-2 mb-0">${messageDto.content}</p>`;
    }

    let actionHtml = '';
    if (type === 'sent') {
        actionHtml = `
            <div class="msg-actions">
                <button type="button" class="btn-option">⋯</button>
                <div class="action-popup">
                    <div class="action-item btn-confirm-recall">Thu hồi</div>
                </div>
            </div>
        `;
    }

    messageContainer.innerHTML = `
        <div class="msg-content">
            ${actionHtml}
            <div class="msg-bubble">${bubbleContent}</div>
        </div>
        <div class="msg-time">${formattedTime}</div>
    `;

    if (type === 'sent') {
        const btnOption = messageContainer.querySelector('.btn-option');
        const popup = messageContainer.querySelector('.action-popup');
        const btnRecall = messageContainer.querySelector('.btn-confirm-recall');

        btnOption.addEventListener('click', (e) => {
            e.stopPropagation();
            document.querySelectorAll('.action-popup.show').forEach(el => { if(el !== popup) el.classList.remove('show'); });
            popup.classList.toggle('show');
        });

        btnRecall.addEventListener('click', (e) => {
            e.stopPropagation();
            recallMessage(messageDto.id);
            popup.classList.remove('show');
        });
    }

    messageArea.appendChild(messageContainer);
}

document.addEventListener('click', () => {
    document.querySelectorAll('.action-popup.show').forEach(el => el.classList.remove('show'));
});

// ===========================================
// === NHẬN TIN NHẮN TỪ WEBSOCKET
// ===========================================
function onMessageReceived(payload) {
    const messageDto = JSON.parse(payload.body);

    if (currentRoomId && messageDto.roomId == currentRoomId) {
        const existingElement = document.querySelector(`.msg-container[data-message-id="${messageDto.id}"]`);

        if (existingElement) {
            if (messageDto.isRecalled) {
                updateMessageToRecalled(messageDto.id);
            }
        } else {
            displayMessage(messageDto);
            scrollToBottom();
        }
    }
}

// ===========================================
// === QUẢN LÝ PHÒNG CHAT
// ===========================================
async function loadChatRooms() {
    try {
        const response = await fetch('/api/chat/rooms');
        if (!response.ok) throw new Error('Không thể tải phòng chat');
        const rooms = await response.json();

        chatRoomList.innerHTML = '';
        rooms.forEach(room => {
            const roomName = room.type === 'ONE_TO_ONE' ? room.oneToOnePartnerName : room.name;
            const avatarChar = roomName.charAt(0).toUpperCase();

            const partner = room.members.find(m => m.id != currentUserId);
            const partnerUsername = partner ? partner.username : '';
            const status = (partner && presenceStatus.get(partnerUsername) === 'ONLINE') ? 'online' : '';
            const statusText = status ? 'Online' : 'Offline';

            const roomElement = document.createElement('a');
            roomElement.href = '#';
            roomElement.classList.add('user-list-item');
            roomElement.setAttribute('data-room-id', room.id);
            roomElement.setAttribute('data-room-name', roomName);

            roomElement.innerHTML = `
                <div class="user-avatar">${avatarChar}</div>
                <div class="user-info" data-username="${partnerUsername}">
                    <span class="user-name">${roomName}</span>
                    <span class="user-status-text">
                        <span class="status-dot ${status}"></span>
                        <span class="status-text">${statusText}</span>
                    </span>
                </div>
            `;
            roomElement.addEventListener('click', onRoomSelected);
            chatRoomList.appendChild(roomElement);
        });
    } catch (error) {
        console.error(error);
        chatRoomList.innerHTML = '<p class="text-danger p-3">Lỗi tải phòng chat.</p>';
    }
}

async function loadUsersForNewChat() {
    try {
        newUserChatList.innerHTML = '<p>Đang tải danh sách...</p>';
        const response = await fetch('/api/chat/users');
        if (!response.ok) throw new Error('Không thể tải danh sách user');
        const users = await response.json();

        newUserChatList.innerHTML = '';
        users.forEach(user => {
            const status = presenceStatus.get(user.username) === 'ONLINE' ? 'online' : '';
            const statusText = status ? 'Online' : 'Offline';
            const userElement = document.createElement('a');
            userElement.href = '#';
            userElement.classList.add('user-list-item');
            userElement.setAttribute('data-user-id', user.id);
            userElement.innerHTML = `
                <div class="user-avatar">${user.name.charAt(0).toUpperCase()}</div>
                <div class="user-info" data-username="${user.username}">
                    <span class="user-name">${user.name}</span>
                    <span class="user-status-text">
                        <span class="status-dot ${status}"></span>
                        <span class="status-text">${statusText}</span>
                    </span>
                </div>
            `;
            userElement.addEventListener('click', onStartNewChat);
            newUserChatList.appendChild(userElement);
        });
    } catch (error) {
        console.error(error);
        newUserChatList.innerHTML = '<p class="text-danger">Lỗi tải danh sách.</p>';
    }
}

async function onStartNewChat(event) {
    event.preventDefault();
    const otherUserId = event.currentTarget.getAttribute('data-user-id');
    try {
        const response = await fetch(`/api/chat/room/with/${otherUserId}`);
        if (!response.ok) throw new Error('Không thể tạo phòng chat');
        const roomDto = await response.json();

        const modalEl = document.querySelector('#newUserChatModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();

        await loadChatRooms();
        selectRoom(roomDto.id, roomDto.oneToOnePartnerName);
    } catch (error) {
        console.error(error);
    }
}

function onRoomSelected(event) {
    event.preventDefault();
    const roomId = event.currentTarget.getAttribute('data-room-id');
    const roomName = event.currentTarget.getAttribute('data-room-name');
    selectRoom(roomId, roomName);
}

async function selectRoom(roomId, roomName) {
    if (currentRoomId === roomId) return;
    currentRoomId = roomId;

    subscriptions.forEach(sub => sub.unsubscribe());
    subscriptions.clear();

    chatWelcomeScreen.style.display = 'none';
    chatMainWindow.style.display = 'flex';
    messageInput.disabled = false;
    messageSendBtn.disabled = false;

    document.querySelectorAll('#chat-room-list .user-list-item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('data-room-id') === roomId) {
            item.classList.add('active');
        }
    });

    chatMainHeader.innerHTML = `
        <div class="user-avatar">${roomName.charAt(0).toUpperCase()}</div>
        <div class="ms-2">
            <h5 class="mb-0">${roomName}</h5>
        </div>
    `;

    typingUsers.clear();
    updateTypingIndicator();

    const msgSub = stompClient.subscribe(`/topic/room/${roomId}`, onMessageReceived);
    const typeSub = stompClient.subscribe(`/topic/room/${roomId}/typing`, onTypingReceived);
    subscriptions.set('messages', msgSub);
    subscriptions.set('typing', typeSub);

    messageArea.innerHTML = '<p class="text-center">Đang tải lịch sử...</p>';
    try {
        const response = await fetch(`/api/chat/room/${roomId}/messages`);
        if (!response.ok) throw new Error('Không thể tải lịch sử tin nhắn');

        const messages = await response.json();
        messageArea.innerHTML = '';
        messages.forEach(displayMessage);
        scrollToBottom();
    } catch (error) {
        console.error(error);
        messageArea.innerHTML = '<p class="text-danger p-3">Lỗi tải lịch sử chat.</p>';
    }
}

// ===========================================
// === XỬ LÝ TYPING INDICATOR
// ===========================================
function onTypingInput() {
    sendTypingEvent(true);
    clearTimeout(typingTimeout);
    typingTimeout = setTimeout(() => {
        sendTypingEvent(false);
    }, 3000);
}

function sendTypingEvent(isTyping) {
    if (stompClient && currentRoomId) {
        const typingDto = {
            roomId: currentRoomId,
            isTyping: isTyping
        };
        stompClient.send("/app/chat.typing", {}, JSON.stringify(typingDto));
    }
}

function onTypingReceived(payload) {
    const typingDto = JSON.parse(payload.body);
    if (typingDto.username === currentUsername) return;

    if (typingDto.isTyping) {
        typingUsers.set(typingDto.username, new Date());
    } else {
        typingUsers.delete(typingDto.username);
    }
    updateTypingIndicator();
}

function updateTypingIndicator() {
    const now = new Date();
    typingUsers.forEach((time, username) => {
        if (now - time > 5000) {
            typingUsers.delete(username);
        }
    });

    const names = Array.from(typingUsers.keys());
    if (names.length === 0) {
        typingIndicator.textContent = "";
    } else if (names.length === 1) {
        typingIndicator.textContent = `${names[0]} đang gõ...`;
    } else if (names.length === 2) {
        typingIndicator.textContent = `${names.join(' và ')} đang gõ...`;
    } else {
        typingIndicator.textContent = "Nhiều người đang gõ...";
    }
}

// ===========================================
// === XỬ LÝ PRESENCE
// ===========================================
function onPresenceMessageReceived(payload) {
    const presenceDto = JSON.parse(payload.body);
    presenceStatus.set(presenceDto.username, presenceDto.status);
    updateAllPresenceIndicators(presenceDto.username, presenceDto.status);
}

function updateAllPresenceIndicators(username, status) {
    const statusText = status === 'ONLINE' ? 'Online' : 'Offline';
    const statusClass = status === 'ONLINE' ? 'online' : '';

    const selector = `.user-info[data-username="${username}"]`;

    document.querySelectorAll(selector).forEach(userInfo => {
        const dot = userInfo.querySelector('.status-dot');
        const text = userInfo.querySelector('.status-text');

        if (dot) dot.className = `status-dot ${statusClass}`;
        if (text) text.textContent = statusText;
    });
}

function onNotificationReceived(payload) {
    if (payload.body === "NEW_ROOM") {
        loadChatRooms();
    }
}

// ===========================================
// === UTILITIES
// ===========================================
function scrollToBottom() {
    messageArea.scrollTop = messageArea.scrollHeight;
}

// ===========================================
// === KHỞI ĐỘNG
// ===========================================
if (document.querySelector('.chat-app-container')) {
    connect();
}