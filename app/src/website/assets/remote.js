/**
 * TVV REMOTE - Mobile Client Controller (v2.76)
 * Soporte dual: Red Wi-Fi Local directa (sin internet) y WebRTC P2P (PeerJS)
 * Sistema de Diagnóstico Técnico en Tiempo Real y Códigos de Error Específicos
 */

(function () {
    'use strict';

    // Detección de Modo Wi-Fi Local (Servidor nativo en Android TV)
    const urlParams = new URLSearchParams(window.location.search);
    const isLocalDirect = window.location.port === '8088';
    const lanParam = urlParams.get('lan');
    const isLocalMode = isLocalDirect || !!lanParam;
    const localBaseUrl = isLocalDirect ? window.location.origin : (lanParam ? (lanParam.startsWith('http') ? lanParam : 'http://' + lanParam) : '');

    // Estado de la aplicación
    const state = {
        isLocal: isLocalMode,
        peer: null,
        conn: null,
        roomCode: null,
        isConnected: false,
        reconnectTimer: null,
        channels: [],
        activeCategory: 'all',
        numpadValue: '',
        numpadTimer: null,
        currentErrorCode: null,
        currentErrorDesc: null,
        toastTimer: null,
        logs: []
    };

    // Elementos DOM
    const dom = {
        statusBadge: document.getElementById('status-badge'),
        statusText: document.getElementById('status-text'),
        roomCodeTag: document.getElementById('room-code-tag'),
        btnDiagShortcut: document.getElementById('btn-diag-shortcut'),
        btnPairing: document.getElementById('btn-pairing'),
        
        // Banner de Alerta
        alertBanner: document.getElementById('remote-alert-banner'),
        alertTitle: document.getElementById('alert-title'),
        alertCodeBadge: document.getElementById('alert-code-badge'),
        alertDesc: document.getElementById('alert-desc'),
        btnAlertWifi: document.getElementById('btn-alert-wifi'),
        btnAlertDiag: document.getElementById('btn-alert-diag'),

        // Modal de Emparejamiento
        pairingModal: document.getElementById('pairing-modal'),
        tabBtnWifi: document.getElementById('tab-btn-wifi'),
        tabBtnPin: document.getElementById('tab-btn-pin'),
        subviewWifi: document.getElementById('subview-wifi'),
        subviewPin: document.getElementById('subview-pin'),
        wifiIpInput: document.getElementById('wifi-ip-input'),
        btnConnectWifi: document.getElementById('btn-connect-wifi'),
        recentLanBox: document.getElementById('recent-lan-box'),
        recentIpChip: document.getElementById('recent-ip-chip'),
        btnCancelPairing: document.getElementById('btn-cancel-pairing'),
        btnCancelPinPairing: document.getElementById('btn-cancel-pin-pairing'),
        pinInputs: [
            document.getElementById('pin-1'),
            document.getElementById('pin-2'),
            document.getElementById('pin-3'),
            document.getElementById('pin-4')
        ],
        btnConnectPin: document.getElementById('btn-connect-pin'),

        // Modal de Diagnóstico
        diagModal: document.getElementById('diag-modal'),
        btnCloseDiag: document.getElementById('btn-close-diag'),
        diagModeVal: document.getElementById('diag-mode-val'),
        diagCodeVal: document.getElementById('diag-code-val'),
        diagCauseVal: document.getElementById('diag-cause-val'),
        diagTerminal: document.getElementById('diag-terminal'),
        btnCopyDiag: document.getElementById('btn-copy-diag'),
        btnClearDiag: document.getElementById('btn-clear-diag'),
        btnRetryDiag: document.getElementById('btn-retry-diag'),

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
        btnUpdateTv: document.getElementById('btn-update-tv'),
        toast: document.getElementById('remote-toast'),
        
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
    // SISTEMA DE DIAGNÓSTICO TÉCNICO Y REGISTRO EN TIEMPO REAL
    // ==========================================================================
    const diag = {
        log(msg, level = 'info', errorCode = null) {
            const now = new Date();
            const timeStr = now.toTimeString().split(' ')[0] + '.' + String(now.getMilliseconds()).padStart(3, '0');
            const entry = { time: timeStr, msg, level, code: errorCode };
            state.logs.push(entry);
            console.log(`[${timeStr}] [${level.toUpperCase()}] ${msg}`);

            // Actualizar terminal en modal
            if (dom.diagTerminal) {
                const row = document.createElement('div');
                row.className = 'log-entry';
                row.innerHTML = `<span class="log-time">${timeStr}</span> <span class="log-level-${level}">[${level.toUpperCase()}]</span> <span>${escapeHtml(msg)}</span>`;
                dom.diagTerminal.appendChild(row);
                dom.diagTerminal.scrollTop = dom.diagTerminal.scrollHeight;
            }

            // Actualizar métricas si hay error
            if (errorCode) {
                state.currentErrorCode = errorCode;
                state.currentErrorDesc = msg;
                if (dom.diagCodeVal) dom.diagCodeVal.textContent = errorCode;
                if (dom.diagCauseVal) dom.diagCauseVal.textContent = msg;
            }
        },

        showBanner(code, title, desc) {
            if (!dom.alertBanner) return;
            state.currentErrorCode = code;
            state.currentErrorDesc = desc;
            if (dom.alertCodeBadge) dom.alertCodeBadge.textContent = code;
            if (dom.alertTitle) dom.alertTitle.textContent = title;
            if (dom.alertDesc) dom.alertDesc.textContent = desc;
            dom.alertBanner.classList.remove('hidden');

            if (dom.diagCodeVal) dom.diagCodeVal.textContent = code;
            if (dom.diagCauseVal) dom.diagCauseVal.textContent = desc;
        },

        hideBanner() {
            if (dom.alertBanner) dom.alertBanner.classList.add('hidden');
            state.currentErrorCode = null;
            state.currentErrorDesc = null;
            if (dom.diagCodeVal) dom.diagCodeVal.textContent = 'NINGUNO';
            if (dom.diagCauseVal) dom.diagCauseVal.textContent = 'Conexión activa y sincronizada.';
        },

        clear() {
            state.logs = [];
            if (dom.diagTerminal) dom.diagTerminal.innerHTML = '';
            diag.log('Registro de diagnóstico reiniciado.', 'info');
        },

        copy() {
            const lines = [
                '=== DIAGNÓSTICO TÉCNICO TVV REMOTE (v2.70) ===',
                `Fecha/Hora: ${new Date().toISOString()}`,
                `Navegador: ${navigator.userAgent}`,
                `URL actual: ${window.location.href}`,
                `Modo de conexión: ${state.isLocal ? 'Wi-Fi Local Directo (8088)' : 'Nube WebRTC (PeerJS)'}`,
                `PIN / Sala: ${state.roomCode || 'N/A'}`,
                `Código de Error Actual: ${state.currentErrorCode || 'NINGUNO'}`,
                `Causa: ${state.currentErrorDesc || 'Sin errores'}`,
                '',
                '--- HISTORIAL DE EVENTOS ---'
            ];

            state.logs.forEach(l => {
                lines.push(`[${l.time}] [${l.level.toUpperCase()}] ${l.code ? '{' + l.code + '} ' : ''}${l.msg}`);
            });

            const text = lines.join('\n');
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(text).then(() => {
                    if (dom.btnCopyDiag) {
                        const original = dom.btnCopyDiag.textContent;
                        dom.btnCopyDiag.textContent = '✅ ¡Copiado!';
                        setTimeout(() => { dom.btnCopyDiag.textContent = original; }, 2000);
                    }
                }).catch(() => fallbackCopy(text));
            } else {
                fallbackCopy(text);
            }
        }
    };

    function fallbackCopy(text) {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        try {
            document.execCommand('copy');
            if (dom.btnCopyDiag) {
                const original = dom.btnCopyDiag.textContent;
                dom.btnCopyDiag.textContent = '✅ ¡Copiado!';
                setTimeout(() => { dom.btnCopyDiag.textContent = original; }, 2000);
            }
        } catch (e) {
            alert('No se pudo copiar automáticamente. Puedes seleccionar el texto de la consola.');
        }
        document.body.removeChild(ta);
    }

    function escapeHtml(text) {
        if (!text) return '';
        return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function showToast(message, duration = 3000) {
        if (!dom.toast) return;
        dom.toast.textContent = message;
        dom.toast.classList.remove('hidden');
        if (state.toastTimer) clearTimeout(state.toastTimer);
        state.toastTimer = setTimeout(() => {
            dom.toast.classList.add('hidden');
        }, duration);
    }

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
    // ESTADO DE CONEXIÓN
    // ==========================================================================
    function setConnectionStatus(status, text) {
        if (dom.statusBadge) {
            dom.statusBadge.className = 'status-indicator ' + status;
        }
        if (dom.statusText) {
            dom.statusText.textContent = text;
        }
        state.isConnected = (status === 'connected');
    }

    // ==========================================================================
    // MODO 1: RED WI-FI LOCAL DIRECTA (LATENCIA CERO, SIN INTERNET)
    // ==========================================================================
    function initLocalWifiMode() {
        state.isLocal = true;
        if (dom.diagModeVal) dom.diagModeVal.textContent = 'Wi-Fi Local (Puerto 8088)';
        diag.log(`Iniciando Modo Wi-Fi Local directo en: ${localBaseUrl}`, 'info');
        setConnectionStatus('connected', 'Wi-Fi Local');
        diag.hideBanner();
        if (dom.roomCodeTag) dom.roomCodeTag.textContent = 'WI-FI';

        // Guardar última IP exitosa
        if (window.location.hostname && window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1') {
            localStorage.setItem('tvv_last_lan_ip', window.location.hostname);
        }

        // Polling de estado ligero cada 3.5 segundos
        setInterval(async () => {
            try {
                const res = await fetch(localBaseUrl + '/api/status');
                if (res.ok) {
                    const data = await res.json();
                    if (!state.isConnected) {
                        setConnectionStatus('connected', 'Wi-Fi Local');
                        diag.hideBanner();
                    }
                    if (data.channelName) {
                        diag.log(`Canal sintonizado en TV: ${data.channelName} (N° ${data.channelNumber || 1})`, 'info');
                    }
                } else {
                    diag.log(`Fallo HTTP en /api/status: ${res.status}`, 'warn');
                }
            } catch (e) {
                diag.log(`Error de conexión local Wi-Fi: ${e.message}`, 'warn', 'ERR_LAN_TIMEOUT');
                setConnectionStatus('disconnected', 'ERR_LAN_TIMEOUT');
                diag.showBanner(
                    'ERR_LAN_TIMEOUT',
                    'Fallo de Conexión Wi-Fi',
                    'No se pudo comunicar con la app de la tele en el puerto 8088. Comprueba que el televisor siga encendido y en la misma red Wi-Fi.'
                );
            }
        }, 3500);
    }

    function sendLocalCommand(payload) {
        haptic();
        let query = '?action=' + encodeURIComponent(payload.action);
        if (payload.number !== undefined) query += '&number=' + encodeURIComponent(payload.number);
        if (payload.name) query += '&name=' + encodeURIComponent(payload.name);
        if (payload.query !== undefined) query += '&query=' + encodeURIComponent(payload.query);

        diag.log(`Enviando comando local: ${payload.action}`, 'info');

        fetch(localBaseUrl + '/api/cmd' + query, { mode: 'no-cors' }).catch(err => {
            diag.log(`Error enviando comando local: ${err.message}`, 'error', 'ERR_CMD_SEND_FAILED');
        });
    }

    // ==========================================================================
    // MODO 2: COMUNICACIÓN P2P (PEERJS / WEBRTC EN LA NUBE)
    // ==========================================================================
    function initPeerConnection(targetRoom) {
        if (!targetRoom) return;
        state.roomCode = targetRoom.toUpperCase().trim();
        if (dom.diagModeVal) dom.diagModeVal.textContent = `Nube WebRTC (PIN: ${state.roomCode})`;
        if (dom.roomCodeTag) dom.roomCodeTag.textContent = state.roomCode;
        setConnectionStatus('connecting', 'Buscando TV...');

        diag.log(`Iniciando conexión en la nube para TV con PIN: ${state.roomCode}`, 'info');
        localStorage.setItem('tvv_last_room', state.roomCode);

        if (state.reconnectTimer) {
            clearTimeout(state.reconnectTimer);
            state.reconnectTimer = null;
        }
        if (state.conn) {
            try { state.conn.close(); } catch (e) {}
            state.conn = null;
        }
        if (state.peer) {
            try { state.peer.destroy(); } catch (e) {}
            state.peer = null;
        }

        if (typeof Peer === 'undefined') {
            const errCode = 'ERR_NO_PEERJS_LIB';
            diag.log('La librería WebRTC (PeerJS) no se pudo cargar desde el CDN.', 'error', errCode);
            setConnectionStatus('disconnected', errCode);
            diag.showBanner(errCode, 'Librería no disponible', 'No se pudo cargar PeerJS. Comprueba tu conexión a internet o usa Wi-Fi local.');
            return;
        }

        const remotePeerId = 'tvv-remote-' + Math.random().toString(36).substring(2, 9);
        diag.log(`Conectando al servidor de señalización con ID móvil: ${remotePeerId}...`, 'info');

        state.peer = new Peer(remotePeerId, {
            debug: 0,
            config: {
                iceServers: [
                    { urls: 'stun:stun.l.google.com:19302' },
                    { urls: 'stun:stun1.l.google.com:19302' }
                ]
            }
        });

        state.peer.on('open', (id) => {
            diag.log(`Servidor de señalización conectado exitosamente. ID asignado: ${id}`, 'success');
            const hostPeerId = 'tvv-host-' + state.roomCode;
            connectToHost(hostPeerId);
        });

        state.peer.on('error', (err) => {
            const type = err.type || 'unknown';
            diag.log(`Error PeerJS [${type}]: ${err.message}`, 'error');

            if (type === 'peer-unavailable') {
                const code = 'ERR_PEER_UNAVAILABLE';
                const msg = `La TV con PIN ${state.roomCode} no está registrada en el servidor de la nube (tvv-host-${state.roomCode}).`;
                diag.log(msg, 'error', code);
                setConnectionStatus('disconnected', code);
                diag.showBanner(
                    code,
                    'TV No Encontrada',
                    `No hay ninguna pantalla conectada con el PIN ${state.roomCode}. Si tu TV y celular están en la misma red Wi-Fi, pulsa abajo para conectar directamente por Wi-Fi.`
                );

                // Reintentar amablemente en 5s
                if (state.reconnectTimer) clearTimeout(state.reconnectTimer);
                state.reconnectTimer = setTimeout(() => {
                    if (!state.isConnected) {
                        diag.log(`Reintentando búsqueda de tvv-host-${state.roomCode}...`, 'info');
                        const hostPeerId = 'tvv-host-' + state.roomCode;
                        connectToHost(hostPeerId);
                    }
                }, 5000);

            } else if (type === 'network' || type === 'server-error') {
                const code = 'ERR_SIGNALING_FAILED';
                const msg = 'No se pudo contactar al servidor de señalización de PeerJS (0.peerjs.com).';
                diag.log(msg, 'error', code);
                setConnectionStatus('disconnected', code);
                diag.showBanner(code, 'Fallo de Red en la Nube', msg);

            } else {
                const code = 'ERR_PEER_' + type.toUpperCase().replace(/-/g, '_');
                diag.log(`Fallo general WebRTC: ${err.message}`, 'error', code);
                setConnectionStatus('disconnected', code);
                diag.showBanner(code, 'Error de Enlace', `Causa: ${err.message}`);
            }
        });
    }

    function connectToHost(hostPeerId) {
        if (!state.peer || state.peer.destroyed) return;
        diag.log(`Sincronizando canal de datos P2P con: ${hostPeerId}...`, 'info');
        setConnectionStatus('connecting', 'Sincronizando...');

        state.conn = state.peer.connect(hostPeerId, {
            reliable: true,
            serialization: 'json'
        });

        state.conn.on('open', () => {
            diag.log(`¡Canal de datos abierto con éxito con la TV (${hostPeerId})!`, 'success');
            setConnectionStatus('connected', 'Conectado');
            diag.hideBanner();
            haptic(60);
            sendCommand({ action: 'HELLO', client: 'TVV Mobile Remote v2.70' });
        });

        state.conn.on('data', (data) => {
            diag.log(`Mensaje recibido de la TV: ${JSON.stringify(data)}`, 'info');
            handleHostMessage(data);
        });

        state.conn.on('close', () => {
            const code = 'ERR_CONNECTION_CLOSED';
            diag.log('La conexión con la TV se ha cerrado.', 'warn', code);
            setConnectionStatus('connecting', 'Reconectando TV...');
            diag.showBanner(code, 'Conexión Perdida', 'La TV se desconectó o la app se cerró.');
            if (state.reconnectTimer) clearTimeout(state.reconnectTimer);
            state.reconnectTimer = setTimeout(() => {
                connectToHost(hostPeerId);
            }, 3000);
        });

        state.conn.on('error', (err) => {
            diag.log(`Error en DataConnection: ${err.message}`, 'error', 'ERR_DATA_CHANNEL');
            setConnectionStatus('connecting', 'Buscando señal...');
        });
    }

    function sendCommand(payload) {
        if (state.isLocal) {
            sendLocalCommand(payload);
            return;
        }

        haptic();
        if (state.conn && state.conn.open) {
            diag.log(`Enviando comando P2P: ${payload.action}`, 'info');
            state.conn.send(payload);
        } else {
            diag.log(`No se pudo enviar ${payload.action}: canal P2P no está abierto.`, 'warn');
        }
    }

    function handleHostMessage(msg) {
        if (!msg) return;
        if (msg.type === 'NOW_PLAYING') {
            diag.log(`Canal activo en TV: ${msg.channelName} (N° ${msg.channelNumber || 1})`, 'info');
        }
    }

    // ==========================================================================
    // MODAL DE EMPAREJAMIENTO Y SWITCH DE TABS (WI-FI vs PIN)
    // ==========================================================================
    function setupPairingModal() {
        // Switch de pestañas en modal
        dom.tabBtnWifi.addEventListener('click', () => {
            haptic(15);
            dom.tabBtnWifi.classList.add('active');
            dom.tabBtnPin.classList.remove('active');
            dom.subviewWifi.classList.add('active');
            dom.subviewPin.classList.remove('active');
        });

        dom.tabBtnPin.addEventListener('click', () => {
            haptic(15);
            dom.tabBtnPin.classList.add('active');
            dom.tabBtnWifi.classList.remove('active');
            dom.subviewPin.classList.add('active');
            dom.subviewWifi.classList.remove('active');
            if (dom.pinInputs[0]) dom.pinInputs[0].focus();
        });

        // Conectar por Wi-Fi Directo (IP local)
        dom.btnConnectWifi.addEventListener('click', () => {
            haptic(30);
            let rawIp = dom.wifiIpInput.value.trim();
            if (!rawIp) {
                alert('Por favor ingresa la dirección IP de tu TV (ejemplo: 192.168.1.50)');
                return;
            }

            // Limpiar prefijo/sufijo si el usuario los pegó completos
            rawIp = rawIp.replace(/^https?:\/\//i, '').replace(/\/.*$/, '');
            let host = rawIp;
            let port = '8088';
            if (rawIp.includes(':')) {
                const parts = rawIp.split(':');
                host = parts[0];
                port = parts[1] || '8088';
            }

            localStorage.setItem('tvv_last_lan_ip', host);
            diag.log(`Redirigiendo a conexión Wi-Fi Local: http://${host}:${port}`, 'info');

            // Navegar directamente a la IP local para evitar Mixed Content HTTPS->HTTP
            window.location.href = `http://${host}:${port}`;
        });

        // Chip de IP reciente
        const lastIp = localStorage.getItem('tvv_last_lan_ip');
        if (lastIp && dom.recentLanBox && dom.recentIpChip) {
            dom.recentIpChip.textContent = lastIp;
            dom.recentLanBox.classList.remove('hidden');
            dom.wifiIpInput.value = lastIp;
            dom.recentIpChip.addEventListener('click', () => {
                haptic(20);
                window.location.href = `http://${lastIp}:8088`;
            });
        }

        // Conectar por PIN en la nube
        dom.btnConnectPin.addEventListener('click', () => {
            haptic(30);
            const pin = dom.pinInputs.map(i => i.value).join('');
            if (pin.length === 4) {
                dom.pairingModal.classList.add('hidden');
                initPeerConnection(pin);
            } else {
                alert('Ingresa los 4 dígitos del PIN.');
            }
        });

        dom.btnPairing.addEventListener('click', () => {
            haptic(15);
            dom.pairingModal.classList.remove('hidden');
        });

        dom.btnCancelPairing.addEventListener('click', () => {
            dom.pairingModal.classList.add('hidden');
        });

        if (dom.btnCancelPinPairing) {
            dom.btnCancelPinPairing.addEventListener('click', () => {
                dom.pairingModal.classList.add('hidden');
            });
        }

        // Manejo de foco entre los inputs del PIN
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

        // Botones de acción rápida en el banner de alerta
        if (dom.btnAlertWifi) {
            dom.btnAlertWifi.addEventListener('click', () => {
                haptic(20);
                dom.pairingModal.classList.remove('hidden');
                dom.tabBtnWifi.click();
            });
        }

        if (dom.btnAlertDiag) {
            dom.btnAlertDiag.addEventListener('click', () => {
                haptic(20);
                openDiagModal();
            });
        }
    }

    // ==========================================================================
    // MODAL DE DIAGNÓSTICO
    // ==========================================================================
    function openDiagModal() {
        if (!dom.diagModal) return;
        dom.diagModal.classList.remove('hidden');
        if (dom.diagTerminal) {
            dom.diagTerminal.scrollTop = dom.diagTerminal.scrollHeight;
        }
    }

    function closeDiagModal() {
        if (dom.diagModal) dom.diagModal.classList.add('hidden');
    }

    function setupDiagModal() {
        if (dom.btnDiagShortcut) {
            dom.btnDiagShortcut.addEventListener('click', () => {
                haptic(15);
                openDiagModal();
            });
        }

        if (dom.statusBadge) {
            dom.statusBadge.addEventListener('click', () => {
                haptic(15);
                openDiagModal();
            });
        }

        if (dom.btnCloseDiag) {
            dom.btnCloseDiag.addEventListener('click', closeDiagModal);
        }

        if (dom.btnCopyDiag) {
            dom.btnCopyDiag.addEventListener('click', () => {
                haptic(25);
                diag.copy();
            });
        }

        if (dom.btnClearDiag) {
            dom.btnClearDiag.addEventListener('click', () => {
                haptic(15);
                diag.clear();
            });
        }

        if (dom.btnRetryDiag) {
            dom.btnRetryDiag.addEventListener('click', () => {
                haptic(30);
                diag.log('Reintentando conexión manual solicitada...', 'info');
                if (state.isLocal) {
                    initLocalWifiMode();
                } else if (state.roomCode) {
                    initPeerConnection(state.roomCode);
                } else {
                    dom.pairingModal.classList.remove('hidden');
                }
            });
        }
    }

    // ==========================================================================
    // GESTIÓN DE TABS PRINCIPALES (MANDO / CANALES / NÚMEROS)
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
        if (item.el) {
            item.el.addEventListener('click', () => {
                sendCommand({ action: item.action });
            });
        }
    });

    // Botón de Actualizar App en TV
    if (dom.btnUpdateTv) {
        dom.btnUpdateTv.addEventListener('click', () => {
            haptic(40);
            dom.btnUpdateTv.classList.add('loading');
            showToast('🔄 Comprobando actualización en la TV...');
            diag.log('Solicitud de actualización enviada a la TV', 'info');
            sendCommand({ action: 'CHECK_UPDATE' });
            setTimeout(() => {
                if (dom.btnUpdateTv) dom.btnUpdateTv.classList.remove('loading');
            }, 2500);
        });
    }

    // Búsqueda con teclado del celular
    if (dom.btnKeyboardSearch && dom.searchBox) {
        dom.btnKeyboardSearch.addEventListener('click', () => {
            haptic(15);
            dom.searchBox.classList.toggle('hidden');
            if (!dom.searchBox.classList.contains('hidden') && dom.phoneSearchInput) {
                dom.phoneSearchInput.focus();
            }
        });
    }

    if (dom.btnSendSearch && dom.phoneSearchInput) {
        dom.btnSendSearch.addEventListener('click', () => {
            const query = dom.phoneSearchInput.value.trim();
            if (query) {
                sendCommand({ action: 'SEARCH', query: query });
                dom.phoneSearchInput.value = '';
                dom.searchBox.classList.add('hidden');
            }
        });
        dom.phoneSearchInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                dom.btnSendSearch.click();
            }
        });
    }

    // ==========================================================================
    // GUÍA DE CANALES DIRECTA
    // ==========================================================================
    async function loadChannelsList() {
        try {
            const channelsUrl = state.isLocal ? (localBaseUrl + '/channels.json') : 'channels.json?v=2.70';
            const res = await fetch(channelsUrl);
            const data = await res.json();
            state.channels = data.channels || [];
            diag.log(`Cargados ${state.channels.length} canales en la guía remota.`, 'info');
            renderChannelList();
        } catch (e) {
            diag.log(`Error al cargar channels.json: ${e.message}`, 'warn');
            if (dom.remoteChannelList) {
                dom.remoteChannelList.innerHTML = '<div class="loading-state">No se pudieron cargar los canales.</div>';
            }
        }
    }

    function renderChannelList() {
        if (!dom.remoteChannelList) return;
        const q = (dom.remoteChSearch ? dom.remoteChSearch.value : '').toLowerCase().trim();
        const cat = state.activeCategory;

        const filtered = state.channels.filter(ch => {
            const matchName = ch.name.toLowerCase().includes(q) || (ch.number && ch.number.toString().includes(q));
            const matchCat = (cat === 'all') || (ch.category && ch.category.toLowerCase() === cat.toLowerCase());
            return matchName && matchCat;
        });

        if (filtered.length === 0) {
            dom.remoteChannelList.innerHTML = '<div class="loading-state">No se encontraron canales.</div>';
            return;
        }

        let html = '';
        filtered.forEach(ch => {
            html += `
                <div class="channel-item" data-num="${ch.number}" data-name="${encodeURIComponent(ch.name)}">
                    <span class="ch-num">${ch.number}</span>
                    <span class="ch-name">${escapeHtml(ch.name)}</span>
                    <span class="ch-cat">${escapeHtml(ch.category || '')}</span>
                </div>
            `;
        });
        dom.remoteChannelList.innerHTML = html;

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

    if (dom.remoteChSearch) {
        dom.remoteChSearch.addEventListener('input', () => {
            renderChannelList();
        });
    }

    if (dom.chChips) {
        dom.chChips.querySelectorAll('.chip').forEach(chip => {
            chip.addEventListener('click', () => {
                haptic(15);
                dom.chChips.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
                chip.classList.add('active');
                state.activeCategory = chip.dataset.cat;
                renderChannelList();
            });
        });
    }

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
            
            if (state.numpadTimer) clearTimeout(state.numpadTimer);
            state.numpadTimer = setTimeout(() => {
                submitNumpad();
            }, 1500);
        });
    });

    if (dom.numpadClear) {
        dom.numpadClear.addEventListener('click', () => {
            haptic(20);
            state.numpadValue = '';
            dom.numpadVal.textContent = '--';
            if (state.numpadTimer) clearTimeout(state.numpadTimer);
        });
    }

    if (dom.numpadEnter) {
        dom.numpadEnter.addEventListener('click', () => {
            submitNumpad();
        });
    }

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
    // INICIALIZACIÓN
    // ==========================================================================
    setupPairingModal();
    setupDiagModal();

    diag.log('Iniciando TVV Remote v2.70...', 'info');

    if (isLocalMode) {
        initLocalWifiMode();
    } else {
        const roomParam = urlParams.get('room') || urlParams.get('pin') || urlParams.get('r');
        if (roomParam) {
            initPeerConnection(roomParam);
        } else {
            const last = localStorage.getItem('tvv_last_room');
            if (last) {
                initPeerConnection(last);
            } else {
                setConnectionStatus('disconnected', 'Toca PIN o IP');
                diag.log('Esperando emparejamiento por IP Wi-Fi o PIN de 4 dígitos.', 'info');
            }
        }
    }

    loadChannelsList();

})();
