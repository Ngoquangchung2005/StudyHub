'use strict';

// --- Biến toàn cục ---
const messageForm = document.querySelector('#messageForm');
const messageInput = document.querySelector('#message');
const messageSendBtn = messageForm ? messageForm.querySelector('button[type="submit"]') : null;
const messageArea = document.querySelector('#chat-messages-window');
const chatRoomList = document.querySelector('#chat-room-list');
const chatMainWindow = document.querySelector('#chat-main-window');
const chatWelcomeScreen = document.querySelector('#chat-welcome-screen');
const chatMainHeader = document.querySelector('#chat-main-header');
const typingIndicator = document.querySelector('#typing-indicator-area');
const newChatBtn = document.querySelector('#new-chat-btn');
const newUserChatList = document.querySelector('#new-chat-user-list');

// === CÁC ELEMENT CHO UPLOAD FILE ===
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

// === BIẾN CHO UPLOAD FILE ===
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
    console.log('Đã kết nối WebSocket!');

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

    if (messageForm) messageForm.addEventListener('submit', onMessageSubmit, true);
    if (messageInput) messageInput.addEventListener('input', onTypingInput);
    if (newChatBtn) newChatBtn.addEventListener('click', loadUsersForNewChat);
    // === THÊM: Sự kiện cho nút "Tạo nhóm" và "Xác nhận tạo nhóm" ===
    const newGroupBtn = document.querySelector('#new-group-btn');
    const confirmGroupBtn = document.querySelector('#confirm-create-group-btn');
    const groupSearchInput = document.querySelector('#search-user-group');

    if (newGroupBtn) {
        newGroupBtn.addEventListener('click', loadUsersForGroupCreation);
    }
    if (confirmGroupBtn) {
        confirmGroupBtn.addEventListener('click', handleCreateGroup);
    }
    if (groupSearchInput) {
        // Tìm kiếm user trong modal nhóm
        groupSearchInput.addEventListener('input', function(e) {
            filterGroupUserList(e.target.value);
        });
    }

    // Sự kiện upload file
    if (fileBtn) fileBtn.addEventListener('click', () => { fileInput.setAttribute('accept', '*/*'); fileInput.click(); });
    if (imageBtn) imageBtn.addEventListener('click', () => { fileInput.setAttribute('accept', 'image/*'); fileInput.click(); });
    if (fileInput) fileInput.addEventListener('change', handleFileSelect);
    if (cancelFileBtn) cancelFileBtn.addEventListener('click', cancelFileUpload);

    document.addEventListener('click', () => {
        document.querySelectorAll('.action-popup.show').forEach(el => el.classList.remove('show'));
    });

    checkUrlForRedirect();
}

function onError(error) {
    console.error('Không thể kết nối WebSocket: ' + error);
}

// ===========================================
// === HÀM HỖ TRỢ RENDER AVATAR ===
// ===========================================
function getAvatarHtml(avatarUrl, name, sizeClass = 'user-avatar') {
    if (avatarUrl) {
        return `<img src="/view-file/${avatarUrl}" class="${sizeClass}" style="object-fit: cover; background: white;">`;
    } else {
        const initial = name ? name.charAt(0).toUpperCase() : '?';
        return `<div class="${sizeClass}">${initial}</div>`;
    }
}

// ===========================================
// === XỬ LÝ SIDEBAR (DANH SÁCH CHAT) ===
// ===========================================
async function loadChatRooms() {
    try {
        const response = await fetch('/api/chat/rooms');
        if (!response.ok) throw new Error('Không thể tải phòng chat');
        const rooms = await response.json();

        chatRoomList.innerHTML = '';
        rooms.forEach(room => {
            const roomName = room.type === 'ONE_TO_ONE' ? room.oneToOnePartnerName : room.name;
            // Lấy link avatar từ DTO (Java đã gửi xuống trường này)
            const avatarUrl = room.type === 'ONE_TO_ONE' ? room.oneToOnePartnerAvatarUrl : null;

            const partner = room.members.find(m => m.id != currentUserId);
            const partnerUsername = partner ? partner.username : '';
            const status = (partner && presenceStatus.get(partnerUsername) === 'ONLINE') ? 'online' : '';
            const statusText = status ? 'Online' : 'Offline';

            const roomElement = document.createElement('a');
            roomElement.href = '#';
            roomElement.classList.add('user-list-item');
            roomElement.setAttribute('data-room-id', room.id);
            roomElement.setAttribute('data-room-name', roomName);
            // Lưu avatarUrl vào data attribute để dùng khi click
            if(avatarUrl) roomElement.setAttribute('data-avatar-url', avatarUrl);

            // Render Avatar (Ảnh hoặc Chữ)
            const avatarHtml = getAvatarHtml(avatarUrl, roomName, 'user-avatar');

            roomElement.innerHTML = `
                ${avatarHtml}
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

function onRoomSelected(event) {
    event.preventDefault();
    // Tìm thẻ a.user-list-item gần nhất (đề phòng click vào icon con)
    const target = event.currentTarget;
    const roomId = target.getAttribute('data-room-id');
    const roomName = target.getAttribute('data-room-name');
    const avatarUrl = target.getAttribute('data-avatar-url'); // Lấy URL avatar

    selectRoom(roomId, roomName, avatarUrl);
}

async function selectRoom(roomId, roomName, avatarUrl) {
    if (currentRoomId === roomId) return;
    currentRoomId = roomId;

    subscriptions.forEach(sub => sub.unsubscribe());
    subscriptions.clear();

    if (chatWelcomeScreen) chatWelcomeScreen.style.display = 'none';
    if (chatMainWindow) chatMainWindow.style.display = 'flex';
    if (messageInput) messageInput.disabled = false;
    if (messageSendBtn) messageSendBtn.disabled = false;

    // Cập nhật trạng thái active ở sidebar
    document.querySelectorAll('#chat-room-list .user-list-item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('data-room-id') === roomId) {
            item.classList.add('active');
        }
    });

    // === CẬP NHẬT HEADER VỚI AVATAR ĐÚNG ===
    if (chatMainHeader) {
        const avatarHtml = getAvatarHtml(avatarUrl, roomName, 'user-avatar');
        chatMainHeader.innerHTML = `
            ${avatarHtml}
            <div class="ms-2">
                <h5 class="mb-0 fw-bold">${roomName}</h5>
            </div>
        `;
    }

    typingUsers.clear();
    updateTypingIndicator();

    const msgSub = stompClient.subscribe(`/topic/room/${roomId}`, onMessageReceived);
    const typeSub = stompClient.subscribe(`/topic/room/${roomId}/typing`, onTypingReceived);
    subscriptions.set('messages', msgSub);
    subscriptions.set('typing', typeSub);

    messageArea.innerHTML = '<p class="text-center mt-3 text-muted">Đang tải lịch sử...</p>';
    try {
        const response = await fetch(`/api/chat/room/${roomId}/messages`);
        if (!response.ok) throw new Error('Không thể tải lịch sử tin nhắn');

        const messages = await response.json();
        messageArea.innerHTML = '';
        messages.forEach(displayMessage);
        scrollToBottom();
    } catch (error) {
        console.error(error);
        messageArea.innerHTML = '<p class="text-danger p-3 text-center">Lỗi tải lịch sử chat.</p>';
    }
}

async function checkUrlForRedirect() {
    const urlParams = new URLSearchParams(window.location.search);
    const userIdToChat = urlParams.get('withUser');

    if (userIdToChat) {
        try {
            const response = await fetch(`/api/chat/room/with/${userIdToChat}`);
            if (!response.ok) throw new Error('Error fetching room');
            const roomDto = await response.json();
            await loadChatRooms();
            // Truyền thêm avatarUrl vào selectRoom
            selectRoom(roomDto.id, roomDto.oneToOnePartnerName, roomDto.oneToOnePartnerAvatarUrl);
            history.replaceState(null, '', window.location.pathname);
        } catch (error) {
            console.error(error);
            history.replaceState(null, '', window.location.pathname);
        }
    }
}

// ===========================================
// === DANH SÁCH CHAT MỚI (MODAL) ===
// ===========================================
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

            // Render Avatar trong Modal
            const avatarHtml = getAvatarHtml(user.avatarUrl, user.name, 'user-avatar');

            const userElement = document.createElement('a');
            userElement.href = '#';
            userElement.classList.add('user-list-item');
            userElement.setAttribute('data-user-id', user.id);

            userElement.innerHTML = `
                ${avatarHtml}
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
        // Truyền avatarUrl khi bắt đầu chat mới
        selectRoom(roomDto.id, roomDto.oneToOnePartnerName, roomDto.oneToOnePartnerAvatarUrl);
    } catch (error) {
        console.error(error);
    }
}

// ===========================================
// === HIỂN THỊ TIN NHẮN (CẬP NHẬT AVATAR) ===
// ===========================================
function displayMessage(messageDto) {
    const messageRow = document.createElement('div');
    messageRow.classList.add('msg-row');
    messageRow.setAttribute('data-message-id', messageDto.id);

    const isSent = String(messageDto.senderId) === String(currentUserId);
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
                </div>
            `;
        } else {
            innerContent = messageDto.content;
        }

        if (messageDto.content && messageDto.type !== 'TEXT') {
            innerContent += `<div class="mt-1 small">${messageDto.content}</div>`;
        }

        let formattedTime = '';
        try { formattedTime = new Date(messageDto.timestamp).toLocaleTimeString('vi-VN', {hour: '2-digit', minute:'2-digit'}); } catch(e){}
        contentHtml = `<div class="msg-content" title="${formattedTime}">${innerContent}</div>`;
    }

    // Avatar tin nhắn (bên cạnh bong bóng chat)
    let avatarHtml = '';
    if (!isSent) {
        // Sử dụng hàm getAvatarHtml để hiển thị nhất quán
        avatarHtml = getAvatarHtml(messageDto.senderAvatarUrl, messageDto.senderName, 'msg-avatar-small');
    }

    let actionsHtml = '';
    if (isSent && !messageDto.isRecalled) {
        actionsHtml = `
            <div class="msg-actions">
                <button type="button" class="btn-option">⋮</button>
                <div class="action-popup">
                    <div class="action-item btn-confirm-recall">Thu hồi</div>
                </div>
            </div>
        `;
    }

    messageRow.innerHTML = `${avatarHtml}${contentHtml}${actionsHtml}`;

    if (isSent && !messageDto.isRecalled) {
        const btnOption = messageRow.querySelector('.btn-option');
        const popup = messageRow.querySelector('.action-popup');
        const btnRecall = messageRow.querySelector('.btn-confirm-recall');

        if (btnOption) {
            btnOption.addEventListener('click', (e) => {
                e.stopPropagation();
                document.querySelectorAll('.action-popup.show').forEach(el => { if(el !== popup) el.classList.remove('show'); });
                popup.classList.toggle('show');
            });
        }
        if (btnRecall) {
            btnRecall.addEventListener('click', (e) => {
                e.stopPropagation();
                recallMessage(messageDto.id);
                popup.classList.remove('show');
            });
        }
    }

    messageArea.appendChild(messageRow);
}

// ===========================================
// === UPLOAD FILE LOGIC ===
// ===========================================
function handleFileSelect(event) {
    const file = event.target.files[0];
    if (!file) return;
    if (file.size > 50 * 1024 * 1024) { alert('File quá lớn! Tối đa 50MB.'); return; }

    selectedFile = file;
    const fileName = file.name;
    const fileSize = (file.size / 1024 / 1024).toFixed(2) + ' MB';
    const fileIcon = getFileIcon(file.type);

    const previewName = document.querySelector('#preview-file-name');
    const previewSize = document.querySelector('#preview-file-size');
    const previewIcon = document.querySelector('#preview-file-icon');

    if (previewName) previewName.textContent = fileName;
    if (previewSize) previewSize.textContent = fileSize;
    if (previewIcon) previewIcon.textContent = fileIcon;
    if (filePreview) filePreview.style.display = 'flex';

    uploadFile(file);
}

function getFileIcon(mimeType) {
    if (mimeType.startsWith('image/')) return '🖼️';
    if (mimeType.includes('pdf')) return '📄';
    if (mimeType.includes('word')) return '📝';
    if (mimeType.includes('excel') || mimeType.includes('spreadsheet')) return '📊';
    return '📁';
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
        uploadedFilePath = data.filePath;
    } catch (error) {
        console.error(error);
        alert('Lỗi upload file!');
        cancelFileUpload();
    }
}

function cancelFileUpload() {
    selectedFile = null;
    uploadedFilePath = null;
    if (fileInput) fileInput.value = '';
    if (filePreview) filePreview.style.display = 'none';
}

function onMessageSubmit(event) {
    event.preventDefault();
    const messageContent = messageInput.value.trim();
    if (!messageContent && !uploadedFilePath) return;

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

function recallMessage(messageId) {
    if (!confirm('Bạn có chắc muốn thu hồi?')) return;
    const msgRow = document.querySelector(`.msg-row[data-message-id="${messageId}"]`);
    if (msgRow) {
        const contentDiv = msgRow.querySelector('.msg-content');
        if (contentDiv) {
            contentDiv.className = 'msg-content recalled';
            contentDiv.innerHTML = 'Tin nhắn đã được thu hồi';
            contentDiv.removeAttribute('style');
            contentDiv.removeAttribute('title');
        }
        const actions = msgRow.querySelector('.msg-actions');
        if(actions) actions.remove();
    }
    if (stompClient && currentRoomId) {
        stompClient.send("/app/chat.recallMessage", {}, JSON.stringify({ messageId: messageId, roomId: currentRoomId }));
    }
}

function onMessageReceived(payload) {
    const messageDto = JSON.parse(payload.body);
    if (currentRoomId && messageDto.roomId == currentRoomId) {
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
            scrollToBottom();
        }
    }
}

function onTypingInput() {
    sendTypingEvent(true);
    clearTimeout(typingTimeout);
    typingTimeout = setTimeout(() => sendTypingEvent(false), 3000);
}

function sendTypingEvent(isTyping) {
    if (stompClient && currentRoomId) {
        stompClient.send("/app/chat.typing", {}, JSON.stringify({ roomId: currentRoomId, isTyping: isTyping }));
    }
}

function onTypingReceived(payload) {
    const typingDto = JSON.parse(payload.body);
    if (typingDto.username === currentUsername) return;
    if (typingDto.isTyping) typingUsers.set(typingDto.username, new Date());
    else typingUsers.delete(typingDto.username);
    updateTypingIndicator();
}

function updateTypingIndicator() {
    const now = new Date();
    typingUsers.forEach((time, username) => { if (now - time > 5000) typingUsers.delete(username); });
    const names = Array.from(typingUsers.keys());
    if (names.length === 0) typingIndicator.textContent = "";
    else if (names.length === 1) typingIndicator.textContent = `${names[0]} đang gõ...`;
    else typingIndicator.textContent = "Nhiều người đang gõ...";
}

function onPresenceMessageReceived(payload) {
    const presenceDto = JSON.parse(payload.body);
    presenceStatus.set(presenceDto.username, presenceDto.status);
    updateAllPresenceIndicators(presenceDto.username, presenceDto.status);
}

function onNotificationReceived(payload) {
    if (payload.body === "NEW_ROOM") loadChatRooms();
}

function scrollToBottom() {
    if (messageArea) messageArea.scrollTop = messageArea.scrollHeight;
}

function updateAllPresenceIndicators(username, status) {
    const statusText = status === 'ONLINE' ? 'Online' : 'Offline';
    const statusClass = status === 'ONLINE' ? 'online' : '';
    document.querySelectorAll(`.user-info[data-username="${username}"]`).forEach(userInfo => {
        const dot = userInfo.querySelector('.status-dot');
        const text = userInfo.querySelector('.status-text');
        if (dot) dot.className = `status-dot ${statusClass}`;
        if (text) text.textContent = statusText;
    });
}

if (document.querySelector('.messenger-container')) connect();
// === LOGIC TẠO NHÓM MỚI ===

// Set chứa các ID user được chọn
let selectedUserIdsForGroup = new Set();

async function loadUsersForGroupCreation() {
    const groupUserListEl = document.querySelector('#group-user-list');
    if (!groupUserListEl) return;

    // Reset input tên nhóm và search
    document.querySelector('#group-name-input').value = '';
    document.querySelector('#search-user-group').value = '';
    selectedUserIdsForGroup.clear();

    groupUserListEl.innerHTML = '<p class="text-center text-muted">Đang tải...</p>';

    try {
        // Tận dụng API lấy user có sẵn
        const response = await fetch('/api/chat/users');
        if (!response.ok) throw new Error('Lỗi tải danh sách user');
        const users = await response.json();

        groupUserListEl.innerHTML = '';

        if (users.length === 0) {
            groupUserListEl.innerHTML = '<p class="text-center p-2">Không tìm thấy user nào khác.</p>';
            return;
        }

        users.forEach(user => {
            const item = document.createElement('div');
            item.className = 'user-select-item d-flex align-items-center p-2 border-bottom';
            item.style.cursor = 'pointer';
            // Lưu data để search
            item.setAttribute('data-search-name', user.name.toLowerCase());

            // Avatar logic
            const avatarHtml = getAvatarHtml(user.avatarUrl, user.name, 'user-avatar-small'); // CSS class user-avatar-small cần thêm hoặc dùng tạm user-avatar

            item.innerHTML = `
                <div class="form-check m-0 d-flex align-items-center w-100">
                    <input class="form-check-input me-3" type="checkbox" value="${user.id}" id="chk-user-${user.id}" style="width: 20px; height: 20px;">
                    <label class="form-check-label d-flex align-items-center w-100" for="chk-user-${user.id}" style="cursor:pointer;">
                        ${avatarHtml}
                        <span class="ms-2 fw-bold">${user.name}</span>
                    </label>
                </div>
            `;

            // Xử lý click vào cả dòng để chọn checkbox
            item.addEventListener('click', (e) => {
                // Ngăn chặn click 2 lần nếu click trực tiếp vào input
                if (e.target.tagName === 'INPUT') {
                    toggleUserSelection(user.id, e.target.checked);
                    return;
                }
                e.preventDefault();
                const checkbox = item.querySelector('input[type="checkbox"]');
                checkbox.checked = !checkbox.checked;
                toggleUserSelection(user.id, checkbox.checked);
            });

            groupUserListEl.appendChild(item);
        });

    } catch (error) {
        console.error(error);
        groupUserListEl.innerHTML = '<p class="text-danger text-center">Lỗi tải dữ liệu</p>';
    }
}

function toggleUserSelection(userId, isChecked) {
    if (isChecked) {
        selectedUserIdsForGroup.add(parseInt(userId));
    } else {
        selectedUserIdsForGroup.delete(parseInt(userId));
    }
}

// Hàm lọc danh sách user trong modal tạo nhóm
function filterGroupUserList(keyword) {
    const items = document.querySelectorAll('#group-user-list .user-select-item');
    const k = keyword.toLowerCase();
    items.forEach(item => {
        const name = item.getAttribute('data-search-name');
        if (name.includes(k)) {
            item.style.display = 'flex';
        } else {
            item.style.display = 'none';
        }
    });
}

async function handleCreateGroup() {
    const groupNameInput = document.querySelector('#group-name-input');
    const groupName = groupNameInput.value.trim();

    if (!groupName) {
        alert("Vui lòng nhập tên nhóm!");
        groupNameInput.focus();
        return;
    }

    if (selectedUserIdsForGroup.size === 0) {
        alert("Vui lòng chọn ít nhất 1 thành viên!");
        return;
    }

    const confirmBtn = document.querySelector('#confirm-create-group-btn');
    const originalText = confirmBtn.textContent;
    confirmBtn.disabled = true;
    confirmBtn.textContent = "Đang tạo...";

    try {
        const csrfMeta = document.querySelector('meta[name="_csrf"]');
        const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        const headers = {
            'Content-Type': 'application/json'
        };
        if (csrfHeaderMeta && csrfMeta) {
            headers[csrfHeaderMeta.getAttribute('content')] = csrfMeta.getAttribute('content');
        }

        const payload = {
            groupName: groupName,
            memberIds: Array.from(selectedUserIdsForGroup)
        };

        const response = await fetch('/api/chat/room/group', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(payload)
        });

        if (!response.ok) throw new Error('Lỗi tạo nhóm');

        const newRoom = await response.json();

        // 1. Đóng modal
        const modalEl = document.querySelector('#createGroupModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        modal.hide();

        // 2. Reload danh sách chat
        await loadChatRooms();

        // 3. Chọn luôn phòng vừa tạo
        selectRoom(newRoom.id, newRoom.name, null); // Chat nhóm chưa có avatar riêng nên để null hoặc xử lý sau

    } catch (error) {
        console.error(error);
        alert("Không thể tạo nhóm. Vui lòng thử lại.");
    } finally {
        confirmBtn.disabled = false;
        confirmBtn.textContent = originalText;
    }
}