package com.tvpy.app;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalRemoteServer {
    private static final String TAG = "LocalRemoteServer";
    public static final int PORT = 8088;

    private final Context context;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public LocalRemoteServer(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;

        threadPool.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.d(TAG, "LocalRemoteServer started on port " + PORT);

                while (isRunning && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        threadPool.execute(() -> handleClient(clientSocket));
                    } catch (Exception e) {
                        if (!isRunning) break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error starting server on port " + PORT, e);
            }
        });
    }

    public synchronized void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing server socket", e);
        }
    }

    private void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String uri = parts[1];

            // Parse headers
            String line;
            Map<String, String> headers = new HashMap<>();
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
                }
            }

            // CORS Preflight
            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendResponse(out, 204, "No Content", "text/plain", new byte[0]);
                return;
            }

            // Parse path and query params
            String path = uri;
            Map<String, String> params = new HashMap<>();
            int qIdx = uri.indexOf('?');
            if (qIdx >= 0) {
                path = uri.substring(0, qIdx);
                String queryString = uri.substring(qIdx + 1);
                for (String pair : queryString.split("&")) {
                    String[] kv = pair.split("=");
                    if (kv.length == 2) {
                        params.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
                    } else if (kv.length == 1) {
                        params.put(URLDecoder.decode(kv[0], "UTF-8"), "");
                    }
                }
            }

            // API: Comando remoto
            if (path.startsWith("/api/cmd")) {
                String action = params.get("action");
                if (action != null) {
                    JSONObject data = new JSONObject();
                    for (Map.Entry<String, String> entry : params.entrySet()) {
                        data.put(entry.getKey(), entry.getValue());
                    }
                    if (params.containsKey("number")) {
                        try {
                            data.put("number", Integer.parseInt(params.get("number")));
                        } catch (Exception ignored) {}
                    }
                    RemoteControlManager.getInstance().dispatchDirectCommand(action, data);
                }
                byte[] resp = "{\"status\":\"ok\"}".getBytes("UTF-8");
                sendResponse(out, 200, "OK", "application/json", resp);
                return;
            }

            // API: Estado de la TV
            if (path.startsWith("/api/status")) {
                RemoteControlManager.getInstance().notifyClientConnected();
                JSONObject statusObj = new JSONObject();
                statusObj.put("status", "connected");
                statusObj.put("channelName", RemoteControlManager.getInstance().getLastChannelName());
                statusObj.put("channelNumber", RemoteControlManager.getInstance().getLastChannelNumber());
                byte[] resp = statusObj.toString().getBytes("UTF-8");
                sendResponse(out, 200, "OK", "application/json", resp);
                return;
            }

            // Archivos estáticos desde assets
            String assetPath;
            String contentType;

            if ("/".equals(path) || "/remote".equals(path) || "/remote.html".equals(path) || "/index.html".equals(path)) {
                RemoteControlManager.getInstance().notifyClientConnected();
                assetPath = "remote.html";
                contentType = "text/html; charset=utf-8";
            } else if ("/remote.css".equals(path)) {
                assetPath = "remote.css";
                contentType = "text/css; charset=utf-8";
            } else if ("/remote.js".equals(path)) {
                assetPath = "remote.js";
                contentType = "application/javascript; charset=utf-8";
            } else if ("/channels.json".equals(path)) {
                assetPath = "channels.json";
                contentType = "application/json; charset=utf-8";
            } else {
                sendResponse(out, 404, "Not Found", "text/plain", "Not Found".getBytes("UTF-8"));
                return;
            }

            try (InputStream assetIn = context.getAssets().open(assetPath)) {
                byte[] fileBytes = toByteArray(assetIn);
                sendResponse(out, 200, "OK", contentType, fileBytes);
            } catch (Exception e) {
                sendResponse(out, 404, "Not Found", "text/plain", ("File not found: " + assetPath).getBytes("UTF-8"));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling client socket", e);
        }
    }

    private void sendResponse(OutputStream out, int statusCode, String statusText, String contentType, byte[] body) {
        try {
            String header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                    "Content-Type: " + contentType + "\r\n" +
                    "Content-Length: " + body.length + "\r\n" +
                    "Connection: close\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: *\r\n\r\n";
            out.write(header.getBytes("UTF-8"));
            out.write(body);
            out.flush();
        } catch (Exception ignored) {}
    }

    private byte[] toByteArray(InputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    public static String getWifiIpAddress(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                int ipInt = wm.getConnectionInfo().getIpAddress();
                if (ipInt != 0) {
                    return Formatter.formatIpAddress(ipInt);
                }
            }
        } catch (Exception ignored) {}

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}

        return "127.0.0.1";
    }
}
