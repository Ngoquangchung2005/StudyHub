// main.js
import { state, dom, currentUser } from './state.js';
import { connect } from './socket.js';
import { onMessageSubmit, onTypingInput, executeRecall, initMessagingFeatures } from './messaging.js';
import { handleFileSelect, cancelFileUpload } from './upload.js';
import { loadFriendList, unfriendUser, startChatWithFriend } from './friends.js';
import { loadUsersForNewChat } from './rooms.js';
import { loadUsersForGroupCreation, handleCreateGroup, filterGroupUserList, handleConfirmLeaveGroup, handleAddMemberToGroup, openGroupMembersModal, kickMember, refreshGroupMembersList, renameGroup, setAdminInGroup, deleteGroup } from './groups.js';
import { scrollToBottom } from './utils.js';

// --- Expose functions to Global Scope (for HTML onclick attributes) ---
window.openGroupMembersModal = openGroupMembersModal;
window.kickMember = kickMember;
window.refreshGroupMembersList = refreshGroupMembersList;
window.renameGroup = renameGroup;
window.setAdminInGroup = setAdminInGroup;
window.deleteGroup = deleteGroup;
window.unfriendUser = unfriendUser;
window.startChatWithFriend = startChatWithFriend;

// [QUAN TRỌNG] Expose hàm này để upload.js hoặc messaging.js có thể gọi cập nhật nút gửi
window.toggleSendButton = toggleSendButton;

// --- DOMContentLoaded ---
document.addEventListener('DOMContentLoaded', () => {
    if (!currentUser.id) {
        console.log("Không phải trang chat, bỏ qua logic chatjs");
        return;
    }

    // Kích hoạt tính năng Emoji và Audio (Logic nằm bên messaging.js)
    initMessagingFeatures();

    if (document.querySelector('.messenger-container')) {
        connect();
        if (dom.messageArea) {
            dom.messageArea.addEventListener('scroll', handleScroll);
        }
    }

    setupEventListeners();
});

// --- Event Listeners Setup ---
function setupEventListeners() {
    const contactTab = document.getElementById('pills-contacts-tab');
    if(contactTab) contactTab.addEventListener('click', loadFriendList);

    const confirmRecallBtn = document.getElementById('btn-confirm-recall-action');
    if (confirmRecallBtn) confirmRecallBtn.addEventListener('click', executeRecall);

    const confirmLeaveBtn = document.getElementById('btn-confirm-leave-group');
    if (confirmLeaveBtn) confirmLeaveBtn.addEventListener('click', handleConfirmLeaveGroup);

    // Group Chat Events
    const newGroupBtn = document.querySelector('#new-group-btn');
    const confirmGroupBtn = document.querySelector('#confirm-create-group-btn');
    const groupSearchInput = document.querySelector('#search-user-group');
    if (newGroupBtn) newGroupBtn.addEventListener('click', loadUsersForGroupCreation);
    if (confirmGroupBtn) confirmGroupBtn.addEventListener('click', handleCreateGroup);
    if (groupSearchInput) groupSearchInput.addEventListener('input', (e) => filterGroupUserList(e.target.value));

    const btnAddMemberConfirm = document.getElementById('btn-add-member-confirm');
    if(btnAddMemberConfirm) btnAddMemberConfirm.addEventListener('click', handleAddMemberToGroup);

    // Message Input Events
    if (dom.messageForm) dom.messageForm.addEventListener('submit', onMessageSubmit, true);

    // [ĐÃ SỬA] Thêm logic toggleSendButton vào sự kiện input
    if (dom.messageInput) {
        dom.messageInput.addEventListener('input', () => {
            onTypingInput();     // Giữ nguyên logic typing indicator cũ
            toggleSendButton();  // Thêm logic bật nút gửi khi có text (hoặc emoji)
        });
    }

    if (dom.newChatBtn) dom.newChatBtn.addEventListener('click', loadUsersForNewChat);

    // File Upload Events
    if (dom.fileBtn) dom.fileBtn.addEventListener('click', () => { dom.fileInput.setAttribute('accept', '*/*'); dom.fileInput.click(); });
    if (dom.imageBtn) dom.imageBtn.addEventListener('click', () => { dom.fileInput.setAttribute('accept', 'image/*'); dom.fileInput.click(); });

    if (dom.fileInput) {
        dom.fileInput.addEventListener('change', (e) => {
            handleFileSelect(e);
            toggleSendButton(); // Cập nhật nút gửi khi chọn file xong
        });
    }

    if (dom.cancelFileBtn) {
        dom.cancelFileBtn.addEventListener('click', () => {
            cancelFileUpload();
            setTimeout(toggleSendButton, 100); // Đợi state cập nhật rồi check lại nút gửi
        });
    }

    // Close popups
    document.addEventListener('click', () => {
        document.querySelectorAll('.action-popup.show').forEach(el => el.classList.remove('show'));
    });
}

function handleScroll() {
    if (!dom.messageArea || !dom.newMessageAlertEl) return;
    const threshold = 100;
    const currentScroll = dom.messageArea.scrollTop + dom.messageArea.clientHeight;
    const maxScroll = dom.messageArea.scrollHeight;
    if (maxScroll - currentScroll < threshold) {
        dom.newMessageAlertEl.style.display = 'none';
    }
}

// [THÊM MỚI] Hàm bật/tắt nút gửi dựa trên nội dung input hoặc file upload
function toggleSendButton() {
    // Lấy nút gửi (đảm bảo id đúng như trong HTML)
    const sendBtn = document.getElementById('btn-send');
    const messageInput = document.getElementById('message');

    if (!sendBtn || !messageInput) return;

    const content = messageInput.value.trim();
    // Kiểm tra xem có text HOẶC có file trong state đang chờ upload không
    // (state.uploadedFilePath import từ state.js)
    const hasFile = state.uploadedFilePath ? true : false;

    if (content.length > 0 || hasFile) {
        sendBtn.removeAttribute('disabled');
        sendBtn.style.opacity = '1';
        sendBtn.style.cursor = 'pointer';
    } else {
        sendBtn.setAttribute('disabled', 'true');
        sendBtn.style.opacity = '0.5';
        sendBtn.style.cursor = 'not-allowed';
    }
}

// Hàm forceScroll cho nút "Tin nhắn mới"
window.forceScrollBottom = function() {
    scrollToBottom(true);
};