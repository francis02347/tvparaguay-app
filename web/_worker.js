import { connect } from 'cloudflare:sockets';

export default {
    async fetch(request, env) {
        const reqUrl = new URL(request.url);

        // Responder preflight OPTIONS para CORS
        if (request.method === 'OPTIONS') {
            return new Response(null, {
                status: 204,
                headers: {
                    'Access-Control-Allow-Origin': '*',
                    'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
                    'Access-Control-Allow-Headers': '*',
                    'Access-Control-Max-Age': '86400'
                }
            });
        }

        // PLAYER EMBED PARA CANALES DAILYMOTION CON REFERER SPOOFING NATIVO
        if (reqUrl.pathname === '/player/dm') {
            const videoId = reqUrl.searchParams.get('v');
            const playerId = reqUrl.searchParams.get('p') || 'x1at3a';
            const referer = reqUrl.searchParams.get('ref') || 'https://trece.com.py/';

            if (!videoId) {
                return new Response('Missing "v" parameter', { status: 400 });
            }

            try {
                const dmUrl = `https://geo.dailymotion.com/player/${playerId}.html?video=${videoId}`;
                const res = await fetch(dmUrl, {
                    headers: {
                        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                        'Referer': referer
                    }
                });

                let html = await res.text();
                // Forzar inicio automático
                html = html.replace('"autostart":"off"', '"autostart":"on"');

                return new Response(html, {
                    status: 200,
                    headers: {
                        'content-type': 'text/html; charset=utf-8',
                        'access-control-allow-origin': '*',
                        'cache-control': 'public, max-age=120'
                    }
                });
            } catch (err) {
                return new Response(`Error al cargar el reproductor: ${err.message}`, { status: 502 });
            }
        }

        if (reqUrl.pathname === '/api/proxy') {
            let targetUrlStr = reqUrl.searchParams.get('url');
            if (!targetUrlStr) {
                return new Response('Missing "url" parameter', { status: 400 });
            }

            try {
                let customCookie = reqUrl.searchParams.get('cookie') || null;
                let customReferer = reqUrl.searchParams.get('referer') || null;

                // 1. Resolver esquema dailymotion:// (Trece, Unicanal, ABC-TV Paraguay)
                if (targetUrlStr.startsWith('dailymotion://')) {
                    const parsedDm = parseDailymotionUrl(targetUrlStr);
                    const dmMeta = await resolveDailymotionStream(parsedDm.videoId, parsedDm.referer, parsedDm.embedder);
                    targetUrlStr = dmMeta.masterUrl;
                    if (dmMeta.cookieStr) {
                        customCookie = dmMeta.cookieStr;
                    }
                    if (dmMeta.referer) {
                        customReferer = dmMeta.referer;
                    }
                }

                // 2. Resolver esquema desdeparaguay://
                if (targetUrlStr.startsWith('desdeparaguay://')) {
                    const channelId = targetUrlStr.replace('desdeparaguay://', '').split('?')[0].trim();
                    const lookupUrl = `https://gentv.desdepylabs.com/External/heinetwork/${channelId}`;
                    const res = await fetch(lookupUrl, {
                        headers: {
                            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                            'Referer': 'https://gen.com.py/'
                        }
                    });
                    const html = await res.text();
                    const matches = html.match(/(https?:\/\/[^\s"'>]+?\.m3u8[^\s"'>]*)/g);
                    let realUrl = null;
                    if (matches) {
                        for (const m of matches) {
                            if (m.includes('desdeparaguay.net') && m.includes('k=')) {
                                realUrl = m.replace(/&amp;/g, '&');
                                break;
                            }
                        }
                    }
                    if (realUrl) {
                        targetUrlStr = realUrl;
                    } else {
                        return new Response('Could not resolve DesdeParaguay stream', { status: 502 });
                    }
                }

                // 3. Manejo de parámetros de encabezados en formato pipe (|User-Agent=...|Referer=...)
                let customUa = null;
                if (targetUrlStr.includes('|')) {
                    const parts = targetUrlStr.split('|');
                    targetUrlStr = parts[0];
                    for (let i = 1; i < parts.length; i++) {
                        const hp = parts[i];
                        if (hp.startsWith('User-Agent=')) customUa = hp.slice('User-Agent='.length);
                        if (hp.startsWith('Referer=')) customReferer = hp.slice('Referer='.length);
                    }
                }

                return await handleProxy(targetUrlStr, reqUrl.origin, customUa, customReferer, customCookie);
            } catch (err) {
                return new Response(`Proxy Error: ${err.message}\n${err.stack}`, {
                    status: 502,
                    headers: {
                        'content-type': 'text/plain; charset=utf-8',
                        'access-control-allow-origin': '*'
                    }
                });
            }
        }

        // Servir archivos estáticos del sitio web (HTML, CSS, JS, etc.)
        return env.ASSETS.fetch(request);
    }
};

function parseDailymotionUrl(dmUrl) {
    const raw = dmUrl.replace('dailymotion://', '').trim();
    const parts = raw.split('?');
    const videoId = parts[0].trim();
    let referer = 'https://www.dailymotion.com/';
    let embedder = referer;
    if (parts.length > 1) {
        const params = new URLSearchParams(parts[1]);
        if (params.get('referer')) referer = params.get('referer');
        if (params.get('embedder')) embedder = params.get('embedder');
    }
    return { videoId, referer, embedder };
}

async function resolveDailymotionStream(videoId, referer, embedder) {
    const metaUrl = `https://www.dailymotion.com/player/metadata/video/${encodeURIComponent(videoId)}?embedder=${encodeURIComponent(embedder || referer)}`;
    const res = await fetch(metaUrl, {
        headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Referer': referer
        }
    });

    if (!res.ok) {
        throw new Error(`Dailymotion metadata HTTP error: ${res.status}`);
    }

    let cookieStr = '';
    if (typeof res.headers.getSetCookie === 'function') {
        const cookies = res.headers.getSetCookie();
        if (cookies && cookies.length > 0) {
            cookieStr = cookies.map(c => c.split(';')[0].trim()).join('; ');
        }
    }
    if (!cookieStr) {
        const raw = res.headers.get('set-cookie') || '';
        if (raw) {
            cookieStr = raw.split(',').map(c => c.split(';')[0].trim()).filter(Boolean).join('; ');
        }
    }

    const data = await res.json();
    const autoQualities = data?.qualities?.auto;
    if (!autoQualities || autoQualities.length === 0) {
        const errMsg = data?.error?.message || 'No HLS stream available for this Dailymotion video';
        throw new Error(errMsg);
    }

    return {
        masterUrl: autoQualities[0].url,
        cookieStr: cookieStr,
        referer: referer
    };
}

async function handleProxy(targetUrlStr, origin, customUa, customReferer, customCookie) {
    const target = new URL(targetUrlStr);
    const isIp = /^(\d{1,3}\.){3}\d{1,3}$/.test(target.hostname);

    // Para dominios con nombre (incluyendo puertos no estándar como live.enhdtv.com:19360),
    // intentamos fetch() nativo primero para aprovechar TLS/SNI completo.
    if (!isIp) {
        try {
            return await handleFetchProxy(targetUrlStr, origin, customUa, customReferer, customCookie);
        } catch (err) {
            // Si fetch() falla por puerto no estándar, caer en socket proxy
            return await handleSocketProxy(targetUrlStr, origin, customUa, customReferer, customCookie);
        }
    }

    // Para IPs directas: usar cloudflare:sockets
    return await handleSocketProxy(targetUrlStr, origin, customUa, customReferer, customCookie);
}

async function handleFetchProxy(targetUrlStr, origin, customUa, customReferer, customCookie) {
    const target = new URL(targetUrlStr);
    const ua = customUa || 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';
    const ref = customReferer || (target.origin + '/');

    const headers = {
        'User-Agent': ua,
        'Referer': ref
    };
    if (customCookie) {
        headers['Cookie'] = customCookie;
    }

    const upstreamRes = await fetch(targetUrlStr, {
        headers: headers,
        redirect: 'follow'
    });

    const respHeaders = new Headers(upstreamRes.headers);
    respHeaders.set('Access-Control-Allow-Origin', '*');
    respHeaders.set('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    respHeaders.set('Access-Control-Allow-Headers', '*');

    // Base URL efectiva considerando posibles redirecciones 301/302 (Pluto TV, QAOTIC CDN, etc.)
    const effectiveBaseUrl = upstreamRes.url || targetUrlStr;

    const contentType = upstreamRes.headers.get('content-type') || '';
    const isM3u8 = target.pathname.toLowerCase().endsWith('.m3u8') || 
                   contentType.includes('mpegurl') || 
                   contentType.includes('application/x-mpegurl');

    if (isM3u8) {
        const text = await upstreamRes.text();
        const extraParams = {};
        if (customCookie) extraParams.cookie = customCookie;
        if (customReferer) extraParams.referer = customReferer;

        const rewritten = rewriteM3u8(text, effectiveBaseUrl, origin, extraParams);
        respHeaders.set('content-type', 'application/vnd.apple.mpegurl; charset=utf-8');
        respHeaders.set('cache-control', 'no-cache, no-store, must-revalidate');
        return new Response(rewritten, {
            status: upstreamRes.status,
            headers: respHeaders
        });
    }

    return new Response(upstreamRes.body, {
        status: upstreamRes.status,
        headers: respHeaders
    });
}

async function handleSocketProxy(targetUrlStr, origin, customUa, customReferer, customCookie) {
    const target = new URL(targetUrlStr);
    const port = target.port ? parseInt(target.port, 10) : (target.protocol === 'https:' ? 443 : 80);
    const isHttps = target.protocol === 'https:';

    const socket = connect({
        hostname: target.hostname,
        port: port,
        secureTransport: isHttps ? 'on' : 'off'
    });

    const ua = customUa || 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';
    const pathAndQuery = (target.pathname || '/') + (target.search || '');

    let reqHeaders = `GET ${pathAndQuery} HTTP/1.1\r\nHost: ${target.hostname}:${port}\r\nUser-Agent: ${ua}\r\nAccept: */*\r\nConnection: close\r\n`;
    if (customReferer) {
        reqHeaders += `Referer: ${customReferer}\r\n`;
    }
    if (customCookie) {
        reqHeaders += `Cookie: ${customCookie}\r\n`;
    }
    reqHeaders += '\r\n';

    const writer = socket.writable.getWriter();
    await writer.write(new TextEncoder().encode(reqHeaders));
    writer.releaseLock();

    const reader = socket.readable.getReader();
    let buffer = new Uint8Array(0);
    let headerEndIndex = -1;

    while (headerEndIndex === -1) {
        const { value, done } = await reader.read();
        if (done) break;

        const newBuf = new Uint8Array(buffer.length + value.length);
        newBuf.set(buffer, 0);
        newBuf.set(value, buffer.length);
        buffer = newBuf;

        for (let i = 0; i <= buffer.length - 4; i++) {
            if (buffer[i] === 13 && buffer[i+1] === 10 && buffer[i+2] === 13 && buffer[i+3] === 10) {
                headerEndIndex = i;
                break;
            }
        }
    }

    if (headerEndIndex === -1) {
        reader.releaseLock();
        await socket.close();
        return new Response('No valid HTTP response from upstream server', { status: 502 });
    }

    const headerBytes = buffer.slice(0, headerEndIndex);
    const bodyRemainder = buffer.slice(headerEndIndex + 4);
    const headerText = new TextDecoder().decode(headerBytes);
    const headerLines = headerText.split('\r\n');
    const statusLine = headerLines[0] || 'HTTP/1.1 200 OK';
    const statusCode = parseInt(statusLine.split(' ')[1], 10) || 200;

    const respHeaders = new Headers({
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
        'Access-Control-Allow-Headers': '*'
    });

    let isM3u8 = target.pathname.toLowerCase().endsWith('.m3u8');
    for (let i = 1; i < headerLines.length; i++) {
        const line = headerLines[i];
        const colonIdx = line.indexOf(':');
        if (colonIdx > 0) {
            const k = line.slice(0, colonIdx).trim().toLowerCase();
            const v = line.slice(colonIdx + 1).trim();
            if (k === 'content-type') {
                respHeaders.set('content-type', v);
                if (v.includes('mpegurl') || v.includes('application/x-mpegurl')) {
                    isM3u8 = true;
                }
            }
        }
    }

    if (isM3u8) {
        const bodyChunks = [bodyRemainder];
        while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            bodyChunks.push(value);
        }
        reader.releaseLock();
        await socket.close();

        let totalLen = bodyChunks.reduce((acc, c) => acc + c.length, 0);
        let fullBuf = new Uint8Array(totalLen);
        let offset = 0;
        for (const chunk of bodyChunks) {
            fullBuf.set(chunk, offset);
            offset += chunk.length;
        }

        const m3u8Text = new TextDecoder().decode(fullBuf);
        const extraParams = {};
        if (customCookie) extraParams.cookie = customCookie;
        if (customReferer) extraParams.referer = customReferer;

        const rewrittenM3u8 = rewriteM3u8(m3u8Text, targetUrlStr, origin, extraParams);

        respHeaders.set('content-type', 'application/vnd.apple.mpegurl; charset=utf-8');
        respHeaders.set('cache-control', 'no-cache, no-store, must-revalidate');
        return new Response(rewrittenM3u8, {
            status: statusCode,
            headers: respHeaders
        });
    }

    // Para segmentos TS / binarios: Streaming directo mediante ReadableStream (latencia cero al primer byte)
    const stream = new ReadableStream({
        async start(controller) {
            if (bodyRemainder && bodyRemainder.length > 0) {
                controller.enqueue(bodyRemainder);
            }
        },
        async pull(controller) {
            try {
                const { value, done } = await reader.read();
                if (done) {
                    controller.close();
                    reader.releaseLock();
                    try { await socket.close(); } catch (_) {}
                } else {
                    controller.enqueue(value);
                }
            } catch (err) {
                controller.error(err);
                reader.releaseLock();
                try { await socket.close(); } catch (_) {}
            }
        },
        cancel() {
            reader.releaseLock();
            try { socket.close(); } catch (_) {}
        }
    });

    if (!respHeaders.has('content-type')) {
        respHeaders.set('content-type', 'video/mp2t');
    }
    respHeaders.set('cache-control', 'no-cache');

    return new Response(stream, {
        status: statusCode,
        headers: respHeaders
    });
}

function rewriteM3u8(content, baseUrlStr, origin, extraParams = {}) {
    const lines = content.split('\n');
    const rewritten = [];

    let extraQuery = '';
    if (extraParams.cookie) {
        extraQuery += `&cookie=${encodeURIComponent(extraParams.cookie)}`;
    }
    if (extraParams.referer) {
        extraQuery += `&referer=${encodeURIComponent(extraParams.referer)}`;
    }

    for (let line of lines) {
        const trimmed = line.trim();
        if (!trimmed) {
            rewritten.push(line);
            continue;
        }

        if (trimmed.startsWith('#')) {
            let processedLine = line;

            if (processedLine.includes('URI="')) {
                const updated = processedLine.replace(/URI="([^"]+)"/g, (match, uri) => {
                    try {
                        const absUri = new URL(uri, baseUrlStr).toString();
                        return `URI="${origin}/api/proxy?url=${encodeURIComponent(absUri)}${extraQuery}"`;
                    } catch {
                        return match;
                    }
                });
                rewritten.push(updated);
            } else {
                rewritten.push(processedLine);
            }
        } else {
            try {
                const absUrl = new URL(trimmed, baseUrlStr).toString();
                rewritten.push(`${origin}/api/proxy?url=${encodeURIComponent(absUrl)}${extraQuery}`);
            } catch {
                rewritten.push(line);
            }
        }
    }

    return rewritten.join('\n');
}
