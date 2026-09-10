/**
 * TV PARAGUAY (tvv.lat) — SMART TV MOTOR DE NAVEGACIÓN Y REPRODUCTOR
 * Optimizado para control remoto (D-Pad, Teclas numéricas, OK, Back)
 */

(function () {
    'use strict';

    // ESTADO DE LA APLICACIÓN
    const state = {
        channels: [],
        filteredChannels: [],
        activeCategory: 'all',
        currentChannel: null,
        currentChannelIndex: -1,
        focusedIndex: 0,
        focusedZone: 'grid', // 'grid' | 'categories' | 'player_hud'
        favorites: new Set(),
        numberBuffer: '',
        numberTimer: null,
        hudTimer: null,
        playbackTimeout: null,
        networkRetryCount: 0,
        mediaRecoveryCount: 0,
        usedProxy: false,
        hls: null,
        isPlaying: false,
        isDrawerOpen: false,
        drawerActiveCategory: 'all',
        proxyChannels: new Set(),
        prefetchTimer: null
    };

    // ELEMENTOS DEL DOM
    const dom = {
        grid: document.getElementById('channels-grid'),
        categoriesBar: document.getElementById('categories-bar'),
        clock: document.getElementById('clock-display'),
        counter: document.getElementById('channel-counter'),
        sectionTitle: document.getElementById('current-section-title'),
        
        // Overlay Numérico
        numericOverlay: document.getElementById('numeric-overlay'),
        numericDigits: document.getElementById('numeric-digits'),

        // Reproductor
        playerModal: document.getElementById('player-modal'),
        playerWrapper: document.getElementById('player-wrapper'),
        video: document.getElementById('tv-video'),
        iframe: document.getElementById('tv-iframe'),
        loader: document.getElementById('player-loader'),
        loaderText: document.getElementById('loader-text'),
        loaderBadgeCh: document.getElementById('loader-badge-ch'),
        loaderBadgeName: document.getElementById('loader-badge-name'),
        hud: document.getElementById('player-hud'),
        hudChannelInfo: document.getElementById('hud-channel-info'),
        hudNum: document.getElementById('hud-channel-num'),
        hudName: document.getElementById('hud-channel-name'),
        hudCat: document.getElementById('hud-channel-cat'),
        
        // Botones del HUD (Estilo YouTube)
        btnClosePlayer: document.getElementById('btn-close-player'),
        btnToggleFav: document.getElementById('btn-toggle-favorite'),
        iconFavSvg: document.getElementById('icon-fav-svg'),
        btnPrev: document.getElementById('btn-prev-channel'),
        btnRewind10: document.getElementById('btn-rewind-10'),
        btnPlayPause: document.getElementById('btn-play-pause'),
        iconPlayPauseSvg: document.getElementById('icon-play-pause-svg'),
        btnForward10: document.getElementById('btn-forward-10'),
        btnNext: document.getElementById('btn-next-channel'),
        btnOpenDrawer: document.getElementById('btn-open-channel-list'),
        btnFullscreen: document.getElementById('btn-fullscreen'),
        iconFullscreenSvg: document.getElementById('icon-fullscreen-svg'),
        
        // Drawer de Canales en Reproducción
        drawer: document.getElementById('player-channels-drawer'),
        drawerCloseBtn: document.getElementById('btn-close-drawer'),
        drawerCategories: document.getElementById('drawer-categories'),
        drawerChannelsList: document.getElementById('drawer-channels-list'),
        drawerCounter: document.getElementById('drawer-counter'),

        // Error
        errorBox: document.getElementById('player-error'),
        btnRetry: document.getElementById('btn-retry-channel'),
        btnErrorBack: document.getElementById('btn-error-back'),

        // Banner Desmutear (Autoplay silenciado)
        unmuteBanner: document.getElementById('unmute-banner'),
        btnUnmute: document.getElementById('btn-unmute')
    };

    // ==========================================================================
    // 1. INICIALIZACIÓN
    // ==========================================================================
    async function init() {
        loadFavoritesFromStorage();
        loadProxyChannelsFromStorage();
        startClock();
        setupEventListeners();
        await loadChannels();

        // Auto-reproducción al entrar a tvv.lat: sintonizar automáticamente el canal 1
        if (state.channels && state.channels.length > 0) {
            playChannel(state.channels[0]);
        }
    }

    // ==========================================================================
    // 2. CARGA DE CANALES
    // ==========================================================================
    async function loadChannels() {
        try {
            const resp = await fetch('channels.json');
            const data = await resp.json();
            state.channels = data.channels || [];
            
            dom.counter.textContent = `${state.channels.length} Canales`;
            filterByCategory('all');
            
            // Foco inicial en la primera tarjeta
            setTimeout(() => {
                setFocusOnGridItem(0);
            }, 100);

        } catch (err) {
            console.error('Error al cargar channels.json:', err);
            dom.grid.innerHTML = `
                <div class="player-error" style="position:static; transform:none; margin: 40px auto;">
                    <h3>No se pudieron cargar los canales</h3>
                    <p>Comprueba la conexión de red de tu Smart TV.</p>
                </div>`;
        }
    }

    function filterByCategory(category) {
        state.activeCategory = category;

        if (category === 'all') {
            state.filteredChannels = [...state.channels];
            dom.sectionTitle.textContent = 'Todos los Canales en Vivo';
        } else if (category === 'favorites') {
            state.filteredChannels = state.channels.filter(ch => state.favorites.has(ch.name));
            dom.sectionTitle.textContent = `⭐ Mis Canales Favoritos (${state.filteredChannels.length})`;
        } else {
            state.filteredChannels = state.channels.filter(ch => ch.category === category);
            dom.sectionTitle.textContent = `${category} (${state.filteredChannels.length})`;
        }

        renderChannels();
        updateActiveCategoryPill();
    }

    function renderChannels() {
        dom.grid.innerHTML = '';

        if (state.filteredChannels.length === 0) {
            dom.grid.innerHTML = `
                <div style="grid-column: 1 / -1; text-align: center; padding: 60px 20px; color: var(--text-secondary);">
                    <p style="font-size: 20px; margin-bottom: 8px;">No hay canales en esta sección.</p>
                    <p style="font-size: 14px; color: var(--text-muted);">Usa las flechas del control para cambiar de categoría.</p>
                </div>`;
            return;
        }

        const fragment = document.createDocumentFragment();

        state.filteredChannels.forEach((ch, index) => {
            const card = document.createElement('div');
            card.className = 'channel-card focusable';
            card.tabIndex = 0;
            card.dataset.index = index;

            const isFav = state.favorites.has(ch.name);
            const numPadded = String(ch.number).padStart(2, '0');

            card.innerHTML = `
                <div class="card-top">
                    <span class="channel-number">CH ${numPadded}</span>
                    <span class="card-live-dot" title="En Vivo"></span>
                </div>
                <div class="card-middle">
                    <h3 class="channel-title">${escapeHtml(ch.name)}</h3>
                </div>
                <div class="card-bottom">
                    <span class="channel-category-tag">${escapeHtml(ch.category)}</span>
                    <span class="fav-btn-icon ${isFav ? 'is-favorite' : ''}">★</span>
                </div>
            `;

            card.addEventListener('click', () => {
                playChannel(ch);
            });

            card.addEventListener('mouseenter', () => {
                if (state.focusedZone === 'grid') {
                    setFocusOnGridItem(index);
                }
            });

            fragment.appendChild(card);
        });

        dom.grid.appendChild(fragment);
    }

    function updateActiveCategoryPill() {
        const pills = dom.categoriesBar.querySelectorAll('.cat-pill');
        pills.forEach(pill => {
            if (pill.dataset.category === state.activeCategory) {
                pill.classList.add('active');
            } else {
                pill.classList.remove('active');
            }
        });

        // Sincronizar también los botones de la barra inferior móvil
        const mobileBtns = document.querySelectorAll('.mobile-nav-btn');
        mobileBtns.forEach(btn => {
            if (btn.dataset.category === state.activeCategory) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }

    // ==========================================================================
    // 3. REPRODUCTOR DE VIDEO (HLS.JS + NATIVO SMART TV)
    // ==========================================================================
    function playChannel(channel) {
        if (!channel || !channel.url) return;

        state.currentChannel = channel;
        state.currentChannelIndex = state.channels.findIndex(c => c.name === channel.name);
        state.isPlaying = true;
        state.networkRetryCount = 0;
        state.mediaRecoveryCount = 0;

        if (state.playbackTimeout) {
            clearTimeout(state.playbackTimeout);
            state.playbackTimeout = null;
        }

        // Mostrar modal y HUD
        dom.playerModal.classList.remove('hidden');
        document.body.classList.add('mobile-playing');
        dom.errorBox.classList.add('hidden');
        dom.loader.classList.remove('hidden');
        if (dom.loaderBadgeCh) dom.loaderBadgeCh.textContent = `CH ${String(channel.number).padStart(2, '0')}`;
        if (dom.loaderBadgeName) dom.loaderBadgeName.textContent = channel.name;
        dom.loaderText.textContent = `Sintonizando ${channel.name}...`;

        // Soporte para botón "Atrás" en celulares y navegadores
        try {
            if (!history.state || !history.state.playerOpen) {
                history.pushState({ playerOpen: true }, '');
            }
        } catch (e) {}

        // Actualizar datos del HUD
        dom.hudNum.textContent = `CH ${String(channel.number).padStart(2, '0')}`;
        dom.hudName.textContent = channel.name;
        dom.hudCat.textContent = channel.category;
        if (dom.iconPlayPauseSvg) {
            dom.iconPlayPauseSvg.innerHTML = '<path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>';
        }
        updateFavoriteButtonState();
        if (state.isDrawerOpen) {
            renderDrawerChannels();
        }

        showHudTemporary();

        // Destruir reproductor HLS anterior si existía
        if (state.hls) {
            state.hls.destroy();
            state.hls = null;
        }
        dom.video.pause();

        // Canales Dailymotion: reproducir mediante el reproductor embebido /player/dm que spoofea el referer oficial
        if (channel.url.startsWith('dailymotion://')) {
            const raw = channel.url.replace('dailymotion://', '').trim();
            const parts = raw.split('?');
            const videoId = parts[0].trim();
            let ref = 'https://trece.com.py/';
            let playerId = 'x1at3a';

            if (channel.name.toLowerCase().includes('unicanal')) {
                ref = 'https://www.unicanal.com.py/';
                playerId = 'x1apn6';
            } else if (channel.name.toLowerCase().includes('abc')) {
                ref = 'https://www.abc.com.py/';
                playerId = 'x1b1gw';
            }

            dom.video.classList.add('hidden');
            dom.iframe.classList.remove('hidden');
            dom.iframe.src = `/player/dm?v=${encodeURIComponent(videoId)}&p=${playerId}&ref=${encodeURIComponent(ref)}`;
            dom.loader.classList.add('hidden');

            state.focusedZone = 'player_hud';
            dom.btnClosePlayer.focus();
            return;
        }

        // Asegurar que el iframe esté oculto y el video visible para todos los demás canales HLS
        if (dom.iframe) {
            dom.iframe.removeAttribute('src');
            dom.iframe.classList.add('hidden');
        }
        dom.video.classList.remove('hidden');

        // Determinar URL de arranque: HTTP, desdeparaguay:// o canal memorizado que requiere proxy
        let streamUrl = channel.url;
        if (channel.url.startsWith('http://') || channel.url.startsWith('desdeparaguay://') || state.proxyChannels.has(channel.name)) {
            state.usedProxy = true;
            streamUrl = `/api/proxy?url=${encodeURIComponent(channel.url)}`;
        } else {
            state.usedProxy = false;
        }

        // Temporizador de seguridad de 20 segundos (permite buffering estable sin abortos prematuros)
        state.playbackTimeout = setTimeout(() => {
            if (state.isPlaying && dom.video.currentTime === 0) {
                if (!state.usedProxy) {
                    console.warn('Timeout en directo, cambiando a proxy seguro...');
                    state.usedProxy = true;
                    rememberProxyChannel(channel.name);
                    const proxied = `/api/proxy?url=${encodeURIComponent(channel.url)}`;
                    if (state.hls) {
                        state.hls.loadSource(proxied);
                        state.hls.startLoad();
                    } else {
                        dom.video.src = proxied;
                        dom.video.play().catch(() => {});
                    }
                } else {
                    handleStreamFatalError('La señal en vivo tardó demasiado en responder.');
                }
            }
        }, 20000);

        function onPlaybackStarted() {
            if (state.playbackTimeout) {
                clearTimeout(state.playbackTimeout);
                state.playbackTimeout = null;
            }
            state.mediaRecoveryCount = 0;
            dom.loader.classList.add('hidden');

            // Si funcionó a través del proxy, memorizarlo para arranques futuros instantáneos
            if (state.usedProxy && channel && channel.name) {
                rememberProxyChannel(channel.name);
            }

            // Precarga inteligente del manifiesto de canales adyacentes tras 2.5s estables
            if (state.prefetchTimer) clearTimeout(state.prefetchTimer);
            state.prefetchTimer = setTimeout(() => {
                prefetchAdjacentManifests();
            }, 2500);
        }

        dom.video.onplaying = onPlaybackStarted;

        if (Hls.isSupported()) {
            state.hls = new Hls({
                enableWorker: true,              // Demuxing en hilo secundario Web Worker (sin lag en la UI móvil)
                lowLatencyMode: false,           // Desactivar baja latencia agresiva para transmisiones deportivas de alto bitrate (evita tirones en ESPN)
                backBufferLength: 20,            // Buffer posterior saludable
                maxBufferLength: 30,             // Buffer inicial de 30s hacia adelante (absorbe variaciones de red y proxy)
                maxMaxBufferLength: 60,          // Tope de buffer amplio para máxima estabilidad
                maxBufferSize: 60 * 1000 * 1000, // 60MB límite de memoria de buffer
                liveSyncDurationCount: 3,        // Margen de seguridad de 3 segmentos (~9s) para cero micro-cortes
                liveMaxLatencyDurationCount: 8,
                maxBufferHole: 0.5,              // Tolerancia adecuada para huecos de PTS/DTS
                highBufferWatchdogPeriod: 3,
                nudgeOffset: 0.1,
                nudgeMaxRetry: 5,
                maxFragLookUpTolerance: 0.25,
                maxAudioFramesDrift: 1.0,
                fragLoadingTimeOut: 20000,
                manifestLoadingTimeOut: 15000,
                levelLoadingTimeOut: 15000,
                fragLoadingMaxRetry: 4,
                manifestLoadingMaxRetry: 4,
                levelLoadingMaxRetry: 4,
                startFragPrefetch: true,         // Precarga anticipada del siguiente segmento
                initialLiveManifestSize: 1,      // Iniciar con el primer manifiesto parsed
                progressive: true,
                testBandwidth: false             // No retrasar el inicio para medir ancho de banda
            });

            state.hls.loadSource(streamUrl);
            state.hls.attachMedia(dom.video);

            state.hls.on(Hls.Events.MANIFEST_LOADED, function () {
                if (dom.loaderText && !dom.loader.classList.contains('hidden')) {
                    dom.loaderText.textContent = 'Descargando transmisión...';
                }
            });

            state.hls.on(Hls.Events.FRAG_LOADED, function () {
                if (dom.loaderText && !dom.loader.classList.contains('hidden')) {
                    dom.loaderText.textContent = 'Iniciando video...';
                }
            });

            state.hls.on(Hls.Events.MANIFEST_PARSED, function () {
                onPlaybackStarted();
                dom.video.play().catch(e => {
                    console.log('Autoplay con sonido bloqueado por navegador, intentando silenciado:', e);
                    // Si el navegador bloqueó la reproducción automática con audio:
                    dom.video.muted = true;
                    dom.video.play().then(() => {
                        if (dom.unmuteBanner) dom.unmuteBanner.classList.remove('hidden');
                    }).catch(() => {});
                });
            });

            state.hls.on(Hls.Events.ERROR, function (event, data) {
                console.warn('Hls Event Error:', data.type, data.details, 'fatal:', data.fatal);

                // Si el error no es fatal, el pipeline de Hls.js lo maneja internamente
                if (!data.fatal) {
                    return;
                }

                // Errores fatales de decodificación de medios
                if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
                    state.mediaRecoveryCount++;
                    if (state.mediaRecoveryCount <= 2) {
                        console.warn(`Recuperando decodificador de medios fatal (intento ${state.mediaRecoveryCount}/2)...`);
                        state.hls.recoverMediaError();
                        dom.video.play().catch(() => {});
                        return;
                    } else if (state.mediaRecoveryCount === 3) {
                        console.warn('Cambiando perfil de decodificación de audio...');
                        state.hls.swapAudioCodec();
                        state.hls.recoverMediaError();
                        dom.video.play().catch(() => {});
                        return;
                    } else {
                        // Solo mostrar error fatal si el video realmente no está reproduciéndose
                        if (dom.video.currentTime === 0 || dom.video.paused) {
                            if (state.playbackTimeout) clearTimeout(state.playbackTimeout);
                            handleStreamFatalError('Este canal no es compatible con el decodificador de este navegador.');
                        } else {
                            console.warn('Error de medios recuperable, continuando reproducción fluida...');
                        }
                        return;
                    }
                }

                // Errores fatales de red (CORS, URLs caídas o bloqueo HTTP)
                if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
                    if (!state.usedProxy) {
                        console.warn('Fallo de red en directo (CORS o Mixed Content). Activando Proxy seguro...');
                        state.usedProxy = true;
                        rememberProxyChannel(channel.name);
                        const proxied = `/api/proxy?url=${encodeURIComponent(channel.url)}`;
                        state.hls.loadSource(proxied);
                        state.hls.startLoad();
                    } else {
                        state.networkRetryCount++;
                        if (state.networkRetryCount <= 2) {
                            console.warn(`Reintentando carga proxy (${state.networkRetryCount}/2)...`);
                            state.hls.startLoad();
                        } else {
                            if (state.playbackTimeout) clearTimeout(state.playbackTimeout);
                            handleStreamFatalError('Este canal no se encuentra emitiendo en este momento.');
                        }
                    }
                    return;
                }

                // Otros errores fatales no contemplados
                if (dom.video.currentTime === 0 || dom.video.paused) {
                    if (state.playbackTimeout) clearTimeout(state.playbackTimeout);
                    handleStreamFatalError();
                }
            });

        } else if (dom.video.canPlayType('application/vnd.apple.mpegurl')) {
            // Soporte nativo para Safari y ciertos motores de Smart TV (Tizen/webOS)
            dom.video.src = streamUrl;
            dom.video.addEventListener('loadedmetadata', function () {
                onPlaybackStarted();
                dom.video.play().catch(e => {
                    console.log('Autoplay nativo con sonido bloqueado, intentando silenciado:', e);
                    dom.video.muted = true;
                    dom.video.play().then(() => {
                        if (dom.unmuteBanner) dom.unmuteBanner.classList.remove('hidden');
                    }).catch(() => {});
                });
            }, { once: true });

            dom.video.addEventListener('error', function () {
                if (!state.usedProxy) {
                    state.usedProxy = true;
                    rememberProxyChannel(channel.name);
                    dom.video.src = `/api/proxy?url=${encodeURIComponent(channel.url)}`;
                    dom.video.load();
                    dom.video.play().catch(() => {});
                } else {
                    if (state.playbackTimeout) clearTimeout(state.playbackTimeout);
                    handleStreamFatalError();
                }
            }, { once: true });
        } else {
            handleStreamFatalError('Este televisor no soporta reproducción HLS.');
        }

        // Foco al reproductor
        state.focusedZone = 'player_hud';
        if (dom.btnPlayPause) {
            dom.btnPlayPause.focus();
        } else if (dom.btnClosePlayer) {
            dom.btnClosePlayer.focus();
        }
    }

    function handleUnmute() {
        if (dom.video) {
            dom.video.muted = false;
        }
        if (dom.unmuteBanner) {
            dom.unmuteBanner.classList.add('hidden');
        }
    }

    function closePlayer(fromPopstate = false) {
        try {
            // 1. Salir inmediatamente de pantalla completa si estaba activa (previene congelamiento en móviles)
            if (document.fullscreenElement || document.webkitFullscreenElement) {
                if (document.exitFullscreen) {
                    document.exitFullscreen().catch(() => {});
                } else if (document.webkitExitFullscreen) {
                    document.webkitExitFullscreen();
                }
            }
        } catch (e) {}

        // 2. Limpiar todos los temporizadores activos
        if (state.prefetchTimer) {
            clearTimeout(state.prefetchTimer);
            state.prefetchTimer = null;
        }
        if (state.playbackTimeout) {
            clearTimeout(state.playbackTimeout);
            state.playbackTimeout = null;
        }
        if (state.hudTimer) {
            clearTimeout(state.hudTimer);
            state.hudTimer = null;
        }

        // 3. Cerrar drawer de canales si estaba abierto
        if (state.isDrawerOpen) {
            try {
                closeChannelsDrawer();
            } catch (e) {}
        }

        // 4. Desbloquear scroll y viewport inmediatamente
        try {
            document.body.classList.remove('mobile-playing');
            document.body.style.overflow = '';
            document.documentElement.style.overflow = '';
        } catch (e) {}

        // 5. Ocultar modal del reproductor y cuadros flotantes
        try {
            dom.playerModal.classList.add('hidden');
            dom.errorBox.classList.add('hidden');
            dom.loader.classList.add('hidden');
            if (dom.unmuteBanner) dom.unmuteBanner.classList.add('hidden');
            dom.hud.classList.remove('autohidden');
        } catch (e) {}

        // 6. Restaurar estados lógicos
        state.isPlaying = false;
        state.focusedZone = 'grid';

        // 7. Navegar atrás en el historial del navegador si corresponde
        if (!fromPopstate && history.state && history.state.playerOpen) {
            try {
                history.back();
            } catch (e) {}
        }

        // 8. Desconectar iframe si estaba en uso
        if (dom.iframe) {
            try {
                dom.iframe.src = 'about:blank';
                dom.iframe.removeAttribute('src');
                dom.iframe.classList.add('hidden');
            } catch (e) {}
        }

        // 9. Destruir Hls de forma segura
        if (state.hls) {
            try {
                state.hls.destroy();
            } catch (e) {}
            state.hls = null;
        }

        // 10. Desconectar video limpiamente SIN disparar evento de error
        try {
            dom.video.onplaying = null;
            dom.video.onerror = null;
            dom.video.pause();
            dom.video.src = '';
            dom.video.removeAttribute('src');
            dom.video.classList.remove('hidden');
        } catch (e) {}

        // 11. Quitar foco retenido dentro del modal cerrado
        if (document.activeElement && dom.playerModal.contains(document.activeElement)) {
            try {
                document.activeElement.blur();
            } catch (e) {}
        }

        // 12. Restaurar foco al canal actual en la grilla sin desplazamiento forzado brusco
        try {
            if (state.currentChannel) {
                const idx = state.filteredChannels.findIndex(c => c.name === state.currentChannel.name);
                if (idx !== -1) {
                    setFocusOnGridItem(idx, false);
                }
            }
        } catch (e) {}
    }

    function handleStreamFatalError(customMsg) {
        if (state.playbackTimeout) {
            clearTimeout(state.playbackTimeout);
            state.playbackTimeout = null;
        }
        dom.loader.classList.add('hidden');
        dom.errorBox.classList.remove('hidden');
        if (customMsg) {
            document.getElementById('error-msg').textContent = customMsg;
        }
        dom.btnRetry.focus();
    }

    function isHudVisible() {
        return dom.hud && !dom.hud.classList.contains('autohidden') && !dom.playerModal.classList.contains('hidden');
    }

    function wrapHudAction(actionFn) {
        return function (e) {
            if (e) e.stopPropagation();
            if (!isHudVisible()) {
                // Si los botones no están visibles, el toque solo debe mostrarlos
                showHudTemporary();
                return;
            }
            actionFn(e);
            showHudTemporary();
        };
    }

    function showHudTemporary(duration = 4000) {
        dom.hud.classList.remove('autohidden');
        if (state.hudTimer) clearTimeout(state.hudTimer);

        state.hudTimer = setTimeout(() => {
            if (state.isPlaying && dom.errorBox.classList.contains('hidden') && !state.isDrawerOpen) {
                dom.hud.classList.add('autohidden');
            }
        }, duration);
    }

    function togglePlayPause() {
        if (dom.video.paused) {
            dom.video.play().catch(() => {});
            if (dom.iconPlayPauseSvg) {
                dom.iconPlayPauseSvg.innerHTML = '<path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>';
            }
            dom.btnPlayPause.setAttribute('title', 'Pausar (OK)');
        } else {
            dom.video.pause();
            if (dom.iconPlayPauseSvg) {
                dom.iconPlayPauseSvg.innerHTML = '<path d="M8 5v14l11-7z"/>';
            }
            dom.btnPlayPause.setAttribute('title', 'Reanudar (OK)');
        }
        showHudTemporary();
    }

    function rewind10() {
        try {
            if (dom.video) {
                const seekable = dom.video.seekable;
                if (seekable && seekable.length > 0) {
                    const start = seekable.start(seekable.length - 1);
                    dom.video.currentTime = Math.max(start, dom.video.currentTime - 10);
                } else if (dom.video.currentTime > 10) {
                    dom.video.currentTime -= 10;
                }
            }
        } catch (e) {
            console.log('Rewind not supported on this stream', e);
        }
        showHudTemporary();
    }

    function forward10() {
        try {
            if (dom.video) {
                const seekable = dom.video.seekable;
                if (seekable && seekable.length > 0) {
                    const end = seekable.end(seekable.length - 1);
                    dom.video.currentTime = Math.min(end, dom.video.currentTime + 10);
                } else {
                    dom.video.currentTime += 10;
                }
            }
        } catch (e) {
            console.log('Forward not supported on this stream', e);
        }
        showHudTemporary();
    }

    function nextChannel() {
        if (!state.currentChannel || state.channels.length === 0) return;
        let nextIdx = (state.currentChannelIndex + 1) % state.channels.length;
        playChannel(state.channels[nextIdx]);
    }

    function prevChannel() {
        if (!state.currentChannel || state.channels.length === 0) return;
        let prevIdx = (state.currentChannelIndex - 1 + state.channels.length) % state.channels.length;
        playChannel(state.channels[prevIdx]);
    }

    function toggleFullscreen() {
        if (!document.fullscreenElement && !document.webkitFullscreenElement) {
            if (dom.playerWrapper.requestFullscreen) {
                dom.playerWrapper.requestFullscreen().catch(err => {
                    if (dom.video.webkitEnterFullscreen) dom.video.webkitEnterFullscreen();
                });
            } else if (dom.video.webkitEnterFullscreen) {
                dom.video.webkitEnterFullscreen();
            }
        } else {
            if (document.exitFullscreen) {
                document.exitFullscreen().catch(() => {});
            } else if (document.webkitExitFullscreen) {
                document.webkitExitFullscreen();
            }
        }
    }

    function updateFullscreenIcon() {
        if (!dom.iconFullscreenSvg) return;
        const isFs = !!(document.fullscreenElement || document.webkitFullscreenElement);
        if (isFs) {
            dom.iconFullscreenSvg.innerHTML = '<path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/>';
            dom.btnFullscreen.setAttribute('title', 'Salir de Pantalla Completa (Esc)');
        } else {
            dom.iconFullscreenSvg.innerHTML = '<path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/>';
            dom.btnFullscreen.setAttribute('title', 'Pantalla Completa (Enter)');
        }
    }

    // ==========================================================================
    // 3.1 GUÍA DE CANALES EN REPRODUCCIÓN (DRAWER FLOTANTE)
    // ==========================================================================
    function openChannelsDrawer() {
        if (!dom.drawer) return;
        state.isDrawerOpen = true;
        dom.drawer.classList.remove('hidden');
        renderDrawerChannels();

        // Si el HUD estaba oculto, mostrarlo para ver la barra superior
        dom.hud.classList.remove('autohidden');
        if (state.hudTimer) clearTimeout(state.hudTimer);

        // Auto-scroll suave hasta el canal activo en reproducción
        setTimeout(() => {
            if (!dom.drawerChannelsList) return;
            const activeItem = dom.drawerChannelsList.querySelector('.drawer-channel-item.active');
            if (activeItem) {
                activeItem.scrollIntoView({ block: 'center', behavior: 'smooth' });
                activeItem.focus();
            } else {
                const first = dom.drawerChannelsList.querySelector('.drawer-channel-item');
                if (first) first.focus();
            }
        }, 100);
    }

    function closeChannelsDrawer() {
        if (!dom.drawer) return;
        state.isDrawerOpen = false;
        dom.drawer.classList.add('hidden');
        if (state.isPlaying) {
            if (dom.btnOpenDrawer) {
                dom.btnOpenDrawer.focus();
            } else if (dom.btnPlayPause) {
                dom.btnPlayPause.focus();
            }
            showHudTemporary();
        }
    }

    function renderDrawerChannels() {
        if (!dom.drawerChannelsList) return;

        let filtered = [];
        if (state.drawerActiveCategory === 'all') {
            filtered = [...state.channels];
        } else if (state.drawerActiveCategory === 'favorites') {
            filtered = state.channels.filter(ch => state.favorites.has(ch.name));
        } else {
            filtered = state.channels.filter(ch => ch.category === state.drawerActiveCategory);
        }

        if (dom.drawerCounter) {
            dom.drawerCounter.textContent = filtered.length;
        }

        // Sincronizar pills activas en el drawer
        if (dom.drawerCategories) {
            const pills = dom.drawerCategories.querySelectorAll('.drawer-cat-pill');
            pills.forEach(p => {
                if (p.dataset.category === state.drawerActiveCategory) {
                    p.classList.add('active');
                } else {
                    p.classList.remove('active');
                }
            });
        }

        dom.drawerChannelsList.innerHTML = '';

        if (filtered.length === 0) {
            dom.drawerChannelsList.innerHTML = `
                <div style="text-align: center; padding: 40px 16px; color: var(--text-secondary);">
                    <p style="font-size: 15px; margin-bottom: 6px;">No hay canales en esta sección</p>
                    <p style="font-size: 12px; color: var(--text-muted);">Elige otra categoría en la barra superior.</p>
                </div>`;
            return;
        }

        const fragment = document.createDocumentFragment();

        filtered.forEach(ch => {
            const isCurrent = state.currentChannel && state.currentChannel.name === ch.name;
            const isFav = state.favorites.has(ch.name);
            const numPadded = String(ch.number).padStart(2, '0');

            const item = document.createElement('div');
            item.className = `drawer-channel-item focusable ${isCurrent ? 'active' : ''}`;
            item.tabIndex = 0;
            item.setAttribute('role', 'button');
            item.setAttribute('title', `Reproducir ${ch.name}`);

            item.innerHTML = `
                <div class="drawer-item-left">
                    <span class="drawer-ch-num">CH ${numPadded}</span>
                    <div class="drawer-ch-info">
                        <span class="drawer-ch-name">${escapeHtml(ch.name)}</span>
                        <span class="drawer-ch-cat">${escapeHtml(ch.category)}</span>
                    </div>
                </div>
                <div class="drawer-item-right">
                    ${isCurrent ? '<span class="drawer-playing-tag"><span class="pulse-dot"></span> EN VIVO</span>' : ''}
                    ${isFav ? '<span class="drawer-fav-star">★</span>' : ''}
                </div>
            `;

            item.addEventListener('click', (e) => {
                e.stopPropagation();
                playChannel(ch);
                closeChannelsDrawer();
            });

            fragment.appendChild(item);
        });

        dom.drawerChannelsList.appendChild(fragment);
    }

    // ==========================================================================
    // 4. MOTOR DE NAVEGACIÓN D-PAD Y CONTROL REMOTO SMART TV
    // ==========================================================================
    function setupEventListeners() {
        window.addEventListener('keydown', handleSmartTvKeydown);

        // Controles HUD: btnClosePlayer cierra directamente sin reabrir HUD
        dom.btnClosePlayer.addEventListener('click', (e) => {
            if (e) e.stopPropagation();
            closePlayer(false);
        });
        dom.btnPlayPause.addEventListener('click', wrapHudAction(togglePlayPause));
        dom.btnNext.addEventListener('click', wrapHudAction(nextChannel));
        dom.btnPrev.addEventListener('click', wrapHudAction(prevChannel));
        if (dom.btnRewind10) dom.btnRewind10.addEventListener('click', wrapHudAction(rewind10));
        if (dom.btnForward10) dom.btnForward10.addEventListener('click', wrapHudAction(forward10));
        if (dom.btnOpenDrawer) dom.btnOpenDrawer.addEventListener('click', wrapHudAction(openChannelsDrawer));
        if (dom.hudChannelInfo) dom.hudChannelInfo.addEventListener('click', wrapHudAction(openChannelsDrawer));
        if (dom.drawerCloseBtn) dom.drawerCloseBtn.addEventListener('click', closeChannelsDrawer);
        dom.btnFullscreen.addEventListener('click', wrapHudAction(toggleFullscreen));
        document.addEventListener('fullscreenchange', updateFullscreenIcon);
        document.addEventListener('webkitfullscreenchange', updateFullscreenIcon);

        // Botón para desmutear transmisión si el navegador bloqueó audio
        if (dom.btnUnmute) {
            dom.btnUnmute.addEventListener('click', (e) => {
                if (e) e.stopPropagation();
                handleUnmute();
            });
        }

        // Categorías del Drawer
        if (dom.drawerCategories) {
            dom.drawerCategories.addEventListener('click', (e) => {
                const pill = e.target.closest('.drawer-cat-pill');
                if (pill && pill.dataset.category) {
                    state.drawerActiveCategory = pill.dataset.category;
                    renderDrawerChannels();
                }
            });
        }

        dom.btnToggleFav.addEventListener('click', wrapHudAction(() => {
            if (state.currentChannel) {
                toggleFavorite(state.currentChannel.name);
                updateFavoriteButtonState();
                renderChannels();
                if (state.isDrawerOpen) {
                    renderDrawerChannels();
                }
            }
        }));

        // Error actions
        dom.btnRetry.addEventListener('click', () => {
            if (state.currentChannel) playChannel(state.currentChannel);
        });
        dom.btnErrorBack.addEventListener('click', () => closePlayer(false));

        // Tocar video/pantalla para mostrar u ocultar HUD (Móvil y Escritorio)
        let lastTapTimestamp = 0;
        function handleScreenTap(e) {
            if (e.target.closest('#player-error')) return;
            if (e.target.closest('#player-channels-drawer')) return;
            if (e.target.closest('#btn-unmute')) return;

            // Si el video arrancó silenciado y el usuario toca la pantalla, activar sonido
            if (dom.video && dom.video.muted && dom.unmuteBanner && !dom.unmuteBanner.classList.contains('hidden')) {
                handleUnmute();
            }

            // Si la lista de canales está abierta y se toca fuera de ella, se cierra
            if (state.isDrawerOpen) {
                if (!e.target.closest('#btn-open-channel-list') && !e.target.closest('#hud-channel-info')) {
                    closeChannelsDrawer();
                    return;
                }
            }

            const now = Date.now();
            if (now - lastTapTimestamp < 250) return;
            lastTapTimestamp = now;

            if (!isHudVisible()) {
                // Si el HUD está oculto, cualquier toque en la pantalla lo hace aparecer
                if (e.cancelable && e.type === 'touchend') e.preventDefault();
                showHudTemporary();
            } else if (!e.target.closest('button') && !e.target.closest('#hud-channel-info')) {
                // Si ya está visible y el toque NO fue en un botón ni en info, ocultar de inmediato
                dom.hud.classList.add('autohidden');
            }
        }

        dom.hud.addEventListener('click', handleScreenTap);
        dom.playerWrapper.addEventListener('click', handleScreenTap);
        dom.hud.addEventListener('touchend', (e) => {
            if (e.target.closest('#player-channels-drawer')) return;
            if (!isHudVisible()) {
                const now = Date.now();
                if (now - lastTapTimestamp < 250) return;
                lastTapTimestamp = now;
                if (e.cancelable) e.preventDefault();
                showHudTemporary();
            }
        }, { passive: false });

        // Movimiento de mouse muestra HUD
        window.addEventListener('mousemove', () => {
            if (state.isPlaying) showHudTemporary();
        });

        // Botón físico / gesto "Atrás" de Android y navegadores
        window.addEventListener('popstate', () => {
            if (state.isDrawerOpen) {
                closeChannelsDrawer();
            } else if (state.isPlaying) {
                closePlayer(true);
            }
        });

        // Clic en categorías
        dom.categoriesBar.addEventListener('click', (e) => {
            const pill = e.target.closest('.cat-pill');
            if (pill) {
                filterByCategory(pill.dataset.category);
            }
        });

        // Clic en barra inferior móvil
        const mobileNav = document.getElementById('mobile-bottom-nav');
        if (mobileNav) {
            mobileNav.addEventListener('click', (e) => {
                const btn = e.target.closest('.mobile-nav-btn');
                if (btn) {
                    filterByCategory(btn.dataset.category);
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                }
            });
        }

        // Auto-fullscreen al girar el celular a horizontal (landscape)
        window.addEventListener('orientationchange', () => {
            if (window.innerWidth <= 768 && state.isPlaying) {
                if (window.orientation === 90 || window.orientation === -90) {
                    dom.playerWrapper.requestFullscreen().catch(() => {});
                } else if (window.orientation === 0) {
                    if (document.fullscreenElement) {
                        document.exitFullscreen().catch(() => {});
                    }
                }
            }
        });
    }

    function handleSmartTvKeydown(e) {
        const key = e.key;

        // 1. TECLAS NUMÉRICAS DIRECTAS (0-9)
        if (/^[0-9]$/.test(key)) {
            handleNumberInput(key);
            return;
        }

        // 2. EN MODO REPRODUCTOR
        if (state.isPlaying) {
            // Si el video arrancó silenciado por política del navegador, cualquier tecla activa el audio
            if (dom.video && dom.video.muted && dom.unmuteBanner && !dom.unmuteBanner.classList.contains('hidden')) {
                handleUnmute();
            }

            // NAVEGACIÓN DENTRO DEL DRAWER DE CANALES
            if (state.isDrawerOpen) {
                switch (key) {
                    case 'Escape':
                    case 'Backspace':
                    case 'GoBack':
                    case 'BrowserBack':
                    case 'l':
                    case 'L':
                        e.preventDefault();
                        closeChannelsDrawer();
                        return;
                    case 'ArrowUp': {
                        e.preventDefault();
                        const items = Array.from(dom.drawerChannelsList.querySelectorAll('.drawer-channel-item'));
                        const activeEl = document.activeElement;
                        const idx = items.indexOf(activeEl);
                        if (idx > 0) {
                            items[idx - 1].focus();
                            items[idx - 1].scrollIntoView({ block: 'nearest' });
                        } else if (idx === 0 && dom.drawerCloseBtn) {
                            dom.drawerCloseBtn.focus();
                        }
                        return;
                    }
                    case 'ArrowDown': {
                        e.preventDefault();
                        const items = Array.from(dom.drawerChannelsList.querySelectorAll('.drawer-channel-item'));
                        const activeEl = document.activeElement;
                        const idx = items.indexOf(activeEl);
                        if (idx >= 0 && idx < items.length - 1) {
                            items[idx + 1].focus();
                            items[idx + 1].scrollIntoView({ block: 'nearest' });
                        } else if (idx === -1 && items.length > 0) {
                            items[0].focus();
                            items[0].scrollIntoView({ block: 'nearest' });
                        }
                        return;
                    }
                    case 'ArrowLeft':
                    case 'ArrowRight': {
                        const pills = Array.from(dom.drawerCategories.querySelectorAll('.drawer-cat-pill'));
                        const activeEl = document.activeElement;
                        const pillIdx = pills.indexOf(activeEl);
                        if (pillIdx !== -1) {
                            e.preventDefault();
                            if (key === 'ArrowLeft' && pillIdx > 0) {
                                pills[pillIdx - 1].focus();
                            } else if (key === 'ArrowRight' && pillIdx < pills.length - 1) {
                                pills[pillIdx + 1].focus();
                            }
                        }
                        return;
                    }
                }
                return;
            }

            showHudTemporary();

            const centerButtons = [dom.btnPrev, dom.btnRewind10, dom.btnPlayPause, dom.btnForward10, dom.btnNext].filter(Boolean);
            const active = document.activeElement;

            switch (key) {
                case 'Escape':
                case 'Backspace':
                case 'GoBack':
                case 'BrowserBack':
                    e.preventDefault();
                    closePlayer();
                    break;
                case 'l':
                case 'L':
                    e.preventDefault();
                    openChannelsDrawer();
                    break;
                case 'ArrowLeft':
                    if (active && centerButtons.includes(active)) {
                        e.preventDefault();
                        const idx = centerButtons.indexOf(active);
                        if (idx > 0) centerButtons[idx - 1].focus();
                        return;
                    } else if (active === dom.btnToggleFav) {
                        e.preventDefault();
                        dom.btnClosePlayer.focus();
                        return;
                    } else if (active === dom.btnFullscreen && dom.btnOpenDrawer) {
                        e.preventDefault();
                        dom.btnOpenDrawer.focus();
                        return;
                    }
                    e.preventDefault();
                    prevChannel();
                    break;
                case 'ArrowRight':
                    if (active && centerButtons.includes(active)) {
                        e.preventDefault();
                        const idx = centerButtons.indexOf(active);
                        if (idx < centerButtons.length - 1) centerButtons[idx + 1].focus();
                        return;
                    } else if (active === dom.btnClosePlayer) {
                        e.preventDefault();
                        dom.btnToggleFav.focus();
                        return;
                    } else if (active === dom.btnOpenDrawer && dom.btnFullscreen) {
                        e.preventDefault();
                        dom.btnFullscreen.focus();
                        return;
                    }
                    e.preventDefault();
                    nextChannel();
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    if (active === dom.btnFullscreen || active === dom.btnOpenDrawer) {
                        dom.btnPlayPause.focus();
                    } else {
                        dom.btnClosePlayer.focus();
                    }
                    break;
                case 'ArrowDown':
                    e.preventDefault();
                    if (active === dom.btnClosePlayer || active === dom.btnToggleFav) {
                        dom.btnPlayPause.focus();
                    } else if (dom.btnOpenDrawer) {
                        dom.btnOpenDrawer.focus();
                    } else {
                        dom.btnFullscreen.focus();
                    }
                    break;
                case 'Enter':
                    if (active && active.classList.contains('focusable')) {
                        return;
                    }
                    togglePlayPause();
                    break;
                case ' ':
                    e.preventDefault();
                    togglePlayPause();
                    break;
                case 'MediaPlayPause':
                case 'MediaPlay':
                case 'MediaPause':
                    e.preventDefault();
                    togglePlayPause();
                    break;
            }
            return;
        }

        // 3. EN MENÚ DE SELECCIÓN (GRID Y CATEGORÍAS)
        switch (key) {
            case 'ArrowLeft':
                e.preventDefault();
                navigateSpatial('left');
                break;
            case 'ArrowRight':
                e.preventDefault();
                navigateSpatial('right');
                break;
            case 'ArrowUp':
                e.preventDefault();
                navigateSpatial('up');
                break;
            case 'ArrowDown':
                e.preventDefault();
                navigateSpatial('down');
                break;
            case 'Enter':
                e.preventDefault();
                triggerCurrentFocus();
                break;
            case 'Escape':
            case 'Backspace':
                // Si está en categoría, vuelve a 'all'
                if (state.activeCategory !== 'all') {
                    filterByCategory('all');
                }
                break;
        }
    }

    function navigateSpatial(direction) {
        if (state.focusedZone === 'categories') {
            const pills = Array.from(dom.categoriesBar.querySelectorAll('.cat-pill'));
            let curr = pills.findIndex(p => p === document.activeElement);
            if (curr === -1) curr = 0;

            if (direction === 'left' && curr > 0) {
                pills[curr - 1].focus();
            } else if (direction === 'right' && curr < pills.length - 1) {
                pills[curr + 1].focus();
            } else if (direction === 'down') {
                state.focusedZone = 'grid';
                setFocusOnGridItem(state.focusedIndex || 0);
            }
            return;
        }

        // Navegación en la grilla de canales
        const cards = dom.grid.querySelectorAll('.channel-card');
        if (cards.length === 0) return;

        const columns = getGridColumnCount();
        let nextIndex = state.focusedIndex;

        switch (direction) {
            case 'left':
                if (nextIndex > 0) nextIndex--;
                break;
            case 'right':
                if (nextIndex < cards.length - 1) nextIndex++;
                break;
            case 'up':
                if (nextIndex - columns >= 0) {
                    nextIndex -= columns;
                } else {
                    // Subir a la barra de categorías
                    state.focusedZone = 'categories';
                    const activePill = dom.categoriesBar.querySelector('.cat-pill.active') || dom.categoriesBar.querySelector('.cat-pill');
                    if (activePill) activePill.focus();
                    return;
                }
                break;
            case 'down':
                if (nextIndex + columns < cards.length) {
                    nextIndex += columns;
                }
                break;
        }

        setFocusOnGridItem(nextIndex);
    }

    function setFocusOnGridItem(index, smoothScroll = true) {
        const cards = dom.grid.querySelectorAll('.channel-card');
        if (index < 0 || index >= cards.length) return;

        cards.forEach(c => c.classList.remove('focused'));

        state.focusedIndex = index;
        const target = cards[index];
        target.classList.add('focused');
        try {
            target.focus({ preventScroll: true });
        } catch (e) {
            target.focus();
        }

        if (smoothScroll) {
            target.scrollIntoView({
                block: 'nearest',
                inline: 'nearest',
                behavior: 'smooth'
            });
        }
    }

    function triggerCurrentFocus() {
        if (state.focusedZone === 'categories') {
            const active = document.activeElement;
            if (active && active.dataset.category) {
                filterByCategory(active.dataset.category);
            }
        } else if (state.focusedZone === 'grid') {
            const channel = state.filteredChannels[state.focusedIndex];
            if (channel) {
                playChannel(channel);
            }
        }
    }

    function getGridColumnCount() {
        const gridWidth = dom.grid.offsetWidth;
        const cardWidth = 240; // Aproximado minmax de CSS
        return Math.max(1, Math.floor(gridWidth / cardWidth));
    }

    // ==========================================================================
    // 5. TECLADO NUMÉRICO RÁPIDO (CONTROL FÍSICO 1, 2, 3...)
    // ==========================================================================
    function handleNumberInput(digit) {
        state.numberBuffer += digit;

        // Mostrar cartel grande en pantalla
        dom.numericDigits.textContent = state.numberBuffer;
        dom.numericOverlay.classList.remove('hidden');

        if (state.numberTimer) clearTimeout(state.numberTimer);

        state.numberTimer = setTimeout(() => {
            dom.numericOverlay.classList.add('hidden');
            const targetNum = parseInt(state.numberBuffer, 10);
            state.numberBuffer = '';

            const match = state.channels.find(c => c.number === targetNum);
            if (match) {
                playChannel(match);
            } else {
                console.log(`Canal número ${targetNum} no encontrado.`);
            }
        }, 1200);
    }

    // ==========================================================================
    // 6. FAVORITOS Y RELOJ
    // ==========================================================================
    function toggleFavorite(channelName) {
        if (state.favorites.has(channelName)) {
            state.favorites.delete(channelName);
        } else {
            state.favorites.add(channelName);
        }
        saveFavoritesToStorage();
    }

    function loadFavoritesFromStorage() {
        try {
            const raw = localStorage.getItem('tvv_favs');
            if (raw) {
                state.favorites = new Set(JSON.parse(raw));
            }
        } catch (e) {}
    }

    function saveFavoritesToStorage() {
        try {
            localStorage.setItem('tvv_favs', JSON.stringify(Array.from(state.favorites)));
        } catch (e) {}
    }

    // ==========================================================================
    // 6.1 MEMORIA DE CANALES PROXY Y PRECARGA INTELIGENTE
    // ==========================================================================
    function loadProxyChannelsFromStorage() {
        try {
            const raw = localStorage.getItem('tvv_proxy_channels');
            if (raw) {
                const list = JSON.parse(raw);
                if (Array.isArray(list)) {
                    state.proxyChannels = new Set(list);
                }
            }
        } catch (e) {}
    }

    function saveProxyChannelsToStorage() {
        try {
            localStorage.setItem('tvv_proxy_channels', JSON.stringify(Array.from(state.proxyChannels)));
        } catch (e) {}
    }

    function rememberProxyChannel(channelName) {
        if (!channelName) return;
        state.proxyChannels.add(channelName);
        saveProxyChannelsToStorage();
    }

    function prefetchAdjacentManifests() {
        if (!state.isPlaying || !state.currentChannel || state.channels.length < 2) return;

        const currIdx = state.currentChannelIndex;
        if (currIdx === -1) return;

        const nextIdx = (currIdx + 1) % state.channels.length;
        const prevIdx = (currIdx - 1 + state.channels.length) % state.channels.length;

        const targets = [state.channels[nextIdx], state.channels[prevIdx]];

        targets.forEach(ch => {
            if (!ch || !ch.url || ch.url.startsWith('dailymotion://')) return;

            let targetUrl = ch.url;
            if (ch.url.startsWith('http://') || ch.url.startsWith('desdeparaguay://') || state.proxyChannels.has(ch.name)) {
                targetUrl = `/api/proxy?url=${encodeURIComponent(ch.url)}`;
            }

            // Descarga silenciosa del manifiesto .m3u8 (~1 KB) con baja prioridad
            // Pre-calienta conexiones DNS, SSL handshake y caché HTTP del celular
            try {
                fetch(targetUrl, {
                    method: 'GET',
                    priority: 'low'
                }).then(resp => {
                    if (resp.ok) {
                        // Precargado con éxito
                    }
                }).catch(() => {
                    // Si falla por CORS silenciosamente, lo recordamos como proxy para que al sintonizarlo arranque directo
                    if (!state.proxyChannels.has(ch.name) && !ch.url.startsWith('http://') && !ch.url.startsWith('desdeparaguay://')) {
                        rememberProxyChannel(ch.name);
                    }
                });
            } catch (e) {}
        });
    }

    function updateFavoriteButtonState() {
        if (!state.currentChannel) return;
        const isFav = state.favorites.has(state.currentChannel.name);
        if (dom.iconFavSvg) {
            dom.iconFavSvg.style.color = isFav ? '#FFD700' : '#FFFFFF';
            dom.iconFavSvg.style.filter = isFav ? 'drop-shadow(0 0 8px rgba(255, 215, 0, 0.85))' : 'none';
        }
        if (dom.btnToggleFav) {
            dom.btnToggleFav.setAttribute('title', isFav ? 'Quitar de Favoritos' : 'Guardar en Favoritos');
        }
    }

    function startClock() {
        function update() {
            const now = new Date();
            const hours = String(now.getHours()).padStart(2, '0');
            const minutes = String(now.getMinutes()).padStart(2, '0');
            dom.clock.textContent = `${hours}:${minutes}`;
        }
        update();
        setInterval(update, 1000);
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>"']/g, function (m) {
            return {
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#39;'
            }[m];
        });
    }

    // REGISTRO DE SERVICE WORKER (PWA)
    if ('serviceWorker' in navigator) {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register('sw.js').catch(err => {
                console.log('SW no registrado:', err);
            });
        });
    }

    // INICIAR
    document.addEventListener('DOMContentLoaded', init);

})();
