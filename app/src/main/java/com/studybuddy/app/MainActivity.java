package com.studybuddy.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int OVERLAY_PERMISSION_REQUEST = 4101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28, 56, 28, 28);
        root.setBackgroundColor(0xFF100D18);

        TextView title = new TextView(this);
        title.setText("StudyBuddy");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Your on-screen study companion");
        subtitle.setTextColor(0xFFD8C9F2);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = 12;
        root.addView(subtitle, subParams);

        TextView explanation = new TextView(this);
        explanation.setText("Start with a floating shortcut. You stay in control of when to share a question. The next stage will add user-approved screen capture and question explanations.");
        explanation.setTextColor(0xFFE8E2F0);
        explanation.setTextSize(16);
        explanation.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, -2);
        textParams.topMargin = 36;
        root.addView(explanation, textParams);

        Button enable = new Button(this);
        enable.setText("Enable floating helper");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = 36;
        root.addView(enable, buttonParams);
        enable.setOnClickListener(v -> enableFloatingHelper());

        Button stop = new Button(this);
        stop.setText("Turn off floating helper");
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(-1, -2);
        stopParams.topMargin = 12;
        root.addView(stop, stopParams);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingHelperService.class));
            Toast.makeText(this, "Floating helper turned off", Toast.LENGTH_SHORT).show();
        });

        setContentView(root);
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST && Settings.canDrawOverlays(this)) {
            startService(new Intent(this, FloatingHelperService.class));
            Toast.makeText(this, "Floating helper enabled", Toast.LENGTH_SHORT).show();
        }
    }
}
