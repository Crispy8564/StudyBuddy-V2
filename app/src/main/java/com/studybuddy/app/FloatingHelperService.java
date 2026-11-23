package com.studybuddy.app;

import android.app.Service;
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

        button.setOnClickListener(v -> {
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(open);
        });

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
