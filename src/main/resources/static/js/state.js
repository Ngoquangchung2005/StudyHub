// state.js
export const state = {
    stompClient: null,
    currentRoomId: null,
    subscriptions: new Map(),
    typingTimeout: null,
    presenceStatus: new Map(),
    typingUsers: new Map(),
    messageIdToRecall: null,
    selectedFile: null,
    uploadedFilePath: null,
    // Lưu các room đang có tin nhắn chưa đọc để không bị mất khi sidebar re-render
    unreadRooms: new Set(),
    isConnected: false,
    reconnectInterval: null
};

// DOM Elements cache
export const dom = {
    messageForm: document.querySelector('#messageForm'),
    messageInput: document.querySelector('#message'),
    userIdEl: document.querySelector('#current-user-id'),
    usernameEl: document.querySelector('#current-username'),
    messageSendBtn: document.getElementById('btn-send'),
    messageArea: document.querySelector('#chat-messages-window'),
    chatRoomList: document.querySelector('#chat-room-list'),
    chatMainWindow: document.querySelector('#chat-main-window'),
    chatWelcomeScreen: document.querySelector('#chat-welcome-screen'),
    chatMainHeader: document.querySelector('#chat-main-header'),
    typingIndicator: document.querySelector('#typing-indicator-area'),
    newChatBtn: document.querySelector('#new-chat-btn'),
    newUserChatList: document.querySelector('#new-chat-user-list'),
    connectionStatusEl: document.getElementById('connection-status'),
    newMessageAlertEl: document.getElementById('new-message-alert'),
    fileInput: document.querySelector('#file-input'),
    fileBtn: document.querySelector('#file-btn'),
    imageBtn: document.querySelector('#image-btn'),
    filePreview: document.querySelector('#file-preview'),
    cancelFileBtn: document.querySelector('#cancel-file-btn')
};

export const currentUser = {
    id: dom.userIdEl ? dom.userIdEl.value : null,
    username: dom.usernameEl ? dom.usernameEl.value : null
};