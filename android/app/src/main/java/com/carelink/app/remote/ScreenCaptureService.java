package com.carelink.app.remote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.carelink.app.R;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";
    private static final String CHANNEL_ID = "screen_capture_channel";
    private static final int NOTIFICATION_ID = 10001;
    private static final int TARGET_MAX_FRAME_BYTES = 20 * 1024;
    private static final long MIN_CAPTURE_INTERVAL_MS = 150L;

    public static final String ACTION_START = "com.carelink.app.START_CAPTURE";
    public static final String ACTION_STOP = "com.carelink.app.STOP_CAPTURE";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final Object PROJECTION_PERMISSION_LOCK = new Object();
    private static int cachedResultCode = -1;
    private static Intent cachedResultData;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final ExecutorService captureExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Binder binder = new LocalBinder();

    private volatile boolean isCapturing = false;
    private volatile boolean isInitializing = false;
    private volatile long lastFrameSubmitAtMs = 0L;
    private volatile String lastError = "";

    private FrameCallback frameCallback;
    private int screenWidth = 720;
    private int screenHeight = 1280;
    private int screenDensity = 1;

    public interface FrameCallback {
        void onFrame(byte[] jpegData, long timestamp);
    }

    public static void cacheProjectionPermission(int resultCode, Intent resultData) {
        synchronized (PROJECTION_PERMISSION_LOCK) {
            cachedResultCode = resultCode;
            cachedResultData = resultData;
        }
    }

    public class LocalBinder extends Binder {
        public ScreenCaptureService getService() {
            return ScreenCaptureService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = Math.min(Math.max(metrics.widthPixels, 1), 720);
            screenHeight = Math.max(
                    (int) (screenWidth * metrics.heightPixels / (float) Math.max(metrics.widthPixels, 1)),
                    1
            );
            screenDensity = Math.max(metrics.densityDpi, 1);
        }
        Log.d(TAG, "Capture size=" + screenWidth + "x" + screenHeight + ", density=" + screenDensity);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            ProjectionPermission permission = resolveProjectionPermission(intent);
            int resultCode = permission.resultCode;
            Intent resultData = permission.resultData;
            if (resultCode == -1 || resultData == null) {
                isInitializing = false;
                lastError = "screen_capture_permission_missing";
                Log.e(TAG, "Missing screen capture permission data");
                return START_NOT_STICKY;
            }
            startForegroundCompat(createNotification("正在共享屏幕..."));
            startCapture(resultCode, resultData);
        } else if (ACTION_STOP.equals(action)) {
            lastError = "";
            stopCapture();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public synchronized void startCapture(int resultCode, Intent resultData) {
        if (isCapturing) {
            isInitializing = false;
            lastError = "";
            return;
        }

        stopCapture();
        isInitializing = true;
        lastError = "";
        startForegroundCompat(createNotification("正在共享屏幕..."));

        try {
            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (manager == null) {
                throw new IllegalStateException("media_projection_manager_unavailable");
            }

            mediaProjection = manager.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) {
                throw new IllegalStateException("media_projection_unavailable");
            }

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            imageReader.setOnImageAvailableListener(availRunner, new Handler(Looper.getMainLooper()));

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    null
            );

            if (virtualDisplay == null) {
                throw new IllegalStateException("virtual_display_create_failed");
            }

            isCapturing = true;
            isInitializing = false;
            lastError = "";
            Log.d(TAG, "Screen capture started");
        } catch (Exception e) {
            lastError = e.getMessage() == null ? "screen_capture_init_failed" : e.getMessage();
            Log.e(TAG, "Start capture failed: " + lastError, e);
            stopCapture();
        }
    }

    public synchronized boolean startCaptureFromCacheIfNeeded() {
        if (isCapturing || isInitializing) {
            return isCapturing;
        }
        ProjectionPermission permission = resolveProjectionPermission(null);
        if (permission.resultCode == -1 || permission.resultData == null) {
            lastError = "screen_capture_permission_missing";
            return false;
        }
        startCapture(permission.resultCode, permission.resultData);
        return isCapturing;
    }

    private final ImageReader.OnImageAvailableListener availRunner = reader -> {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null || frameCallback == null || !isCapturing) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastFrameSubmitAtMs < MIN_CAPTURE_INTERVAL_MS) {
                return;
            }
            lastFrameSubmitAtMs = now;
            final Image frameImage = image;
            image = null;
            captureExecutor.execute(() -> processFrame(frameImage));
        } catch (Exception e) {
            Log.e(TAG, "Capture frame failed: " + e.getMessage(), e);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    };

    private void processFrame(Image image) {
        try {
            long timestamp = image.getTimestamp();
            byte[] jpegData = convertImageToJpeg(image, 60);
            if (jpegData == null || jpegData.length == 0 || frameCallback == null || !isCapturing) {
                return;
            }
            mainHandler.post(() -> {
                if (frameCallback != null && isCapturing) {
                    frameCallback.onFrame(jpegData, timestamp);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Process frame failed: " + e.getMessage(), e);
        } finally {
            try {
                image.close();
            } catch (Exception ignored) {
            }
        }
    }

    private byte[] convertImageToJpeg(Image image, int quality) {
        if (image == null || image.getPlanes() == null || image.getPlanes().length == 0) {
            return null;
        }
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;
            int bitmapWidth = screenWidth + Math.max(rowPadding, 0) / Math.max(pixelStride, 1);

            Bitmap rawBitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888);
            rawBitmap.copyPixelsFromBuffer(buffer);

            Bitmap croppedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, screenWidth, screenHeight);
            if (rawBitmap != croppedBitmap) {
                rawBitmap.recycle();
            }

            int newW = Math.max(croppedBitmap.getWidth() / 2, 1);
            int newH = Math.max(croppedBitmap.getHeight() / 2, 1);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, newW, newH, true);
            if (scaledBitmap != croppedBitmap) {
                croppedBitmap.recycle();
            }

            byte[] result = encodeJpegWithinLimit(scaledBitmap, quality, TARGET_MAX_FRAME_BYTES);
            scaledBitmap.recycle();
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Convert image to jpeg failed: " + e.getMessage(), e);
            return null;
        }
    }

    private byte[] encodeJpegWithinLimit(Bitmap bitmap, int initialQuality, int maxBytes) {
        if (bitmap == null) {
            return null;
        }
        Bitmap working = bitmap;
        int quality = Math.max(25, Math.min(initialQuality, 90));

        for (int i = 0; i < 5; i++) {
            byte[] encoded = encodeJpeg(working, quality);
            if (encoded == null) {
                return null;
            }
            if (encoded.length <= maxBytes || i == 4) {
                if (working != bitmap) {
                    working.recycle();
                }
                return encoded;
            }

            if (quality > 30) {
                quality = Math.max(30, quality - 10);
                continue;
            }

            int nextW = Math.max((int) (working.getWidth() * 0.8f), 1);
            int nextH = Math.max((int) (working.getHeight() * 0.8f), 1);
            Bitmap smaller = Bitmap.createScaledBitmap(working, nextW, nextH, true);
            if (working != bitmap) {
                working.recycle();
            }
            working = smaller;
        }

        if (working != bitmap) {
            working.recycle();
        }
        return null;
    }

    private byte[] encodeJpeg(Bitmap bitmap, int quality) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Encode jpeg failed: " + e.getMessage(), e);
            return null;
        }
    }

    public synchronized void stopCapture() {
        isCapturing = false;
        isInitializing = false;
        lastFrameSubmitAtMs = 0L;

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        Log.d(TAG, "Screen capture stopped");
    }

    public void setFrameCallback(FrameCallback callback) {
        this.frameCallback = callback;
    }

    public boolean isCapturing() {
        return isCapturing;
    }

    public boolean isInitializing() {
        return isInitializing;
    }

    public String getLastError() {
        return lastError == null ? "" : lastError;
    }

    public synchronized void clearLastError() {
        if (!isInitializing) {
            lastError = "";
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "屏幕共享服务",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("用于远程协助的屏幕共享");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("颐养家 - 远程协助")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
            return;
        }
        startForeground(NOTIFICATION_ID, notification);
    }

    private Intent extractResultData(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        }
        return intent.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    private ProjectionPermission resolveProjectionPermission(Intent intent) {
        int resultCode = -1;
        Intent resultData = null;

        if (intent != null) {
            resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
            resultData = extractResultData(intent);
            if (resultCode != -1 && resultData != null) {
                cacheProjectionPermission(resultCode, resultData);
                return new ProjectionPermission(resultCode, resultData);
            }
        }

        synchronized (PROJECTION_PERMISSION_LOCK) {
            if (cachedResultCode != -1 && cachedResultData != null) {
                return new ProjectionPermission(cachedResultCode, cachedResultData);
            }
        }
        return new ProjectionPermission(-1, null);
    }

    private static class ProjectionPermission {
        private final int resultCode;
        private final Intent resultData;

        private ProjectionPermission(int resultCode, Intent resultData) {
            this.resultCode = resultCode;
            this.resultData = resultData;
        }
    }

    @Override
    public void onDestroy() {
        stopCapture();
        captureExecutor.shutdown();
        super.onDestroy();
    }
}
