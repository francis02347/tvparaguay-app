package com.tvpy.app;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

@androidx.media3.common.util.UnstableApi
public class BackgroundAudioService extends Service {

    public static volatile boolean isRunning = false;
    public static volatile boolean wasStoppedByUser = false;

    public static boolean isRunning() {
        return isRunning;
    }

    private static final String CHANNEL_ID = "BackgroundAudioChannel";
    private static final int NOTIFICATION_ID = 999;
    public static final String ACTION_STOP = "ACTION_STOP";

    private ExoPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private MediaSession mediaSession;

    private String channelName;
    private String streamUrl;
    private String rawUrl;
    private int channelIndex = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        setupAudioFocus();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = new MediaSession(this, "TVParaguay");
            mediaSession.setActive(true);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            wasStoppedByUser = true;
            isRunning = false;
            stopPlayback();
            stopSelf();
            return START_NOT_STICKY;
        }

        wasStoppedByUser = false;
        isRunning = true;

        // Promover a primer plano de inmediato para evitar el crash ForegroundServiceDidNotStartInTimeException
        createNotificationChannel();
        if (intent != null) {
            channelName = intent.getStringExtra("channel_name");
            streamUrl = intent.getStringExtra("stream_url");
            rawUrl = intent.getStringExtra("raw_url");
            channelIndex = intent.getIntExtra("channel_index", -1);
        }
        if (channelName == null) channelName = "Transmisión en Vivo";

        updateMediaSession();

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (streamUrl != null && !streamUrl.isEmpty()) {
            startPlayback(intent);
        } else {
            stopPlayback();
            stopSelf();
        }

        return START_STICKY;
    }

    private void setupAudioFocus() {
        audioFocusChangeListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                // Foco perdido de forma permanente (ej: se abre otro reproductor)
                isRunning = false;
                stopPlayback();
                stopSelf();
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                       focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                // Foco perdido temporalmente (ej: notificación o tono de llamada)
                if (player != null) {
                    player.pause();
                    updatePlaybackState(android.media.session.PlaybackState.STATE_PAUSED);
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                // Foco recuperado
                if (player != null) {
                    player.play();
                    updatePlaybackState(android.media.session.PlaybackState.STATE_PLAYING);
                }
            }
        };
    }

    private void updateMediaSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            android.media.MediaMetadata.Builder metadataBuilder = new android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, channelName)
                    .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "TV Paraguay");
            mediaSession.setMetadata(metadataBuilder.build());

            android.media.session.PlaybackState.Builder stateBuilder = new android.media.session.PlaybackState.Builder()
                    .setActions(android.media.session.PlaybackState.ACTION_PLAY |
                                android.media.session.PlaybackState.ACTION_PAUSE |
                                android.media.session.PlaybackState.ACTION_STOP)
                    .setState(android.media.session.PlaybackState.STATE_PLAYING,
                              android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            mediaSession.setPlaybackState(stateBuilder.build());
        }
    }

    private void updatePlaybackState(int state) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            android.media.session.PlaybackState.Builder stateBuilder = new android.media.session.PlaybackState.Builder()
                    .setActions(android.media.session.PlaybackState.ACTION_PLAY |
                                android.media.session.PlaybackState.ACTION_PAUSE |
                                android.media.session.PlaybackState.ACTION_STOP)
                    .setState(state, android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            mediaSession.setPlaybackState(stateBuilder.build());
        }
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) return false;

        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();

            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build();

            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
        }

        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
    }

    private void startPlayback(Intent intent) {
        if (!requestAudioFocus()) {
            stopPlayback();
            stopSelf();
            return;
        }

        try {
            // 2. Extraer headers HTTP de la URL original y extras del Intent
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            String urlForHeaders = (rawUrl != null && !rawUrl.isEmpty()) ? rawUrl : streamUrl;
            int pipeIdx = urlForHeaders.indexOf('|');
            if (pipeIdx >= 0) {
                String headersPart = urlForHeaders.substring(pipeIdx + 1).trim();
                String[] pairs = headersPart.split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        headers.put(Uri.decode(kv[0]), Uri.decode(kv[1]));
                    }
                }
            }

            if (intent != null) {
                String intentCookie = intent.getStringExtra("cookie");
                String intentReferer = intent.getStringExtra("referer");
                if (intentCookie != null && !intentCookie.isEmpty()) {
                    headers.put("Cookie", intentCookie);
                }
                if (intentReferer != null && !intentReferer.isEmpty()) {
                    headers.put("Referer", intentReferer);
                }
            }

            String userAgent = null;
            if (intent != null) {
                userAgent = intent.getStringExtra("user_agent");
            }
            if (userAgent == null || userAgent.isEmpty()) {
                userAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, como Gecko) Chrome/112.0.0.0 Mobile Safari/537.36";
            }

            // 3. Crear DataSource.Factory con soporte de headers
            DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setAllowCrossProtocolRedirects(true);
            
            if (!headers.isEmpty()) {
                httpFactory.setDefaultRequestProperties(headers);
            }

            DataSource.Factory baseFactory = new DefaultDataSource.Factory(this, httpFactory);

            // 4. Inicializar ExoPlayer para Audio
            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(baseFactory))
                    .build();

            // Evitar que el reproductor intente reproducir video
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters().buildUpon()
                            .setViewportSizeToPhysicalDisplaySize(this, false)
                            .build()
            );

            String cleanUrl = streamUrl;
            int pipeIdxClean = streamUrl.indexOf('|');
            if (pipeIdxClean >= 0) {
                cleanUrl = streamUrl.substring(0, pipeIdxClean).trim();
            }

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(cleanUrl));
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
            isRunning = true;

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        isRunning = true;
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlayingNow) {
                    if (isPlayingNow) {
                        isRunning = true;
                    }
                }

                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    error.printStackTrace();
                    isRunning = false;
                    stopPlayback();
                    stopSelf();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            isRunning = false;
            stopPlayback();
            stopSelf();
        }
    }

    private void stopPlayback() {
        isRunning = false;
        abandonAudioFocus();
        if (player != null) {
            player.release();
            player = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        stopForeground(true);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Reproductor de Audio en Segundo Plano",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, BackgroundAudioService.class);
        stopIntent.setAction(ACTION_STOP);
        
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Al hacer click en la notificación, vuelve a la reproducción del canal
        Intent notificationIntent = new Intent(this, PlayerActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (channelIndex >= 0) {
            notificationIntent.putExtra("channel_index", channelIndex);
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Intentar usar el ícono de auriculares que creamos o el de launcher de la app como fallback
        int iconRes = getResources().getIdentifier("ic_headphones", "drawable", getPackageName());
        if (iconRes == 0) {
            iconRes = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(channelName)
                .setContentText("Reproduciendo en segundo plano")
                .setSmallIcon(iconRes)
                .setContentIntent(pendingIntent)
                .setColorized(true)
                .setColor(0xFF00F2FE) // brand primary color
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent).build());
        } else {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0));
        }

        return builder.build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        stopPlayback();
        super.onDestroy();
    }
}
