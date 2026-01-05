// rooms.js
import { state, dom, currentUser } from './state.js';
import { getAvatarHtml, scrollToBottom } from './utils.js';
import { onMessageReceived, onTypingReceived, displayMessage } from './messaging.js';
import { openGroupMembersModal } from './groups.js';

export async function loadChatRooms() {
    try {
        const response = await fetch('/api/chat/rooms');
        if (!response.ok) throw new Error('Không thể tải phòng chat');
        const rooms = await response.json();

        dom.chatRoomList.innerHTML = '';
        rooms.forEach(room => {
            const roomName = room.type === 'ONE_TO_ONE' ? room.oneToOnePartnerName : room.name;
            const avatarUrl = room.type === 'ONE_TO_ONE' ? room.oneToOnePartnerAvatarUrl : null;
            const partner = room.members.find(m => m.id != currentUser.id);
            const partnerUsername = partner ? partner.username : '';
            const status = (partner && state.presenceStatus.get(partnerUsername) === 'ONLINE') ? 'online' : '';
            const statusText = status ? 'Online' : 'Offline';

            const roomElement = document.createElement('a');
            roomElement.href = '#';
            roomElement.classList.add('user-list-item');

            // Thêm ID để tìm kiếm khi có tin nhắn đến
            roomElement.id = `room-item-${room.id}`;

            roomElement.setAttribute('data-room-id', room.id);
            roomElement.setAttribute('data-room-name', roomName);
            roomElement.setAttribute('data-room-type', room.type);
            if(avatarUrl) roomElement.setAttribute('data-avatar-url', avatarUrl);

            const avatarHtml = getAvatarHtml(avatarUrl, roomName, 'user-avatar');

            // [ĐÃ SỬA] Chỉ hiển thị Status, xóa class 'room-last-msg'
            roomElement.innerHTML = `
                ${avatarHtml}
                <div class="user-info" data-username="${partnerUsername}">
                    <span class="user-name room-name">${roomName}</span>
                    <span class="user-status-text">
                        <span class="status-dot ${status}"></span>
                        <span class="status-text">${statusText}</span>
                    </span>
                </div>`;
            roomElement.addEventListener('click', onRoomSelected);
            dom.chatRoomList.appendChild(roomElement);
        });
    } catch (error) {
        console.error(error);
        dom.chatRoomList.innerHTML = '<p class="text-danger p-3">Lỗi tải phòng chat.</p>';
    }
}

function onRoomSelected(event) {
    event.preventDefault();
    const target = event.currentTarget;
    selectRoom(
        target.getAttribute('data-room-id'),
        target.getAttribute('data-room-name'),
        target.getAttribute('data-avatar-url'),
        target.getAttribute('data-room-type')
    );
}

export async function selectRoom(roomId, roomName, avatarUrl, roomType) {
    if (state.currentRoomId === roomId) return;
    state.currentRoomId = roomId;

    // --- XÓA DẤU CHẤM ĐỎ KHI CLICK VÀO PHÒNG ---
    const roomElement = document.getElementById(`room-item-${roomId}`);
    if (roomElement) {
        const dot = roomElement.querySelector('.unread-dot');
        if (dot) dot.remove(); // Xóa dấu chấm bên cạnh tên

        const anyDot = document.querySelector('.unread-dot');
        if (!anyDot) {
            const navBadge = document.getElementById('nav-chat-badge');
            if (navBadge) navBadge.style.display = 'none';
        }
    }
    // ------------------------------------------------------

    state.subscriptions.forEach(sub => sub.unsubscribe());
    state.subscriptions.clear();

    if (dom.chatWelcomeScreen) dom.chatWelcomeScreen.style.display = 'none';
    if (dom.chatMainWindow) dom.chatMainWindow.style.display = 'flex';
    if (dom.messageInput) dom.messageInput.disabled = false;
    if (dom.messageSendBtn) dom.messageSendBtn.disabled = false;
    if (dom.newMessageAlertEl) dom.newMessageAlertEl.style.display = 'none';

    document.querySelectorAll('#chat-room-list .user-list-item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('data-room-id') === roomId) item.classList.add('active');
    });

    if (dom.chatMainHeader) {
        const avatarHtml = getAvatarHtml(avatarUrl, roomName, 'user-avatar');
        let partnerUsername = null;
        if (roomType === 'ONE_TO_ONE') {
            const roomItem = document.querySelector(`.user-list-item[data-room-id="${roomId}"]`);
            const userInfoDiv = roomItem ? roomItem.querySelector('.user-info') : null;
            partnerUsername = userInfoDiv ? userInfoDiv.getAttribute('data-username') : null;
        }

        let headerContent = `${avatarHtml}<div class="ms-2 flex-grow-1"><h5 class="mb-0 fw-bold">${roomName}</h5></div><div class="d-flex align-items-center gap-2">`;

        if (roomType === 'ONE_TO_ONE' && partnerUsername) {
            headerContent += `<button id="btn-start-video-call" class="btn btn-primary btn-sm rounded-circle" title="Gọi Video">📹</button>`;
        }
        if (roomType === 'GROUP') {
            headerContent += `
                <button class="btn btn-light btn-sm rounded-circle ms-2" onclick="openGroupMembersModal(${roomId})" title="Thành viên nhóm">⚙️</button>
                <button class="btn btn-outline-danger btn-sm" data-bs-toggle="modal" data-bs-target="#leaveGroupModal" title="Rời nhóm">🚪 Rời nhóm</button>
            `;
        }
        headerContent += `</div>`;
        dom.chatMainHeader.innerHTML = headerContent;

        const btnVideoCall = document.getElementById('btn-start-video-call');
        if (btnVideoCall && partnerUsername) {
            btnVideoCall.addEventListener('click', function() {
                if (typeof startVideoCall === 'function') startVideoCall(partnerUsername);
            });
        }
    }

    state.typingUsers.clear();
    dom.typingIndicator.textContent = "";

    const msgSub = state.stompClient.subscribe(`/topic/room/${roomId}`, onMessageReceived);
    const typeSub = state.stompClient.subscribe(`/topic/room/${roomId}/typing`, onTypingReceived);
    state.subscriptions.set('messages', msgSub);
    state.subscriptions.set('typing', typeSub);

    dom.messageArea.innerHTML = '<p class="text-center mt-3 text-muted">Đang tải lịch sử...</p>';
    try {
        const response = await fetch(`/api/chat/room/${roomId}/messages`);
        if (!response.ok) throw new Error('Không thể tải lịch sử tin nhắn');
        const messages = await response.json();
        dom.messageArea.innerHTML = '';
        messages.forEach(displayMessage);
        scrollToBottom(true);
    } catch (error) {
        console.error(error);
        dom.messageArea.innerHTML = '<p class="text-danger p-3 text-center">Lỗi tải lịch sử chat.</p>';
    }
}

export async function loadUsersForNewChat() {
    try {
        dom.newUserChatList.innerHTML = '<p>Đang tải danh sách...</p>';
        const response = await fetch('/api/chat/users');
        if (!response.ok) throw new Error('Không thể tải danh sách user');
        const users = await response.json();
        dom.newUserChatList.innerHTML = '';
        users.forEach(user => {
            const status = state.presenceStatus.get(user.username) === 'ONLINE' ? 'online' : '';
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
                    <span class="user-status-text"><span class="status-dot ${status}"></span><span class="status-text">${statusText}</span></span>
                </div>`;
            userElement.addEventListener('click', onStartNewChat);
            dom.newUserChatList.appendChild(userElement);
        });
    } catch (error) {
        console.error(error);
        dom.newUserChatList.innerHTML = '<p class="text-danger">Lỗi tải danh sách.</p>';
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
    } catch (error) { console.error(error); }
}

export async function checkUrlForRedirect() {
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