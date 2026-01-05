// groups.js
import { state, dom, currentUser } from './state.js';
import { getAvatarHtml } from './utils.js';
import { loadChatRooms, selectRoom } from './rooms.js';

let selectedUserIdsForGroup = new Set();

export async function loadUsersForGroupCreation() {
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
                </div>`;
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
    if (isChecked) selectedUserIdsForGroup.add(parseInt(userId));
    else selectedUserIdsForGroup.delete(parseInt(userId));
}

export function filterGroupUserList(keyword) {
    const items = document.querySelectorAll('#group-user-list .user-select-item');
    const k = keyword.toLowerCase();
    items.forEach(item => {
        const name = item.getAttribute('data-search-name');
        if (name.includes(k)) item.style.display = 'flex';
        else item.style.display = 'none';
    });
}

export async function handleCreateGroup() {
    const groupNameInput = document.querySelector('#group-name-input');
    const groupName = groupNameInput.value.trim();
    if (!groupName) { alert("Vui lòng nhập tên nhóm!"); groupNameInput.focus(); return; }
    if (selectedUserIdsForGroup.size === 0) { alert("Vui lòng chọn ít nhất 1 thành viên!"); return; }
    const confirmBtn = document.querySelector('#confirm-create-group-btn');
    const originalText = confirmBtn.textContent;
    confirmBtn.disabled = true;
    confirmBtn.textContent = "Đang tạo...";
    try {
        const csrfMeta = document.querySelector('meta[name="_csrf"]');
        const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        const headers = { 'Content-Type': 'application/json' };
        if (csrfHeaderMeta && csrfMeta) headers[csrfHeaderMeta.getAttribute('content')] = csrfMeta.getAttribute('content');
        const payload = { groupName: groupName, memberIds: Array.from(selectedUserIdsForGroup) };
        const response = await fetch('/api/chat/room/group', { method: 'POST', headers: headers, body: JSON.stringify(payload) });
        if (!response.ok) throw new Error('Lỗi tạo nhóm');
        const newRoom = await response.json();
        const modalEl = document.querySelector('#createGroupModal');
        if (modalEl) { const modal = bootstrap.Modal.getInstance(modalEl); if (modal) modal.hide(); }
        await loadChatRooms();
        selectRoom(newRoom.id, newRoom.name, null, 'GROUP');
    } catch (error) { console.error(error); alert("Không thể tạo nhóm. Vui lòng thử lại."); } finally { confirmBtn.disabled = false; confirmBtn.textContent = originalText; }
}

export async function handleConfirmLeaveGroup() {
    if (!state.currentRoomId) return;
    const btn = document.getElementById('btn-confirm-leave-group');
    const originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = "Đang xử lý...";
    try {
        const csrfMeta = document.querySelector('meta[name="_csrf"]');
        const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        const headers = {};
        if (csrfHeaderMeta && csrfMeta) headers[csrfHeaderMeta.getAttribute('content')] = csrfMeta.getAttribute('content');
        const response = await fetch(`/api/chat/room/${state.currentRoomId}/leave`, { method: 'POST', headers: headers });
        if (response.ok) {
            const modalEl = document.getElementById('leaveGroupModal');
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) modal.hide();
            const roomItem = document.querySelector(`.user-list-item[data-room-id="${state.currentRoomId}"]`);
            if (roomItem) roomItem.remove();
            dom.chatMainWindow.style.display = 'none';
            dom.chatWelcomeScreen.style.display = 'flex';
            // Hủy subscribe đúng cách (không dùng topic string)
            try {
                state.subscriptions.forEach(sub => sub.unsubscribe());
                state.subscriptions.clear();
            } catch (e) {}
            state.currentRoomId = null;
        } else { const text = await response.text(); alert("Lỗi: " + text); }
    } catch (error) { console.error(error); alert("Có lỗi xảy ra khi rời nhóm."); } finally { btn.disabled = false; btn.textContent = originalText; }
}

export async function openGroupMembersModal(roomId) {
    window.currentGroupSettingsId = roomId;
    const modalList = document.getElementById('group-members-list');
    modalList.innerHTML = '<p class="text-center text-muted">Đang tải...</p>';
    const modal = new bootstrap.Modal(document.getElementById('groupMembersModal'));
    modal.show();
    try {
        const response = await fetch(`/api/chat/room/${roomId}/members`);
        if (response.ok) { const members = await response.json(); renderGroupMembers(members); }
    } catch (e) { console.error(e); modalList.innerHTML = '<p class="text-danger text-center">Lỗi tải danh sách</p>'; }
}

function renderGroupMembers(members) {
    const listEl = document.getElementById('group-members-list');
    listEl.innerHTML = '';
    const myId = currentUser.id;
    members.forEach(m => {
        const isMe = String(m.id) === String(myId);
        const avatarHtml = getAvatarHtml(m.avatarUrl, m.name, 'user-avatar-small');
        // Lưu ý: kickMember cần được expose ra window
        const kickBtn = isMe ? '<span class="badge bg-secondary">Bạn</span>' : `<button class="btn btn-sm btn-outline-danger py-0" onclick="kickMember(${m.id})">Đuổi</button>`;
        const item = document.createElement('div');
        item.className = 'd-flex align-items-center justify-content-between p-2 border-bottom';
        item.innerHTML = `<div class="d-flex align-items-center">${avatarHtml}<div class="ms-2"><div class="fw-bold" style="font-size: 0.9rem;">${m.name}</div><div class="text-muted small">@${m.username}</div></div></div><div>${kickBtn}</div>`;
        listEl.appendChild(item);
    });
}

// Refresh danh sách thành viên (không mở lại modal) - dùng cho realtime
export async function refreshGroupMembersList(roomId) {
    const listEl = document.getElementById('group-members-list');
    if (!listEl) return;
    listEl.innerHTML = '<p class="text-center text-muted">Đang tải...</p>';
    try {
        const response = await fetch(`/api/chat/room/${roomId}/members`);
        if (response.ok) {
            const members = await response.json();
            renderGroupMembers(members);
        } else {
            listEl.innerHTML = '<p class="text-danger text-center">Lỗi tải danh sách</p>';
        }
    } catch (e) {
        console.error(e);
        listEl.innerHTML = '<p class="text-danger text-center">Lỗi tải danh sách</p>';
    }
}

export async function handleAddMemberToGroup() {
    const input = document.getElementById('input-add-member');
    const username = input.value.trim();
    const roomId = window.currentGroupSettingsId;
    const errorDiv = document.getElementById('add-member-error');
    if(!username) return;
    try {
        const res = await fetch('/api/chat/users');
        const users = await res.json();
        const foundUser = users.find(u => u.username === username);
        if (!foundUser) { errorDiv.textContent = "Không tìm thấy username này!"; errorDiv.style.display = 'block'; return; }
        const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
        const addRes = await fetch(`/api/chat/room/${roomId}/add/${foundUser.id}`, { method: 'POST', headers: { [csrfHeader]: csrfToken } });
        if (addRes.ok) {
            input.value = '';
            errorDiv.style.display = 'none';
            // Realtime sẽ tự cập nhật sidebar cho mọi người; ở modal thì chỉ refresh list
            await refreshGroupMembersList(roomId);
        } else {
            errorDiv.textContent = "Lỗi: Có thể người này đã ở trong nhóm.";
            errorDiv.style.display = 'block';
        }
    } catch (e) { console.error(e); errorDiv.textContent = "Lỗi hệ thống"; errorDiv.style.display = 'block'; }
}

export async function kickMember(userId) {
    if(!confirm("Bạn có chắc chắn muốn mời người này ra khỏi nhóm?")) return;
    const roomId = window.currentGroupSettingsId;
    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');
    try {
        const res = await fetch(`/api/chat/room/${roomId}/kick/${userId}`, { method: 'POST', headers: { [csrfHeader]: csrfToken } });
        if(res.ok) {
            await refreshGroupMembersList(roomId);
        } else {
            alert("Lỗi khi xóa thành viên.");
        }
    } catch (e) { console.error(e); }
}