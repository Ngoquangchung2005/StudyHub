'use strict';

// --- Biến toàn cục (lấy từ file chat.html) ---
const messageForm = document.querySelector('#messageForm');
const messageInput = document.querySelector('#message');
const messageArea = document.querySelector('#chat-messages-area');
const userListArea = document.querySelector('.user-list');

// --- Biến cho STOMP ---
let stompClient = null;
let currentUsername = document.querySelector('#current-username').value;
let currentUserId = document.querySelector('#current-user-id').value;

// --- Biến lưu trữ cuộc hội thoại ---
let selectedUserId = null; // ID của người đang chat cùng

// ===========================================
// === 1. HÀM KẾT NỐI WEBSOCKET
// ===========================================
function connect() {
    // 1. Tạo kết nối SockJS đến endpoint '/ws' (đã cấu hình trong WebSocketConfig)
    const socket = new SockJS('/ws');

    // 2. Tạo một STOMP client từ kết nối SockJS
    stompClient = Stomp.over(socket);

    // 3. Bắt đầu kết nối STOMP
    stompClient.connect({}, onConnected, onError); // (headers, callback_khi_thành_công, callback_khi_lỗi)
}

function onConnected() {
    console.log('Đã kết nối WebSocket thành công!');

    // 4. Đăng ký (Subscribe) vào "kênh" CÁ NHÂN của user này
    // Bất kỳ tin nhắn nào được gửi đến '/queue/messages' CỦA USER NÀY
    // (được server định tuyến) sẽ được nhận bởi hàm onMessageReceived.
    stompClient.subscribe(`/user/${currentUsername}/queue/messages`, onMessageReceived);

    // 5. Thêm listener cho các item trong danh sách user
    document.querySelectorAll('.user-select-item').forEach(item => {
        item.addEventListener('click', onUserSelected);
    });
}

function onError(error) {
    console.error('Không thể kết nối WebSocket: ' + error);
}

// ===========================================
// === 2. HÀM XỬ LÝ KHI CHỌN USER ĐỂ CHAT
// ===========================================
function onUserSelected(event) {
    // Lấy ID và Tên của user được chọn từ thuộc tính 'data-'
    const selectedUserElement = event.currentTarget;
    selectedUserId = selectedUserElement.getAttribute('data-user-id');
    const selectedUserName = selectedUserElement.getAttribute('data-user-name');

    // Hiển thị tên người đang chat cùng
    document.querySelector('#chat-with-header h5').textContent = `Đang chat với: ${selectedUserName}`;

    // Xóa tin nhắn cũ
    messageArea.innerHTML = '';

    // Hiển thị khung nhập tin nhắn
    document.querySelector('#message-form-area').style.display = 'flex';

    // Bỏ highlight user cũ (nếu có) và highlight user mới
    document.querySelectorAll('.user-select-item').forEach(item => {
        item.classList.remove('active', 'bg-light');
    });
    selectedUserElement.classList.add('active', 'bg-light');
}

// ===========================================
// === 3. HÀM XỬ LÝ KHI GỬI TIN NHẮN
// ===========================================
function onMessageSubmit(event) {
    event.preventDefault(); // Ngăn form submit (tải lại trang)

    // 1. Lấy nội dung tin nhắn
    const messageContent = messageInput.value.trim();

    // 2. Kiểm tra nếu có nội dung VÀ đã chọn người nhận
    if (messageContent && stompClient && selectedUserId) {

        // 3. Tạo đối tượng ChatMessage (giống hệt DTO bên Java)
        const chatMessage = {
            senderUsername: currentUsername,
            recipientId: selectedUserId,
            content: messageContent
        };

        // 4. Gửi tin nhắn đến server qua "đường ống" STOMP
        // Nó sẽ đi đến endpoint '/app/chat.sendMessage' (đã cấu hình trong ChatController)
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));

        // 5. Hiển thị tin nhắn của MÌNH lên khung chat ngay lập tức
        displayMessage(chatMessage, 'sent');

        // 6. Xóa nội dung trong ô input
        messageInput.value = '';
    }
}

// ===========================================
// === 4. HÀM XỬ LÝ KHI NHẬN ĐƯỢC TIN NHẮN (TỪ SERVER)
// ===========================================
function onMessageReceived(payload) {
    // 'payload' là tin nhắn STOMP, 'body' là nội dung (một chuỗi JSON)
    const chatMessage = JSON.parse(payload.body);

    // Chỉ hiển thị tin nhắn nếu nó đến từ người mà mình ĐANG CHỌN chat
    // (Chúng ta sẽ nâng cấp phần thông báo "ting ting" sau)
    if (selectedUserId && chatMessage.senderUsername === document.querySelector(`[data-user-id="${selectedUserId}"]`).getAttribute('data-user-name')) {
        displayMessage(chatMessage, 'received');
    } else {
        // (Xử lý thông báo "Bạn có tin nhắn mới từ..." ở đây)
        console.log("Bạn có tin nhắn mới từ: " + chatMessage.senderUsername);
    }
}

// ===========================================
// === 5. HÀM HIỂN THỊ TIN NHẮN LÊN GIAO DIỆN
// ===========================================
function displayMessage(message, type) {
    // 'type' có thể là 'sent' (đã gửi) hoặc 'received' (đã nhận)

    const messageElement = document.createElement('div');
    messageElement.classList.add('message', type); // 'message sent' hoặc 'message received'

    const messageContent = document.createElement('div');
    messageContent.classList.add('message-content');
    messageContent.textContent = message.content;

    messageElement.appendChild(messageContent);
    messageArea.appendChild(messageElement);

    // Tự động cuộn xuống tin nhắn mới nhất
    messageArea.scrollTop = messageArea.scrollHeight;
}


// ===========================================
// === 6. KÍCH HOẠT MỌI THỨ
// ===========================================

// Chỉ chạy code này khi người dùng ở trang chat
if (document.body.contains(messageForm)) {
    // 1. Kết nối WebSocket ngay khi trang tải
    connect();

    // 2. Gán sự kiện 'submit' cho form chat
    messageForm.addEventListener('submit', onMessageSubmit, true);
}