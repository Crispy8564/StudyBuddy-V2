package com.studybuddy.app;

import android.app.Activity;
import android.media.projection.MediaProjectionManager;
import android.content.Intent;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import android.util.Base64;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int OVERLAY_PERMISSION_REQUEST = 4101;
    private static final int SCREENSHOT_PICK_REQUEST = 4102;
    private static final int SCREEN_CAPTURE_REQUEST = 4103;

    private LinearLayout root;
    private TextView status;
    private TextView extractedText;
    private ImageView screenshotPreview;
    private Button chooseScreenshot;
    private TextRecognizer recognizer;
    private Bitmap selectedScreenshot;
    private Uri selectedScreenshotUri;
    private Button explainWithAi;
    private TextView aiAnswer;
    private boolean autoAskAfterScreenshot = false;
    private boolean launchedFromAssistant = false;
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private static final String AI_ENDPOINT = "https://studybuddy-ai.crispyawest2012.workers.dev";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        autoAskAfterScreenshot = getIntent().getBooleanExtra("assistant_one_tap", false);
        launchedFromAssistant = autoAskAfterScreenshot;
        buildScreen();
        if (autoAskAfterScreenshot) status.post(this::beginScreenCapture);
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28, 42, 28, 32);
        root.setBackgroundColor(0xFF100D18);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("StudyBuddy");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Your on-screen study companion");
        subtitle.setTextColor(0xFFD8C9F2);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        addTopSpace(subtitle, 10);

        TextView instructions = new TextView(this);
        instructions.setText("HOW TO SCAN A QUESTION\n1. Take a screenshot of your Educake question.\n2. Tap the floating SB bubble.\n3. Choose the screenshot below.\n\nStudyBuddy reads the text on your device and keeps the original image so diagrams can be considered too. Nothing is captured until you choose an image.");
        instructions.setTextColor(0xFFE8E2F0);
        instructions.setTextSize(15);
        instructions.setPadding(18, 18, 18, 18);
        instructions.setBackgroundColor(0xFF211B2B);
        addTopSpace(instructions, 28);

        chooseScreenshot = new Button(this);
        chooseScreenshot.setText("Choose Educake screenshot");
        addTopSpace(chooseScreenshot, 18);
        chooseScreenshot.setOnClickListener(v -> chooseScreenshot());

        explainWithAi = new Button(this);
        explainWithAi.setText("Ask StudyBuddy AI");
        explainWithAi.setEnabled(false);
        addTopSpace(explainWithAi, 12);
        explainWithAi.setOnClickListener(v -> askStudyBuddyAi());

        aiAnswer = new TextView(this);
        aiAnswer.setText("Your AI explanation will appear here.");
        aiAnswer.setTextColor(0xFFE8E2F0);
        aiAnswer.setTextSize(16);
        aiAnswer.setPadding(18, 18, 18, 18);
        aiAnswer.setBackgroundColor(0xFF211B2B);
        addTopSpace(aiAnswer, 18);

        status = new TextView(this);
        status.setText("Ready when you are.");
        status.setTextColor(0xFFC4B5FD);
        status.setTextSize(14);
        addTopSpace(status, 14);

        TextView imageHeading = new TextView(this);
        imageHeading.setText("QUESTION IMAGE (INCLUDING DIAGRAMS)");
        imageHeading.setTextColor(Color.WHITE);
        imageHeading.setTextSize(14);
        imageHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        addTopSpace(imageHeading, 24);

        screenshotPreview = new ImageView(this);
        screenshotPreview.setAdjustViewBounds(true);
        screenshotPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        screenshotPreview.setMaxHeight(dp(320));
        screenshotPreview.setVisibility(View.GONE);
        screenshotPreview.setBackgroundColor(0xFF211B2B);
        addTopSpace(screenshotPreview, 10);

        TextView resultHeading = new TextView(this);
        resultHeading.setText("RECOGNISED QUESTION TEXT");
        resultHeading.setTextColor(Color.WHITE);
        resultHeading.setTextSize(14);
        resultHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        addTopSpace(resultHeading, 24);

        extractedText = new TextView(this);
        extractedText.setText("Your recognised text will appear here.");
        extractedText.setTextColor(0xFFE8E2F0);
        extractedText.setTextSize(15);
        extractedText.setPadding(18, 18, 18, 18);
        extractedText.setBackgroundColor(0xFF211B2B);
        addTopSpace(extractedText, 10);

        Button enable = new Button(this);
        enable.setText("Enable floating helper");
        addTopSpace(enable, 28);
        enable.setOnClickListener(v -> enableFloatingHelper());

        Button stop = new Button(this);
        stop.setText("Turn off floating helper");
        addTopSpace(stop, 10);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingHelperService.class));
            stopService(new Intent(this, ScreenCaptureService.class));
            Toast.makeText(this, "Floating helper turned off", Toast.LENGTH_SHORT).show();
        });

        setContentView(scroll);
    }

    private void addTopSpace(View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        root.addView(view, params);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void beginScreenCapture() {
        if (ScreenCaptureService.isRunning()) {
            Intent capture = new Intent(this, ScreenCaptureService.class);
            capture.setAction(ScreenCaptureService.ACTION_CAPTURE);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(capture);
            else startService(capture);
            moveTaskToBack(true);
            return;
        }
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST);
    }

    private void chooseScreenshot() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(pick, "Choose Educake screenshot"), SCREENSHOT_PICK_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "No image picker is available.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST && Settings.canDrawOverlays(this)) {
            startService(new Intent(this, FloatingHelperService.class));
            Toast.makeText(this, "Floating helper enabled", Toast.LENGTH_SHORT).show();
        } else if (requestCode == SCREEN_CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                Intent captureService = new Intent(this, ScreenCaptureService.class);
                captureService.putExtra("resultCode", resultCode);
                captureService.putExtra("resultData", data);
                captureService.putExtra("captureNow", true);
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(captureService);
                else startService(captureService);
                moveTaskToBack(true);
            } else {
                Toast.makeText(this, "Screen capture permission wasn't granted.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == SCREENSHOT_PICK_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            readScreenshot(data.getData());
        }
    }

    private void readScreenshot(Uri imageUri) {
        status.setText("Opening screenshot on this device…");
        chooseScreenshot.setEnabled(false);
        try (InputStream stream = getContentResolver().openInputStream(imageUri)) {
            if (stream == null) throw new IOException("Image stream unavailable");
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap == null) throw new IOException("Image could not be decoded");
            if (selectedScreenshot != null && !selectedScreenshot.isRecycled()) {
                selectedScreenshot.recycle();
            }
            selectedScreenshot = bitmap;
            selectedScreenshotUri = imageUri;
            screenshotPreview.setImageBitmap(selectedScreenshot);
            screenshotPreview.setVisibility(View.VISIBLE);

            status.setText("Image loaded. Reading question text…");
            InputImage image = InputImage.fromBitmap(selectedScreenshot, 0);
            recognizer.process(image)
                .addOnSuccessListener(result -> showRecognisedText(result))
                .addOnFailureListener(error -> {
                    status.setText("Image loaded, but text recognition failed. The diagram image is still available above.");
                    Toast.makeText(this, "Text recognition failed.", Toast.LENGTH_LONG).show();
                })
                .addOnCompleteListener(task -> chooseScreenshot.setEnabled(true));
        } catch (IOException | SecurityException e) {
            chooseScreenshot.setEnabled(true);
            status.setText("Couldn't open that image. Please choose it again.");
        }
    }

    private void showRecognisedText(Text result) {
        String recognised = result.getText();
        if (recognised == null || recognised.trim().isEmpty()) {
            status.setText("Image loaded. No text found, but the diagram is still available above.");
            extractedText.setText("No text was detected in this screenshot.");
        } else {
            status.setText("Screenshot image and text loaded successfully.");
            extractedText.setText(recognised);
        }
        explainWithAi.setEnabled(selectedScreenshotUri != null);
        if (autoAskAfterScreenshot) { autoAskAfterScreenshot = false; askStudyBuddyAi(); }
    }

    private void askStudyBuddyAi() {
        if (selectedScreenshot == null) {
            Toast.makeText(this, "Choose a question screenshot first.", Toast.LENGTH_SHORT).show();
            return;
        }
        explainWithAi.setEnabled(false);
        explainWithAi.setText("Thinking…");
        aiAnswer.setText("StudyBuddy is analysing your question and diagram…");
        final Bitmap imageCopy = selectedScreenshot.copy(Bitmap.Config.ARGB_8888, false);
        final String ocr = extractedText.getText().toString();

        aiExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
                imageCopy.compress(Bitmap.CompressFormat.JPEG, 82, imageBytes);
                imageCopy.recycle();
                String encodedImage = Base64.encodeToString(imageBytes.toByteArray(), Base64.NO_WRAP);
                JSONObject payload = new JSONObject();
                payload.put("imageBase64", encodedImage);
                payload.put("mimeType", "image/jpeg");
                payload.put("ocrText", ocr);

                connection = (HttpURLConnection) new URL(AI_ENDPOINT).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
                if (stream != null) {
                    try (InputStream input = stream) {
                        byte[] buffer = new byte[4096]; int count;
                        while ((count = input.read(buffer)) != -1) responseBytes.write(buffer, 0, count);
                    }
                }
                JSONObject response = new JSONObject(responseBytes.toString("UTF-8"));
                if (code < 200 || code >= 300) throw new IOException(response.optString("error", "AI request failed (" + code + ")."));
                String answer = response.optString("answer", "");
                if (answer.trim().isEmpty()) throw new IOException("The AI returned an empty answer.");
                runOnUiThread(() -> { aiAnswer.setText(answer); status.setText("StudyBuddy AI has finished."); Intent resultIntent = new Intent("com.studybuddy.app.SHOW_AI_ANSWER"); resultIntent.setPackage(getPackageName()); resultIntent.putExtra("answer", answer); sendBroadcast(resultIntent); });
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Please try again." : error.getMessage();
                runOnUiThread(() -> { aiAnswer.setText("I couldn't get an explanation: " + message); status.setText("AI request unsuccessful."); });
            } finally {
                if (connection != null) connection.disconnect();
                runOnUiThread(() -> { explainWithAi.setEnabled(true); explainWithAi.setText("Ask StudyBuddy AI"); });
            }
        });
        if (launchedFromAssistant) {
            launchedFromAssistant = false;
            moveTaskToBack(true);
        }
    }

    private void shareQuestionWithAi() {
        if (selectedScreenshotUri == null) {
            Toast.makeText(this, "Choose a question screenshot first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String questionText = extractedText.getText().toString();
        String prompt = "Please help me understand this school question. Use the attached screenshot and the recognised text below. Explain the reasoning step by step, refer to the diagram if relevant, and help me learn rather than just giving an answer. If anything in the image is unclear, say so.\n\nRecognised text:\n" + questionText;

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        share.putExtra(Intent.EXTRA_STREAM, selectedScreenshotUri);
        share.putExtra(Intent.EXTRA_TEXT, prompt);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(ClipData.newUri(getContentResolver(), "StudyBuddy question screenshot", selectedScreenshotUri));
        try {
            startActivity(Intent.createChooser(share, "Choose an AI app to explain this question"));
        } catch (Exception e) {
            Toast.makeText(this, "No compatible app was found to share the question.", Toast.LENGTH_LONG).show();
        }
    }

    private void enableFloatingHelper() {
        if (!Settings.canDrawOverlays(this)) {
            Intent permissionIntent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(permissionIntent, OVERLAY_PERMISSION_REQUEST);
            return;
        }
        startService(new Intent(this, FloatingHelperService.class));
        Toast.makeText(this, "Floating helper enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.close();
        if (selectedScreenshot != null && !selectedScreenshot.isRecycled()) {
            selectedScreenshot.recycle();
        }
        super.onDestroy();
    }
}
