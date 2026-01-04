// socket.js
import { state, dom } from './state.js';
import { toggleInputState } from './utils.js';
import { onPresenceMessageReceived, onNotificationReceived, loadFriendList } from './friends.js';
import { loadChatRooms, checkUrlForRedirect } from './rooms.js';

export function connect() {
    const socket = new SockJS('/ws');
    state.stompClient = Stomp.over(socket);
    state.stompClient.debug = null;
    window.stompClient = state.stompClient;
    state.stompClient.connect({}, onConnected, onError);
}

async function onConnected() {
    console.log('Đã kết nối WebSocket Chat!');
    state.isConnected = true;

    if (dom.connectionStatusEl) dom.connectionStatusEl.style.display = 'none';

    if (state.reconnectInterval) {
        clearInterval(state.reconnectInterval);
        state.reconnectInterval = null;
    }

    toggleInputState(true);

    // Subscribe
    state.stompClient.subscribe('/topic/presence', onPresenceMessageReceived);
    state.stompClient.subscribe('/user/queue/notifications', onNotificationReceived);
    state.stompClient.subscribe('/user/queue/video-call', function(payload) {
        if (typeof handleVideoSignal === "function") {
            handleVideoSignal(payload);
        }
    });

    // Load Data
    loadFriendList();
    loadChatRooms();

    try {
        const response = await fetch('/api/chat/online-users');
        const onlineUsernames = await response.json();
        onlineUsernames.forEach(username => state.presenceStatus.set(username, "ONLINE"));
    } catch (error) { console.error("Lỗi tải online-users:", error); }

    checkUrlForRedirect();
}

function onError(error) {
    console.error('Mất kết nối WebSocket:', error);
    state.isConnected = false;

    if (dom.connectionStatusEl) {
        dom.connectionStatusEl.style.display = 'block';
        dom.connectionStatusEl.className = "reconnecting";
        dom.connectionStatusEl.innerHTML = '<i class="fa-solid fa-wifi"></i> Mất kết nối. Đang thử lại...';
    }

    toggleInputState(false);

    if (!state.reconnectInterval) {
        state.reconnectInterval = setInterval(() => {
            console.log("Đang thử kết nối lại...");
            connect();
        }, 5000);
    }
}