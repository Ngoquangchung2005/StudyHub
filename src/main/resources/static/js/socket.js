// socket.js
import { state, dom } from './state.js';
import { toggleInputState } from './utils.js';
import { onPresenceMessageReceived, onNotificationReceived, loadFriendList } from './friends.js';
import { loadChatRooms, checkUrlForRedirect, onRoomEventReceived } from './rooms.js';
import { showUnreadDot } from './messaging.js';

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

    // Realtime: tạo nhóm / thêm-kick thành viên / rời nhóm -> cập nhật sidebar không cần reload trang
    state.stompClient.subscribe('/user/queue/room-events', onRoomEventReceived);

    // Lắng nghe tin nhắn mới ở BẤT KỲ phòng nào (server sẽ gửi /queue/chat cho từng user)
    // -> dùng để hiện chấm đỏ ở sidebar (và badge navbar) khi user KHÔNG mở phòng đó.
    state.stompClient.subscribe('/user/queue/chat', function(payload) {
        try {
            const messageDto = JSON.parse(payload.body);
            const incomingRoomId = String(messageDto.roomId);
            const currentRoomId = state.currentRoomId ? String(state.currentRoomId) : null;
            if (currentRoomId !== incomingRoomId) {
                showUnreadDot(incomingRoomId);
            }
        } catch (e) {
            console.error('Lỗi parse chat queue payload:', e);
        }
    });
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