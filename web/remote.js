/**
 * TVV REMOTE - Mobile Client Controller
 * Conexión WebRTC P2P de latencia ultrabaja mediante PeerJS
 */

(function () {
    'use strict';

    // Estado de la aplicación
    const state = {
        peer: null,
        conn: null,
        roomCode: null,
        isConnected: false,
        channels: [],
        activeCategory: 'all',
        numpadValue: '',
        numpadTimer: null
    };

    // Elementos DOM
    const dom = {
        statusBadge: document.getElementById('status-badge'),
        statusText: document.getElementById('status-text'),
        roomCodeTag: document.getElementById('room-code-tag'),
        btnPairing: document.getElementById('btn-pairing'),
        pairingModal: document.getElementById('pairing-modal'),
        pinInputs: [
            document.getElementById('pin-1'),
            document.getElementById('pin-2'),
            document.getElementById('pin-3'),
            document.getElementById('pin-4')
        ],
        btnConnectPin: document.getElementById('btn-connect-pin'),
        btnCancelPairing: document.getElementById('btn-cancel-pairing'),
        tabBtns: document.querySelectorAll('.tab-btn'),
        tabContents: document.querySelectorAll('.tab-content'),
        
        // D-Pad y botones principales
        dpadUp: document.getElementById('dpad-up'),
        dpadDown: document.getElementById('dpad-down'),
        dpadLeft: document.getElementById('dpad-left'),
        dpadRight: document.getElementById('dpad-right'),
        dpadOk: document.getElementById('dpad-ok'),
        
        btnVolUp: document.getElementById('btn-vol-up'),
        btnVolDown: document.getElementById('btn-vol-down'),
        btnMute: document.getElementById('btn-mute'),
        btnChUp: document.getElementById('btn-ch-up'),
        btnChDown: document.getElementById('btn-ch-down'),
        btnPlayPause: document.getElementById('btn-play-pause'),
        btnBack: document.getElementById('btn-back'),
        btnGuide: document.getElementById('btn-guide'),
        btnFullscreen: document.getElementById('btn-fullscreen'),
        btnPower: document.getElementById('btn-power'),
        
        // Búsqueda
        btnKeyboardSearch: document.getElementById('btn-keyboard-search'),
        searchBox: document.getElementById('search-input-box'),
        phoneSearchInput: document.getElementById('phone-search-input'),
        btnSendSearch: document.getElementById('btn-send-search'),
        
        // Canales
        remoteChSearch: document.getElementById('remote-ch-search'),
        chChips: document.getElementById('ch-chips'),
        remoteChannelList: document.getElementById('remote-channel-list'),
        
        // Numpad
        numpadVal: document.getElementById('numpad-val'),
        numBtns: document.querySelectorAll('.num-btn'),
        numpadClear: document.getElementById('numpad-clear'),
        numpadEnter: document.getElementById('numpad-enter')
    };

    // ==========================================================================
    // RESPUESTA HÁPTICA (VIBRACIÓN TÁCTIL)
    // ==========================================================================
    function haptic(ms = 22) {
        if ('vibrate' in navigator) {
            try {
                navigator.vibrate(ms);
            } catch (e) {}
        }
    }

    // ==========================================================================
    // COMUNICACIÓN P2P (PEERJS / WEBRTC)
    // ==========================================================================
    function setConnectionStatus(status, text) {
        dom.statusBadge.className = 'status-indicator ' + status;
        dom.statusText.textContent = text;
        state.isConnected = (status === 'connected');
    }

    function initPeerConnection(targetRoom) {
        if (!targetRoom) return;
        state.roomCode = targetRoom.toUpperCase().trim();
        dom.roomCodeTag.textContent = state.roomCode;
        setConnectionStatus('connecting', 'Conectando...');

        // Guardar última sala para reconexión automática
        localStorage.setItem('tvv_last_room', state.roomCode);

        // Destruir conexión anterior si existe
        if (state.conn) {
            try { state.conn.close(); } catch (e) {}
            state.conn = null;
        }
        if (state.peer) {
            try { state.peer.destroy(); } catch (e) {}
            state.peer = null;
        }

        // Crear Peer anónimo móvil
        const remotePeerId = 'tvv-remote-' + Math.random().toString(36).substring(2, 9);
        state.peer = new Peer(remotePeerId, {
            debug: 0,
            config: {
                iceServers: [
                    { urls: 'stun:stun.l.google.com:19302' },
                    { urls: 'stun:stun1.l.google.com:19302' }
                ]
            }
        });

        state.peer.on('open', () => {
            const hostPeerId = 'tvv-host-' + state.roomCode;
            connectToHost(hostPeerId);
        });

        state.peer.on('error', (err) => {
            console.warn('Peer error:', err);
            setConnectionStatus('disconnected', 'Error de red');
        });
    }

    function connectToHost(hostPeerId) {
        state.conn = state.peer.connect(hostPeerId, {
            reliable: true,
            serialization: 'json'
        });

        state.conn.on('open', () => {
            setConnectionStatus('connected', 'Conectado');
            haptic(60);
            // Saludo inicial
            sendCommand({ action: 'HELLO', client: 'TVV Mobile Remote' });
        });

        state.conn.on('data', (data) => {
            handleHostMessage(data);
        });

        state.conn.on('close', () => {
            setConnectionStatus('disconnected', 'Desconectado');
        });

        state.conn.on('error', () => {
            setConnectionStatus('disconnected', 'Error');
        });
    }

    function sendCommand(payload) {
        haptic();
        if (state.conn && state.conn.open) {
            state.conn.send(payload);
        } else {
            console.log('No conectado a la TV. Comando:', payload);
        }
    }

    function handleHostMessage(msg) {
        if (!msg) return;
        if (msg.type === 'NOW_PLAYING') {
            console.log('Canal actual en TV:', msg.channelName);
        }
    }

    // ==========================================================================
    // GESTIÓN DE TABS
    // ==========================================================================
    dom.tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            haptic(15);
            const targetId = btn.dataset.tab;
            dom.tabBtns.forEach(b => b.classList.remove('active'));
            dom.tabContents.forEach(c => c.classList.remove('active'));
            
            btn.classList.add('active');
            const targetContent = document.getElementById(targetId);
            if (targetContent) targetContent.classList.add('active');
        });
    });

    // ==========================================================================
    // ASIGNACIÓN DE BOTONES DEL MANDO
    // ==========================================================================
    const buttonActions = [
        { el: dom.dpadUp, action: 'DPAD_UP' },
        { el: dom.dpadDown, action: 'DPAD_DOWN' },
        { el: dom.dpadLeft, action: 'DPAD_LEFT' },
        { el: dom.dpadRight, action: 'DPAD_RIGHT' },
        { el: dom.dpadOk, action: 'DPAD_OK' },
        { el: dom.btnVolUp, action: 'VOL_UP' },
        { el: dom.btnVolDown, action: 'VOL_DOWN' },
        { el: dom.btnMute, action: 'MUTE' },
        { el: dom.btnChUp, action: 'CH_UP' },
        { el: dom.btnChDown, action: 'CH_DOWN' },
        { el: dom.btnPlayPause, action: 'PLAY_PAUSE' },
        { el: dom.btnBack, action: 'BACK' },
        { el: dom.btnGuide, action: 'GUIDE' },
        { el: dom.btnFullscreen, action: 'FULLSCREEN' },
        { el: dom.btnPower, action: 'STANDBY' }
    ];

    buttonActions.forEach(item => {
        if (!item.el) return;
        item.el.addEventListener('click', (e) => {
            e.preventDefault();
            sendCommand({ action: item.action });
        });
    });

    // Alternar caja de búsqueda
    dom.btnKeyboardSearch.addEventListener('click', () => {
        haptic();
        dom.searchBox.classList.toggle('hidden');
        if (!dom.searchBox.classList.contains('hidden')) {
            dom.phoneSearchInput.focus();
        }
    });

    dom.btnSendSearch.addEventListener('click', () => {
        const query = dom.phoneSearchInput.value.trim();
        sendCommand({ action: 'SEARCH', query: query });
        dom.searchBox.classList.add('hidden');
    });

    dom.phoneSearchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            dom.btnSendSearch.click();
        }
    });

    // ==========================================================================
    // CARGA Y SELECCIÓN DE CANALES
    // ==========================================================================
    async function loadChannelsList() {
        try {
            const resp = await fetch('channels.json?v=2.68');
            const data = await resp.json();
            state.channels = data.channels || [];
            renderChannelList();
        } catch (err) {
            dom.remoteChannelList.innerHTML = '<div class="loading-state">Error cargando canales</div>';
        }
    }

    function renderChannelList() {
        const query = (dom.remoteChSearch.value || '').toLowerCase().trim();
        const cat = state.activeCategory;

        const filtered = state.channels.filter(ch => {
            const matchName = ch.name.toLowerCase().includes(query) || (ch.number && ch.number.toString().includes(query));
            const matchCat = (cat === 'all') || (ch.category && ch.category.toLowerCase() === cat.toLowerCase());
            return matchName && matchCat;
        });

        if (filtered.length === 0) {
            dom.remoteChannelList.innerHTML = '<div class="loading-state">No se encontraron canales</div>';
            return;
        }

        let html = '';
        filtered.forEach(ch => {
            html += `
                <div class="channel-item" data-num="${ch.number}" data-name="${encodeURIComponent(ch.name)}">
                    <span class="ch-num">${ch.number}</span>
                    <span class="ch-name">${ch.name}</span>
                    <span class="ch-cat">${ch.category || ''}</span>
                </div>
            `;
        });
        dom.remoteChannelList.innerHTML = html;

        // Eventos táctiles en cada canal
        dom.remoteChannelList.querySelectorAll('.channel-item').forEach(el => {
            el.addEventListener('click', () => {
                const num = parseInt(el.dataset.num, 10);
                const name = decodeURIComponent(el.dataset.name);
                haptic(35);
                sendCommand({
                    action: 'SELECT_CHANNEL',
                    number: num,
                    name: name
                });
            });
        });
    }

    dom.remoteChSearch.addEventListener('input', () => {
        renderChannelList();
    });

    dom.chChips.querySelectorAll('.chip').forEach(chip => {
        chip.addEventListener('click', () => {
            haptic(15);
            dom.chChips.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            state.activeCategory = chip.dataset.cat;
            renderChannelList();
        });
    });

    // ==========================================================================
    // TECLADO NUMÉRICO (NUMPAD)
    // ==========================================================================
    dom.numBtns.forEach(btn => {
        if (!btn.dataset.num) return;
        btn.addEventListener('click', () => {
            haptic(20);
            if (state.numpadValue.length < 3) {
                state.numpadValue += btn.dataset.num;
                dom.numpadVal.textContent = state.numpadValue;
            }
            
            // Auto submit tras 1.5s
            if (state.numpadTimer) clearTimeout(state.numpadTimer);
            state.numpadTimer = setTimeout(() => {
                submitNumpad();
            }, 1500);
        });
    });

    dom.numpadClear.addEventListener('click', () => {
        haptic(20);
        state.numpadValue = '';
        dom.numpadVal.textContent = '--';
        if (state.numpadTimer) clearTimeout(state.numpadTimer);
    });

    dom.numpadEnter.addEventListener('click', () => {
        submitNumpad();
    });

    function submitNumpad() {
        if (!state.numpadValue) return;
        const targetNumber = parseInt(state.numpadValue, 10);
        haptic(40);
        sendCommand({
            action: 'NUMPAD_SELECT',
            number: targetNumber
        });
        state.numpadValue = '';
        dom.numpadVal.textContent = targetNumber.toString();
        setTimeout(() => {
            dom.numpadVal.textContent = '--';
        }, 1200);
    }

    // ==========================================================================
    // MODAL DE PIN Y EMPAREJAMIENTO
    // ==========================================================================
    dom.btnPairing.addEventListener('click', () => {
        haptic(15);
        dom.pairingModal.classList.remove('hidden');
        dom.pinInputs[0].focus();
    });

    dom.btnCancelPairing.addEventListener('click', () => {
        dom.pairingModal.classList.add('hidden');
    });

    dom.pinInputs.forEach((input, idx) => {
        input.addEventListener('input', () => {
            if (input.value.length === 1 && idx < 3) {
                dom.pinInputs[idx + 1].focus();
            }
        });
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Backspace' && input.value === '' && idx > 0) {
                dom.pinInputs[idx - 1].focus();
            }
        });
    });

    dom.btnConnectPin.addEventListener('click', () => {
        const pin = dom.pinInputs.map(i => i.value).join('');
        if (pin.length === 4) {
            dom.pairingModal.classList.add('hidden');
            initPeerConnection(pin);
        }
    });

    // ==========================================================================
    // INICIALIZACIÓN
    // ==========================================================================
    function checkUrlRoom() {
        const urlParams = new URLSearchParams(window.location.search);
        const roomParam = urlParams.get('room') || urlParams.get('pin') || urlParams.get('r');
        
        if (roomParam) {
            initPeerConnection(roomParam);
        } else {
            // Revisar último guardado
            const last = localStorage.getItem('tvv_last_room');
            if (last) {
                initPeerConnection(last);
            } else {
                setConnectionStatus('disconnected', 'Toca PIN');
            }
        }
    }

    // Cargar canales y arrancar conexión
    loadChannelsList();
    checkUrlRoom();

})();
