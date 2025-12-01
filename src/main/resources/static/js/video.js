'use strict';

// === CẤU HÌNH GLOBAL ===
const rtcConfig = {
    iceServers: [
        { urls: 'stun:stun.l.google.com:19302' }
    ]
};

let peerConnection;
let localStream;
let remoteUsername;
let pendingOffer;

// === 1. HÀM XỬ LÝ TÍN HIỆU TỪ SERVER ===
async function handleVideoSignal(payload) {
    console.log("🔥 Đã nhận tín hiệu Video từ Socket!"); // <--- LOG DEBUG

    const message = JSON.parse(payload.body);
    console.log("Loại tín hiệu:", message.type, "Từ:", message.sender);

    if (message.type === 'offer') {
        // BÊN NHẬN: Có người gọi đến
        remoteUsername = message.sender;
        document.getElementById('caller-name').textContent = remoteUsername + " đang gọi...";

        // Hiện Modal thông báo
        const incomingModalEl = document.getElementById('incomingCallModal');
        if (incomingModalEl) {
            const modal = new bootstrap.Modal(incomingModalEl);
            modal.show();
        }

        pendingOffer = message.data;

    } else if (message.type === 'answer') {
        // BÊN GỌI: Đối phương đã bắt máy
        console.log("Đối phương đã bắt máy!");
        if (peerConnection) {
            await peerConnection.setRemoteDescription(new RTCSessionDescription(JSON.parse(message.data)));
        }

    } else if (message.type === 'candidate') {
        // Nhận thông tin mạng
        if (peerConnection) {
            try {
                // QUAN TRỌNG: Phải đảm bảo peerConnection đã có RemoteDescription trước khi add candidate
                // Nếu thêm quá sớm sẽ bị lỗi.
                if (peerConnection.remoteDescription) {
                    await peerConnection.addIceCandidate(new RTCIceCandidate(JSON.parse(message.data)));
                    console.log("Đã thêm ICE Candidate thành công");
                } else {
                    // Nếu chưa có RemoteDescription, hãy lưu tạm candidate lại và thêm sau (Advanced)
                    // Hoặc đơn giản là log ra warning
                    console.warn("Chưa có RemoteDescription, bỏ qua candidate này");
                }
            } catch (e) {
                console.error("Lỗi add ICE candidate", e);
            }
        }
    } else if (message.type === 'leave') {
        endCall(false);
        alert("Cuộc gọi đã kết thúc.");
    }
}

// === 2. BẮT ĐẦU CUỘC GỌI ===
async function startVideoCall(partnerUsername) {
    console.log("Đang gọi cho:", partnerUsername);

    if (!partnerUsername) {
        alert("Lỗi: Không tìm thấy username người nhận!");
        return;
    }
    remoteUsername = partnerUsername;

    // Mở Modal Video
    const videoModal = new bootstrap.Modal(document.getElementById('videoCallModal'));
    videoModal.show();

    await setupLocalStream();
    createPeerConnection();

    // Tạo Offer
    const offer = await peerConnection.createOffer();
    await peerConnection.setLocalDescription(offer);

    // Gửi tín hiệu
    sendSignal('offer', JSON.stringify(offer));
}

// === 3. TRẢ LỜI CUỘC GỌI ===
async function acceptCall() {
    // Ẩn modal thông báo
    const incomingEl = document.getElementById('incomingCallModal');
    const incomingModal = bootstrap.Modal.getInstance(incomingEl);
    if (incomingModal) incomingModal.hide();

    // Hiện modal video
    const videoModal = new bootstrap.Modal(document.getElementById('videoCallModal'));
    videoModal.show();

    await setupLocalStream();
    createPeerConnection();

    // Set Remote (Offer từ người gọi)
    await peerConnection.setRemoteDescription(new RTCSessionDescription(JSON.parse(pendingOffer)));

    // Tạo Answer
    const answer = await peerConnection.createAnswer();
    await peerConnection.setLocalDescription(answer);

    sendSignal('answer', JSON.stringify(answer));
}

// === 4. CÁC HÀM HỖ TRỢ ===
function rejectCall() {
    const incomingEl = document.getElementById('incomingCallModal');
    const modal = bootstrap.Modal.getInstance(incomingEl);
    if(modal) modal.hide();
    sendSignal('leave', 'rejected');
}

function endCall(isInitiator) {
    if (isInitiator && remoteUsername) sendSignal('leave', 'ended');

    if (peerConnection) {
        peerConnection.close();
        peerConnection = null;
    }
    if (localStream) {
        localStream.getTracks().forEach(track => track.stop());
        localStream = null;
    }

    const remoteVideo = document.getElementById('remoteVideo');
    const localVideo = document.getElementById('localVideo');
    if (remoteVideo) remoteVideo.srcObject = null;
    if (localVideo) localVideo.srcObject = null;

    const videoModalEl = document.getElementById('videoCallModal');
    const modal = bootstrap.Modal.getInstance(videoModalEl);
    if (modal) modal.hide();
}

async function setupLocalStream() {
    try {
        localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
        document.getElementById('localVideo').srcObject = localStream;
    } catch (e) {
        alert("Không thể bật Camera: " + e.message);
    }
}

function createPeerConnection() {
    peerConnection = new RTCPeerConnection(rtcConfig);

    localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));

    // Khi nhận được stream của đối phương
    peerConnection.ontrack = (event) => {
        console.log("Đã nhận được Remote Stream!", event.streams);
        const remoteVideo = document.getElementById('remoteVideo');

        if (event.streams && event.streams[0]) {
            remoteVideo.srcObject = event.streams[0];
        } else {
            // Fallback cho một số trình duyệt cũ nếu streams[0] rỗng
            if (!remoteVideo.srcObject) {
                remoteVideo.srcObject = new MediaStream();
            }
            remoteVideo.srcObject.addTrack(event.track);
        }
    };

    peerConnection.onicecandidate = (event) => {
        if (event.candidate) {
            sendSignal('candidate', JSON.stringify(event.candidate));
        }
    };
}

function sendSignal(type, data) {
    if (stompClient && remoteUsername) {
        console.log("Gửi tín hiệu:", type, "Đến:", remoteUsername);
        const msg = {
            type: type,
            data: data,
            recipient: remoteUsername
        };
        stompClient.send("/app/chat.videoCall", {}, JSON.stringify(msg));
    } else {
        console.error("Chưa kết nối socket hoặc thiếu username người nhận");
    }
}

// Gán sự kiện click
document.addEventListener('DOMContentLoaded', () => {
    const btnAccept = document.getElementById('btn-accept-call');
    if(btnAccept) btnAccept.addEventListener('click', acceptCall);

    const btnReject = document.getElementById('btn-reject-call');
    if(btnReject) btnReject.addEventListener('click', rejectCall);

    const btnEnd = document.getElementById('btn-end-call');
    if(btnEnd) btnEnd.addEventListener('click', () => endCall(true));
});