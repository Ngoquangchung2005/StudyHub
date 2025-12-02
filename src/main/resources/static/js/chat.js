'use strict';

// --- Biến toàn cục ---
const messageForm = document.querySelector('#messageForm');
const messageInput = document.querySelector('#message');

// Kiểm tra xem element có tồn tại không trước khi lấy .value
const userIdEl = document.querySelector('#current-user-id');
const usernameEl = document.querySelector('#current-username');

const currentUserId = userIdEl ? userIdEl.value : null;
const currentUsername = usernameEl ? usernameEl.value : null;

if (!currentUserId) {
    console.log("Không phải trang chat, bỏ qua logic chat.js");
}

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

// === BIẾN LƯU MESSAGE ID CẦN THU HỒI ===
let messageIdToRecall = null;

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

    // Lắng nghe tín hiệu Video Call
    stompClient.subscribe('/user/queue/video-call', function(payload) {
        if (typeof handleVideoSignal === "function") {
            handleVideoSignal(payload);
        }
    });

    // === ĐĂNG KÝ SỰ KIỆN POPUP ===
    const confirmRecallBtn = document.getElementById('btn-confirm-recall-action');
    if (confirmRecallBtn) confirmRecallBtn.addEventListener('click', executeRecall);

    const confirmLeaveBtn = document.getElementById('btn-confirm-leave-group');
    if (confirmLeaveBtn) confirmLeaveBtn.addEventListener('click', handleConfirmLeaveGroup);

    // === ĐĂNG KÝ SỰ KIỆN CHO NHÓM ===
    const newGroupBtn = document.querySelector('#new-group-btn');
    const confirmGroupBtn = document.querySelector('#confirm-create-group-btn');
    const groupSearchInput = document.querySelector('#search-user-group');

    if (newGroupBtn) newGroupBtn.addEventListener('click', loadUsersForGroupCreation);
    if (confirmGroupBtn) confirmGroupBtn.addEventListener('click', handleCreateGroup);
    if (groupSearchInput) groupSearchInput.addEventListener('input', (e) => filterGroupUserList(e.target.value));

    // === ĐĂNG KÝ SỰ KIỆN THÊM THÀNH VIÊN VÀO NHÓM ===
    const btnAddMemberConfirm = document.getElementById('btn-add-member-confirm');
    if(btnAddMemberConfirm) btnAddMemberConfirm.addEventListener('click', handleAddMemberToGroup);

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
            // Lưu loại phòng
            roomElement.setAttribute('data-room-type', room.type);
            if(avatarUrl) roomElement.setAttribute('data-avatar-url', avatarUrl);

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
    const target = event.currentTarget;
    const roomId = target.getAttribute('data-room-id');
    const roomName = target.getAttribute('data-room-name');
    const avatarUrl = target.getAttribute('data-avatar-url');
    const roomType = target.getAttribute('data-room-type');

    selectRoom(roomId, roomName, avatarUrl, roomType);
}

async function selectRoom(roomId, roomName, avatarUrl, roomType) {
    if (currentRoomId === roomId) return;
    currentRoomId = roomId;

    subscriptions.forEach(sub => sub.unsubscribe());
    subscriptions.clear();

    if (chatWelcomeScreen) chatWelcomeScreen.style.display = 'none';
    if (chatMainWindow) chatMainWindow.style.display = 'flex';
    if (messageInput) messageInput.disabled = false;
    if (messageSendBtn) messageSendBtn.disabled = false;

    document.querySelectorAll('#chat-room-list .user-list-item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('data-room-id') === roomId) {
            item.classList.add('active');
        }
    });

    if (chatMainHeader) {
        const avatarHtml = getAvatarHtml(avatarUrl, roomName, 'user-avatar');
        let partnerUsername = null;

        if (roomType === 'ONE_TO_ONE') {
            const roomItem = document.querySelector(`.user-list-item[data-room-id="${roomId}"]`);
            const userInfoDiv = roomItem ? roomItem.querySelector('.user-info') : null;
            partnerUsername = userInfoDiv ? userInfoDiv.getAttribute('data-username') : null;
        }

        let headerContent = `
            ${avatarHtml}
            <div class="ms-2 flex-grow-1">
                <h5 class="mb-0 fw-bold">${roomName}</h5>
            </div>
            <div class="d-flex align-items-center gap-2">
        `;

        if (roomType === 'ONE_TO_ONE' && partnerUsername) {
            headerContent += `<button id="btn-start-video-call" class="btn btn-primary btn-sm rounded-circle" title="Gọi Video">📹</button>`;
        }

        // === UPDATE: NÚT QUẢN LÝ NHÓM ===
        if (roomType === 'GROUP') {
            headerContent += `
                <button class="btn btn-light btn-sm rounded-circle ms-2" 
                        onclick="openGroupMembersModal(${roomId})" 
                        title="Thành viên nhóm">
                    ⚙️
                </button>
                <button class="btn btn-outline-danger btn-sm" 
                        data-bs-toggle="modal" 
                        data-bs-target="#leaveGroupModal" 
                        title="Rời nhóm">
                    🚪 Rời nhóm
                </button>
            `;
        }

        headerContent += `</div>`;
        chatMainHeader.innerHTML = headerContent;

        const btnVideoCall = document.getElementById('btn-start-video-call');
        if (btnVideoCall && partnerUsername) {
            btnVideoCall.addEventListener('click', function() {
                if (typeof startVideoCall === 'function') {
                    startVideoCall(partnerUsername);
                } else {
                    console.error("Hàm startVideoCall không tồn tại!");
                }
            });
        }
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
            selectRoom(roomDto.id, roomDto.oneToOnePartnerName, roomDto.oneToOnePartnerAvatarUrl, 'ONE_TO_ONE');
            history.replaceState(null, '', window.location.pathname);
        } catch (error) {
            console.error(error);
            history.replaceState(null, '', window.location.pathname);
        }
    }
}

// ===========================================
// === CHAT MỚI & TẠO NHÓM (MODAL) ===
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
        selectRoom(roomDto.id, roomDto.oneToOnePartnerName, roomDto.oneToOnePartnerAvatarUrl, 'ONE_TO_ONE');
    } catch (error) {
        console.error(error);
    }
}

// --- LOGIC TẠO NHÓM ---

let selectedUserIdsForGroup = new Set();

async function loadUsersForGroupCreation() {
    const groupUserListEl = document.querySelector('#group-user-list');
    if (!groupUserListEl) return;

    document.querySelector('#group-name-input').value = '';
    document.querySelector('#search-user-group').value = '';
    selectedUserIdsForGroup.clear();

    groupUserListEl.innerHTML = '<p class="text-center text-muted">Đang tải...</p>';

    try {
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
            item.setAttribute('data-search-name', user.name.toLowerCase());

            const avatarHtml = getAvatarHtml(user.avatarUrl, user.name, 'user-avatar-small');

            item.innerHTML = `
                <div class="form-check m-0 d-flex align-items-center w-100">
                    <input class="form-check-input me-3" type="checkbox" value="${user.id}" id="chk-user-${user.id}" style="width: 20px; height: 20px;">
                    <label class="form-check-label d-flex align-items-center w-100" for="chk-user-${user.id}" style="cursor:pointer;">
                        ${avatarHtml}
                        <span class="ms-2 fw-bold">${user.name}</span>
                    </label>
                </div>
            `;

            item.addEventListener('click', (e) => {
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
        const headers = { 'Content-Type': 'application/json' };
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

        const modalEl = document.querySelector('#createGroupModal');
        if (modalEl) {
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) modal.hide();
        }

        await loadChatRooms();
        selectRoom(newRoom.id, newRoom.name, null, 'GROUP');

    } catch (error) {
        console.error(error);
        alert("Không thể tạo nhóm. Vui lòng thử lại.");
    } finally {
        confirmBtn.disabled = false;
        confirmBtn.textContent = originalText;
    }
}

// ===========================================
// === QUẢN LÝ THÀNH VIÊN NHÓM (MỚI) ===
// ===========================================

async function openGroupMembersModal(roomId) {
    window.currentGroupSettingsId = roomId;

    const modalList = document.getElementById('group-members-list');
    modalList.innerHTML = '<p class="text-center text-muted">Đang tải...</p>';

    const modal = new bootstrap.Modal(document.getElementById('groupMembersModal'));
    modal.show();

    try {
        const response = await fetch(`/api/chat/room/${roomId}/members`);
        if (response.ok) {
            const members = await response.json();
            renderGroupMembers(members);
        }
    } catch (e) {
        console.error(e);
        modalList.innerHTML = '<p class="text-danger text-center">Lỗi tải danh sách</p>';
    }
}

function renderGroupMembers(members) {
    const listEl = document.getElementById('group-members-list');
    listEl.innerHTML = '';

    const myId = document.getElementById('current-user-id').value;

    members.forEach(m => {
        const isMe = String(m.id) === String(myId);
        const avatarHtml = getAvatarHtml(m.avatarUrl, m.name, 'user-avatar-small');

        const kickBtn = isMe ? '<span class="badge bg-secondary">Bạn</span>' :
            `<button class="btn btn-sm btn-outline-danger py-0" onclick="kickMember(${m.id})">Đuổi</button>`;

        const item = document.createElement('div');
        item.className = 'd-flex align-items-center justify-content-between p-2 border-bottom';
        item.innerHTML = `
            <div class="d-flex align-items-center">
                ${avatarHtml}
                <div class="ms-2">
                    <div class="fw-bold" style="font-size: 0.9rem;">${m.name}</div>
                    <div class="text-muted small">@${m.username}</div>
                </div>
            </div>
            <div>${kickBtn}</div>
        `;
        listEl.appendChild(item);
    });
}

// Xử lý thêm thành viên
async function handleAddMemberToGroup() {
    const input = document.getElementById('input-add-member');
    const username = input.value.trim();
    const roomId = window.currentGroupSettingsId;
    const errorDiv = document.getElementById('add-member-error');

    if(!username) return;

    try {
        const res = await fetch('/api/chat/users');
        const users = await res.json();

        const foundUser = users.find(u => u.username === username);

        if (!foundUser) {
            errorDiv.textContent = "Không tìm thấy username này!";
            errorDiv.style.display = 'block';
            return;
        }

        const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

        const addRes = await fetch(`/api/chat/room/${roomId}/add/${foundUser.id}`, {
            method: 'POST',
            headers: { [csrfHeader]: csrfToken }
        });

        if (addRes.ok) {
            input.value = '';
            errorDiv.style.display = 'none';
            alert("Đã thêm thành viên!");
            openGroupMembersModal(roomId); // Reload lại list
        } else {
            errorDiv.textContent = "Lỗi: Có thể người này đã ở trong nhóm.";
            errorDiv.style.display = 'block';
        }

    } catch (e) {
        console.error(e);
        errorDiv.textContent = "Lỗi hệ thống";
        errorDiv.style.display = 'block';
    }
}

// Xử lý kick thành viên
async function kickMember(userId) {
    if(!confirm("Bạn có chắc chắn muốn mời người này ra khỏi nhóm?")) return;

    const roomId = window.currentGroupSettingsId;
    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    try {
        const res = await fetch(`/api/chat/room/${roomId}/kick/${userId}`, {
            method: 'POST',
            headers: { [csrfHeader]: csrfToken }
        });

        if(res.ok) {
            openGroupMembersModal(roomId);
        } else {
            alert("Lỗi khi xóa thành viên.");
        }
    } catch (e) {
        console.error(e);
    }
}

// ===========================================
// === CÁC HÀM CŨ (DISPLAY MSG, UPLOAD, ETC) ===
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
    messageIdToRecall = messageId;
    const modalElement = document.getElementById('recallConfirmationModal');
    const modal = new bootstrap.Modal(modalElement);
    modal.show();
}

function executeRecall() {
    if (!messageIdToRecall) return;

    const msgRow = document.querySelector(`.msg-row[data-message-id="${messageIdToRecall}"]`);
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
        stompClient.send("/app/chat.recallMessage", {}, JSON.stringify({ messageId: messageIdToRecall, roomId: currentRoomId }));
    }

    const modalElement = document.getElementById('recallConfirmationModal');
    const modal = bootstrap.Modal.getInstance(modalElement);
    modal.hide();

    messageIdToRecall = null;
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

// Logic Rời Nhóm (Giữ nguyên)
async function handleConfirmLeaveGroup() {
    if (!currentRoomId) return;
    const btn = document.getElementById('btn-confirm-leave-group');
    const originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = "Đang xử lý...";
    try {
        const csrfMeta = document.querySelector('meta[name="_csrf"]');
        const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        const headers = {};
        if (csrfHeaderMeta && csrfMeta) {
            headers[csrfHeaderMeta.getAttribute('content')] = csrfMeta.getAttribute('content');
        }
        const response = await fetch(`/api/chat/room/${currentRoomId}/leave`, {
            method: 'POST',
            headers: headers
        });
        if (response.ok) {
            const modalEl = document.getElementById('leaveGroupModal');
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) modal.hide();
            const roomItem = document.querySelector(`.user-list-item[data-room-id="${currentRoomId}"]`);
            if (roomItem) roomItem.remove();
            chatMainWindow.style.display = 'none';
            chatWelcomeScreen.style.display = 'flex';
            if (stompClient) stompClient.unsubscribe(`/topic/room/${currentRoomId}`);
            currentRoomId = null;
        } else {
            const text = await response.text();
            alert("Lỗi: " + text);
        }
    } catch (error) {
        console.error(error);
        alert("Có lỗi xảy ra khi rời nhóm.");
    } finally {
        btn.disabled = false;
        btn.textContent = originalText;
    }
}