// main.js
import { state, dom, currentUser } from './state.js';
import { connect } from './socket.js';
import { onMessageSubmit, onTypingInput, executeRecall } from './messaging.js';
import { handleFileSelect, cancelFileUpload } from './upload.js';
import { loadFriendList, unfriendUser, startChatWithFriend } from './friends.js';
import { loadUsersForNewChat, loadChatRooms } from './rooms.js';
import { loadUsersForGroupCreation, handleCreateGroup, filterGroupUserList, handleConfirmLeaveGroup, handleAddMemberToGroup, openGroupMembersModal, kickMember } from './groups.js';
import { scrollToBottom } from './utils.js';
import { startRecording, stopRecordingAndSend, cancelRecording } from './recorder.js';

// --- Expose functions to Global Scope (for HTML onclick attributes) ---
window.openGroupMembersModal = openGroupMembersModal;
window.kickMember = kickMember;
window.unfriendUser = unfriendUser;
window.startChatWithFriend = startChatWithFriend;

// --- DOMContentLoaded ---
document.addEventListener('DOMContentLoaded', () => {
    if (!currentUser.id) {
        console.log("Không phải trang chat, bỏ qua logic chat.js");
        return;
    }

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
    if (dom.messageInput) dom.messageInput.addEventListener('input', onTypingInput);
    if (dom.newChatBtn) dom.newChatBtn.addEventListener('click', loadUsersForNewChat);

    // File Upload Events
    if (dom.fileBtn) dom.fileBtn.addEventListener('click', () => { dom.fileInput.setAttribute('accept', '*/*'); dom.fileInput.click(); });
    if (dom.imageBtn) dom.imageBtn.addEventListener('click', () => { dom.fileInput.setAttribute('accept', 'image/*'); dom.fileInput.click(); });
    if (dom.fileInput) dom.fileInput.addEventListener('change', handleFileSelect);
    if (dom.cancelFileBtn) dom.cancelFileBtn.addEventListener('click', cancelFileUpload);

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

// Hàm forceScroll cho nút "Tin nhắn mới" (cần expose ra window nếu nút này gọi onclick)
window.forceScrollBottom = function() {
    scrollToBottom(true);
};