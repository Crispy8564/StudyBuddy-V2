package com.studybuddy.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
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

public class MainActivity extends Activity {
    private static final int OVERLAY_PERMISSION_REQUEST = 4101;
    private static final int SCREENSHOT_PICK_REQUEST = 4102;

    private LinearLayout root;
    private TextView status;
    private TextView extractedText;
    private Button chooseScreenshot;
    private TextRecognizer recognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        buildScreen();
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
        instructions.setText("HOW TO SCAN A QUESTION\n1. Take a screenshot of your Educake question.\n2. Tap the floating SB bubble.\n3. Choose the screenshot below.\n\nStudyBuddy will read the text on your device. Nothing is captured until you choose an image.");
        instructions.setTextColor(0xFFE8E2F0);
        instructions.setTextSize(15);
        instructions.setPadding(18, 18, 18, 18);
        instructions.setBackgroundColor(0xFF211B2B);
        addTopSpace(instructions, 28);

        chooseScreenshot = new Button(this);
        chooseScreenshot.setText("Choose Educake screenshot");
        addTopSpace(chooseScreenshot, 18);
        chooseScreenshot.setOnClickListener(v -> chooseScreenshot());

        status = new TextView(this);
        status.setText("Ready when you are.");
        status.setTextColor(0xFFC4B5FD);
        status.setTextSize(14);
        addTopSpace(status, 14);

        TextView resultHeading = new TextView(this);
        resultHeading.setText("RECOGNISED QUESTION TEXT");
        resultHeading.setTextColor(0xFFFFFFFF);
        resultHeading.setTextSize(14);
        resultHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        addTopSpace(resultHeading, 26);

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
            Toast.makeText(this, "Floating helper turned off", Toast.LENGTH_SHORT).show();
        });

        setContentView(scroll);
    }

    private void addTopSpace(android.view.View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        root.addView(view, params);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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
        } else if (requestCode == SCREENSHOT_PICK_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            readScreenshot(data.getData());
        }
    }

    private void readScreenshot(Uri imageUri) {
        status.setText("Reading screenshot on this device…");
        chooseScreenshot.setEnabled(false);
        try {
            Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            recognizer.process(image)
                .addOnSuccessListener(result -> showRecognisedText(result))
                .addOnFailureListener(error -> {
                    status.setText("Couldn't read that image. Try a clearer screenshot.");
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
            status.setText("No text found. Try a screenshot where the question is clear.");
            extractedText.setText("No text was detected.");
        } else {
            status.setText("Screenshot read successfully.");
            extractedText.setText(recognised);
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
        super.onDestroy();
    }
}
