// friends.js
import { state, dom } from './state.js';
import { getAvatarHtml } from './utils.js';
import { loadChatRooms, selectRoom } from './rooms.js';

export async function loadFriendList() {
    const container = document.getElementById('friend-list-container');
    if(!container) return;
    try {
        const response = await fetch('/api/friends/list');
        if (!response.ok) return;
        const friends = await response.json();
        container.innerHTML = '';
        if(friends.length === 0) { container.innerHTML = '<p class="text-center text-muted mt-4">Chưa có bạn bè nào.</p>'; return; }

        friends.forEach(friend => {
            const isOnline = state.presenceStatus.get(friend.username) === 'ONLINE';
            const statusClass = isOnline ? 'online' : '';
            const statusText = isOnline ? 'Online' : 'Offline';
            const avatarHtml = getAvatarHtml(friend.avatarUrl, friend.name, 'user-avatar');

            const el = document.createElement('div');
            el.className = 'user-list-item position-relative group-action-hover';
            el.setAttribute('data-username', friend.username);

            // Các hàm onClick startChatWithFriend, unfriendUser sẽ được expose ở main.js
            el.innerHTML = `
                ${avatarHtml}
                <div class="user-info cursor-pointer" onclick="startChatWithFriend(${friend.id})" style="flex-grow: 1; cursor: pointer;"> 
                    <span class="user-name">${friend.name}</span>
                    <span class="user-status-text">
                        <span class="status-dot ${statusClass}"></span>
                        <span class="status-text">${statusText}</span>
                    </span>
                </div>
                
                <div class="dropdown ms-auto">
                    <button class="btn btn-sm btn-light p-1" data-bs-toggle="dropdown" aria-expanded="false">⋮</button>
                    <ul class="dropdown-menu dropdown-menu-end shadow border-0" style="font-size: 0.9rem;">
                        <li><a class="dropdown-item" href="#" onclick="startChatWithFriend(${friend.id})">💬 Nhắn tin</a></li>
                        <li><a class="dropdown-item" href="/profile/${friend.username}">👤 Xem trang cá nhân</a></li>
                        <li><hr class="dropdown-divider"></li>
                        <li><a class="dropdown-item text-danger" href="#" onclick="unfriendUser(${friend.id}, '${friend.username}', this)">❌ Hủy kết bạn</a></li>
                    </ul>
                </div>
            `;
            container.appendChild(el);
        });
    } catch (e) { console.error("Lỗi tải danh bạ:", e); }
}

export async function unfriendUser(friendId, friendUsername, btnElement) {
    if(!confirm(`Bạn có chắc muốn hủy kết bạn với ${friendUsername}?`)) return;
    try {
        const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
        const response = await fetch(`/api/friends/unfriend/${friendId}`, {
            method: 'POST',
            headers: { [csrfHeader]: csrfToken }
        });
        if (response.ok) removeFriendFromUI(friendUsername);
        else alert("Lỗi khi hủy kết bạn.");
    } catch (e) { console.error(e); alert("Có lỗi xảy ra."); }
}

export async function startChatWithFriend(friendId) {
    try {
        const response = await fetch(`/api/chat/room/with/${friendId}`);
        if(response.ok) {
            const roomDto = await response.json();
            const chatTabBtn = document.getElementById('pills-chats-tab');
            if(chatTabBtn) chatTabBtn.click();
            await loadChatRooms();
            selectRoom(roomDto.id, roomDto.oneToOnePartnerName, roomDto.oneToOnePartnerAvatarUrl, 'ONE_TO_ONE');
        }
    } catch(e) { console.error(e); }
}

export function onNotificationReceived(payload) {
    let noti = null;
    let content = "";
    try {
        noti = JSON.parse(payload.body);
        content = noti.content || "";
    } catch (e) { content = payload.body; }

    if (noti && typeof showNotificationPopup === 'function') {
        showNotificationPopup(noti);
    }

    if (content.toLowerCase().includes("chấp nhận lời mời") || content === "FRIEND_ACCEPTED") {
        loadFriendList();
        loadChatRooms();
        window.dispatchEvent(new Event('friend-status-changed'));
    } else if (content.startsWith("UNFRIEND|")) {
        removeFriendFromUI(content.split("|")[1]);
    }
}

export function onPresenceMessageReceived(payload) {
    const presenceDto = JSON.parse(payload.body);
    state.presenceStatus.set(presenceDto.username, presenceDto.status);
    updateAllPresenceIndicators(presenceDto.username, presenceDto.status);
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

function removeFriendFromUI(username) {
    const contactItem = document.querySelector(`.user-list-item[data-username="${username}"]`);
    if (contactItem) {
        contactItem.remove();
        const container = document.getElementById('friend-list-container');
        if (container && container.children.length === 0) {
            container.innerHTML = '<p class="text-center text-muted mt-4">Chưa có bạn bè nào.</p>';
        }
    }
}