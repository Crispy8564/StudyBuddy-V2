package com.studybuddy.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
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
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenCaptureService extends Service {
    public static final String ACTION_CAPTURE = "com.studybuddy.app.CAPTURE_NOW";
    private static volatile boolean running = false;
    public static boolean isRunning() { return running; }
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private int width, height, density;
    private boolean requested = false;
    private boolean sending = false;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private static final String ENDPOINT = "https://studybuddy-ai.crispyawest2012.workers.dev";

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "com.studybuddy.app.STOP_CAPTURE".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_CAPTURE.equals(intent.getAction())) {
            if (handler != null) handler.post(() -> requested = true);
            return START_STICKY;
        }
        if (intent == null || !intent.hasExtra("resultData")) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startCaptureNotification();
        try {
            int resultCode = intent.getIntExtra("resultCode", 0);
            Intent resultData = (Intent) intent.getParcelableExtra("resultData");
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            if (projection == null) throw new IllegalStateException("No screen permission");
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            width = metrics.widthPixels;
            height = metrics.heightPixels;
            density = metrics.densityDpi;
            thread = new HandlerThread("StudyBuddyCapture");
            thread.start();
            handler = new Handler(thread.getLooper());
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
            reader.setOnImageAvailableListener(this::onImageAvailable, handler);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            }, handler);
            display = projection.createVirtualDisplay("StudyBuddyScreen", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
            running = true;
            if (intent.getBooleanExtra("captureNow", true)) handler.post(() -> requested = true);
        } catch (Exception e) {
            sendAnswer("Screen capture could not start. Tap SB and approve screen sharing.");
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startCaptureNotification() {
        String id = "studybuddy_capture";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(id, "StudyBuddy screen capture", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, id) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("StudyBuddy is ready")
            .setContentText("Tap SB to analyse the current screen.")
            .setOngoing(true);
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(4104, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(4104, notification);
    }

    private void onImageAvailable(ImageReader source) {
        Image image = source.acquireLatestImage();
        if (image == null) return;
        if (!requested || sending) {
            image.close();
            return;
        }
        requested = false;
        sending = true;
        Bitmap bitmap = imageToBitmap(image);
        image.close();
        if (bitmap == null) {
            sending = false;
            sendAnswer("I couldn't capture the screen. Please try again.");
        } else sendToAi(bitmap);
    }

    private Bitmap imageToBitmap(Image image) {
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int padding = rowStride - pixelStride * width;
            Bitmap padded = Bitmap.createBitmap(width + padding / pixelStride, height, Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
            padded.recycle();
            return cropped;
        } catch (Exception e) { return null; }
    }

    private void sendToAi(Bitmap bitmap) {
        network.execute(() -> {
            HttpURLConnection connection = null;
            try {
                ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 78, imageBytes);
                bitmap.recycle();
                JSONObject payload = new JSONObject();
                payload.put("imageBase64", Base64.encodeToString(imageBytes.toByteArray(), Base64.NO_WRAP));
                payload.put("mimeType", "image/jpeg");
                payload.put("ocrText", "");
                connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(body); }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                ByteArrayOutputStream response = new ByteArrayOutputStream();
                if (stream != null) try (InputStream in = stream) {
                    byte[] data = new byte[4096];
                    int count;
                    while ((count = in.read(data)) != -1) response.write(data, 0, count);
                }
                JSONObject json = new JSONObject(response.toString("UTF-8"));
                String answer = status >= 200 && status < 300 ? json.optString("answer", "I couldn't read that question.") : json.optString("error", "The AI request failed.");
                sendAnswer(answer);
            } catch (Exception e) {
                sendAnswer("StudyBuddy couldn't contact the AI. Check your internet connection and try again.");
            } finally {
                if (connection != null) connection.disconnect();
                if (handler != null) handler.post(() -> sending = false);
            }
        });
    }

    private void sendAnswer(String answer) {
        Intent result = new Intent("com.studybuddy.app.SHOW_AI_ANSWER");
        result.setPackage(getPackageName());
        result.putExtra("answer", answer);
        sendBroadcast(result);
    }

    @Override public void onDestroy() {
        running = false;
        if (display != null) display.release();
        if (reader != null) reader.close();
        if (projection != null) projection.stop();
        if (thread != null) thread.quitSafely();
        network.shutdown();
        super.onDestroy();
    }
}