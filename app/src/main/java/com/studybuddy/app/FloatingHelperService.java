package com.studybuddy.app;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingHelperService extends Service {
    private WindowManager windowManager;
    private View bubble;
    private WindowManager.LayoutParams params;
    private View answerPanel;
    private TextView answerText;
    private BroadcastReceiver answerReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        TextView button = new TextView(this);
        button.setText("SB");
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(makeBubbleBackground());
        bubble = button;

        params = new WindowManager.LayoutParams(
            dp(56),
            dp(56),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(16);
        params.y = dp(220);

        button.setOnClickListener(v -> { Intent open = new Intent(this, MainActivity.class); open.putExtra("assistant_one_tap", true); open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP); startActivity(open); });
        answerReceiver = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) { String answer = intent.getStringExtra("answer"); if (answer != null) showAnswer(answer); } };
        IntentFilter answerFilter = new IntentFilter("com.studybuddy.app.SHOW_AI_ANSWER");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(answerReceiver, answerFilter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(answerReceiver, answerFilter);

        button.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downX;
            private float downY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = params.x;
                    startY = params.y;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    moved = false;
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    int dx = (int) (downX - event.getRawX());
                    int dy = (int) (event.getRawY() - downY);
                    if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
                    params.x = Math.max(0, startX + dx);
                    params.y = Math.max(0, startY + dy);
                    windowManager.updateViewLayout(bubble, params);
                    return moved;
                }
                if (event.getAction() == MotionEvent.ACTION_UP && moved) return true;
                return false;
            }
        });

        windowManager.addView(bubble, params);
    }


    private void showAnswer(String answer) {
        if (windowManager == null) return;
        if (answerPanel == null) {
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(16), dp(12), dp(16), dp(16));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF21B1724);
            bg.setCornerRadius(dp(18));
            bg.setStroke(dp(1), 0xFFB794F6);
            panel.setBackground(bg);
            LinearLayout header = new LinearLayout(this);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = new TextView(this);
            title.setText("StudyBuddy");
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(17);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView close = new TextView(this);
            close.setText("×");
            close.setTextColor(0xFFFFFFFF);
            close.setTextSize(28);
            close.setGravity(Gravity.CENTER);
            header.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
            close.setOnClickListener(v -> hideAnswer());
            panel.addView(header);
            ScrollView scroll = new ScrollView(this);
            answerText = new TextView(this);
            answerText.setTextColor(0xFFF4EFFA);
            answerText.setTextSize(15);
            answerText.setTextIsSelectable(true);
            answerText.setPadding(0, dp(8), 0, dp(4));
            scroll.addView(answerText);
            panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(280)));
            answerPanel = panel;
        }
        answerText.setText(answer);
        if (answerPanel.getParent() == null) {
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(dp(300), -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            p.y = dp(90);
            windowManager.addView(answerPanel, p);
        }
    }

    private void hideAnswer() {
        if (windowManager != null && answerPanel != null && answerPanel.getParent() != null) windowManager.removeView(answerPanel);
        answerPanel = null;
        answerText = null;
    }

    private android.graphics.drawable.GradientDrawable makeBubbleBackground() {
        android.graphics.drawable.GradientDrawable shape =
            new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(0xFF8B5CF6);
        shape.setStroke(dp(2), 0xFFE9D5FF);
        return shape;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        if (answerReceiver != null) { try { unregisterReceiver(answerReceiver); } catch (Exception ignored) {} }
        hideAnswer();
        if (windowManager != null && bubble != null) {
            try { windowManager.removeView(bubble); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
