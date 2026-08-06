package com.kidskiosk.tv;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/** Remote-friendly error state used while the kiosk server is unavailable. */
@SuppressLint("ViewConstructor")
final class KioskErrorView extends FrameLayout {

    KioskErrorView(Context context, String detail, Runnable retryAction) {
        super(context);
        setBackgroundColor(ContextCompat.getColor(context, R.color.tv_ground));

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        int horizontalPadding = Math.round(48 * density);
        column.setPadding(horizontalPadding, 0, horizontalPadding, 0);

        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        columnParams.gravity = Gravity.CENTER;
        addView(column, columnParams);

        TextView title = new TextView(context);
        title.setText(R.string.load_error_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        column.addView(title);

        TextView message = new TextView(context);
        message.setText(detail);
        message.setTextColor(ContextCompat.getColor(context, R.color.tv_accent_300));
        message.setTextSize(18);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = Math.round(16 * density);
        column.addView(message, messageParams);

        TextView retryStatus = new TextView(context);
        retryStatus.setText(R.string.retry_status);
        retryStatus.setTextColor(Color.LTGRAY);
        retryStatus.setTextSize(16);
        retryStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Math.round(12 * density);
        column.addView(retryStatus, statusParams);

        Button retry = new Button(context);
        retry.setText(R.string.retry_now);
        retry.setAllCaps(false);
        retry.setOnClickListener(view -> retryAction.run());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = Math.round(24 * density);
        column.addView(retry, buttonParams);
        retry.requestFocus();
    }
}
