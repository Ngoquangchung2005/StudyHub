'use strict';

// --- Biến toàn cục (DOM Elements) ---
const messageForm = document.querySelector('#messageForm');
const messageInput = document.querySelector('#message');
const messageSendBtn = messageForm.querySelector('button');
const messageArea = document.querySelector('#chat-messages-window');
const chatRoomList = document.querySelector('#chat-room-list');
const chatMainWindow = document.querySelector('#chat-main-window');
const chatWelcomeScreen = document.querySelector('#chat-welcome-screen');
const chatMainHeader = document.querySelector('#chat-main-header');
const typingIndicator = document.querySelector('#typing-indicator-area');
const newChatBtn = document.querySelector('#new-chat-btn');
const newUserChatList = document.querySelector('#new-chat-user-list');

// --- Biến STOMP và User ---
const currentUserId = document.querySelector('#current-user-id').value;
const currentUsername = document.querySelector('#current-username').value;
let stompClient = null;

// --- Biến Trạng thái ---
let currentRoomId = null;
let subscriptions = new Map();
let typingTimeout = null;
let presenceStatus = new Map(); // { username -> "ONLINE" | "OFFLINE" }

// === PHẦN "ĐANG GÕ" (1): Biến lưu ai đang gõ ===
let typingUsers = new Map(); // { username -> timestamp }

// ===========================================
// === 1. KẾT NỐI VÀ KHỞI TẠO
// ===========================================
function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, onConnected, onError);
}

async function onConnected() {
    console.log('Đã kết nối WebSocket!');

    // 1. Đăng ký các kênh
    stompClient.subscribe('/topic/presence', onPresenceMessageReceived);
    stompClient.subscribe(`/user/${currentUsername}/queue/notify`, onNotificationReceived);

    // 2. HỎI SERVER AI ĐANG ONLINE (Fix lỗi Race Condition)
    try {
        const response = await fetch('/api/chat/online-users');
        const onlineUsernames = await response.json();
        onlineUsernames.forEach(username => {
            presenceStatus.set(username, "ONLINE");
        });
    } catch (error) {
        console.error("Không thể tải danh sách online:", error);
    }

    // 3. TẢI DANH SÁCH PHÒNG CHAT
    loadChatRooms();

    // 4. Gán sự kiện
    messageForm.addEventListener('submit', onMessageSubmit, true);
    // === PHẦN "ĐANG GÕ" (2): Gán sự kiện cho ô input ===
    messageInput.addEventListener('input', onTypingInput);
    newChatBtn.addEventListener('click', loadUsersForNewChat);
}

function onError(error) {
    console.error('Không thể kết nối WebSocket: ' + error);
}
async function checkUrlForRedirect() {
    const urlParams = new URLSearchParams(window.location.search);
    const userIdToChat = urlParams.get('withUser');

    if (userIdToChat) {
        console.log("Phát hiện redirect, mở chat với user ID:", userIdToChat);
        try {
            // 1. Gọi API để lấy/tạo phòng (giống hệt onStartNewChat)
            const response = await fetch(`/api/chat/room/with/${userIdToChat}`);
            if (!response.ok) throw new Error('Không thể tạo phòng chat từ redirect');
            const roomDto = await response.json();

            // 2. Tải lại danh sách phòng (để đảm bảo phòng mới/cũ xuất hiện)
            // (Hàm loadChatRooms() đã được gọi trong onConnected,
            // nhưng gọi lại để chắc chắn)
            await loadChatRooms();

            // 3. Chọn phòng đó
            selectRoom(roomDto.id, roomDto.oneToOnePartnerName);

            // 4. Xóa param khỏi URL để tránh load lại khi F5
            history.replaceState(null, '', window.location.pathname);

        } catch (error) {
            console.error(error);
            // Xóa param khỏi URL nếu có lỗi
            history.replaceState(null, '', window.location.pathname);
        }
    }
}
// === KẾT THÚC THÊM MỚI ===

// ===========================================
// === 2. TẢI DỮ LIỆU (API)
// ===========================================

// Tải danh sách phòng chat cho sidebar
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

// Tải danh sách user (cho modal chat mới)
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

// ===========================================
// === 3. XỬ LÝ SỰ KIỆN (EVENTS)
// ===========================================

// (Event) Bấm vào 1 user để chat
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

// (Event) Bấm vào 1 phòng chat trong sidebar
function onRoomSelected(event) {
    event.preventDefault();
    const roomId = event.currentTarget.getAttribute('data-room-id');
    const roomName = event.currentTarget.getAttribute('data-room-name');
    selectRoom(roomId, roomName);
}

// (Action) Logic chọn phòng chat
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

    // === PHẦN "ĐANG GÕ" (3): Xóa indicator khi đổi phòng ===
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

// (Event) Gửi tin nhắn
function onMessageSubmit(event) {
    event.preventDefault();
    const messageContent = messageInput.value.trim();
    if (messageContent && stompClient && currentRoomId) {
        const sendMessageDto = {
            roomId: currentRoomId,
            content: messageContent
        };
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(sendMessageDto));
        messageInput.value = '';

        // === PHẦN "ĐANG GÕ" (4): Gửi 'stop' khi bấm Gửi ===
        sendTypingEvent(false);
    }
}

// === PHẦN "ĐANG GÕ" (5): Tất cả logic xử lý "đang gõ" ===

// (Event) Đang gõ
function onTypingInput() {
    sendTypingEvent(true);
    clearTimeout(typingTimeout);
    typingTimeout = setTimeout(() => {
        sendTypingEvent(false);
    }, 3000); // 3 giây
}

// (Action) Gửi sự kiện gõ
function sendTypingEvent(isTyping) {
    if (stompClient && currentRoomId) {
        const typingDto = {
            roomId: currentRoomId,
            isTyping: isTyping
        };
        stompClient.send("/app/chat.typing", {}, JSON.stringify(typingDto));
    }
}

// (STOMP) Nhận sự kiện "đang gõ"
function onTypingReceived(payload) {
    const typingDto = JSON.parse(payload.body);
    if (typingDto.username === currentUsername) return; // Bỏ qua chính mình

    if (typingDto.isTyping) {
        typingUsers.set(typingDto.username, new Date());
    } else {
        typingUsers.delete(typingDto.username);
    }
    updateTypingIndicator();
}

// (DOM) Cập nhật text "đang gõ" dựa trên danh sách
function updateTypingIndicator() {
    const now = new Date();
    // Xóa những ai đã gõ quá 5 giây (phòng khi "stop" event bị lạc)
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
// === KẾT THÚC PHẦN "ĐANG GÕ" ===


// ===========================================
// === 4. XỬ LÝ NHẬN (STOMP)
// ===========================================

// (STOMP) Nhận tin nhắn mới
function onMessageReceived(payload) {
    const messageDto = JSON.parse(payload.body);
    if (messageDto.roomId == currentRoomId) {
        displayMessage(messageDto);
        scrollToBottom();
    }
}

// (STOMP) Nhận sự kiện Online/Offline
function onPresenceMessageReceived(payload) {
    const presenceDto = JSON.parse(payload.body);
    presenceStatus.set(presenceDto.username, presenceDto.status);
    updateAllPresenceIndicators(presenceDto.username, presenceDto.status);
}

// (STOMP) Nhận thông báo (VD: có phòng mới)
function onNotificationReceived(payload) {
    console.log("Nhận được thông báo:", payload.body);
    if (payload.body === "NEW_ROOM") {
        loadChatRooms();
    }
}

// ===========================================
// === 5. TIỆN ÍCH (DOM)
// ===========================================

// Hiển thị 1 bong bóng chat
function displayMessage(messageDto) {
    const messageContainer = document.createElement('div');
    messageContainer.classList.add('msg-container');

    const type = messageDto.senderId == currentUserId ? 'sent' : 'received';
    messageContainer.classList.add(type);

    const time = new Date(messageDto.timestamp);
    const formattedTime = time.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });

    messageContainer.innerHTML = `
        <div class="msg-bubble">
            ${type === 'received' ? `<strong class="d-block">${messageDto.senderName}</strong>` : ''}
            ${messageDto.content}
        </div>
        <div class="msg-time">${formattedTime}</div>
    `;
    messageArea.appendChild(messageContainer);
}

// Cuộn xuống dưới cùng
function scrollToBottom() {
    messageArea.scrollTop = messageArea.scrollHeight;
}

// Cập nhật tất cả "chấm" online/offline cho 1 user
function updateAllPresenceIndicators(username, status) {
    const statusText = status === 'ONLINE' ? 'Online' : 'Offline';
    const statusClass = status === 'ONLINE' ? 'online' : '';

    // Selector tìm đúng thẻ .user-info dựa trên data-username
    const selector = `.user-info[data-username="${username}"]`;

    document.querySelectorAll(selector).forEach(userInfo => {
        const dot = userInfo.querySelector('.status-dot');
        const text = userInfo.querySelector('.status-text');

        if (dot) dot.className = `status-dot ${statusClass}`;
        if (text) text.textContent = statusText;
    });
}

// ===========================================
// === 6. KÍCH HOẠT
// ===========================================
// Chỉ chạy khi ở trang chat
if (document.querySelector('.chat-app-container')) {
    connect();
}