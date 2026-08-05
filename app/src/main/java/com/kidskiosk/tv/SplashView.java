package com.kidskiosk.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

/**
 * Branded loading screen shown while the WebView loads the kiosk page.
 * The original design spec described a 320x660 portrait card (44px
 * radius, accent glow, animated 3-dot progress at a ~600ms cycle) — this
 * adapts those same elements and timing for this app's actual fullscreen
 * landscape TV context rather than implementing the portrait shape
 * literally, since a phone-shaped card doesn't map onto a landscape
 * screen. Not visually verified against a mockup or emulator — see the
 * v1.1 plan's note on why.
 *
 * Its own View (not built inline in MainActivity, unlike the rest of this
 * app's UI) because it has a real animation lifecycle — startDotPulse/
 * stopAnimating — that's cleaner encapsulated here than spread across
 * MainActivity's own fields and callbacks.
 */
public class SplashView extends FrameLayout {

    private static final int DOT_COUNT = 3;
    private static final int DOT_CYCLE_MS = 600;

    private final ValueAnimator[] dotAnimators = new ValueAnimator[DOT_COUNT];

    public SplashView(Context context) {
        super(context);
        setBackgroundColor(ContextCompat.getColor(context, R.color.tv_ground));

        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        columnParams.gravity = Gravity.CENTER;
        addView(column, columnParams);

        // Accent glow behind the launcher icon, standing in for the
        // spec's "accent glow" — a radial gradient fading from the accent
        // color at ~40% alpha at the center to fully transparent at the
        // edge, so it reads as a soft glow rather than a hard-edged disc.
        int glowSize = Math.round(220 * density);
        FrameLayout iconStack = new FrameLayout(context);
        column.addView(iconStack, new LinearLayout.LayoutParams(glowSize, glowSize));

        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(glowSize / 2f);
        int accent = ContextCompat.getColor(context, R.color.tv_accent);
        glow.setColors(new int[]{(accent & 0x00FFFFFF) | 0x66000000, accent & 0x00FFFFFF});
        ImageView glowView = new ImageView(context);
        glowView.setImageDrawable(glow);
        iconStack.addView(glowView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        int iconSize = Math.round(120 * density);
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.mipmap.ic_launcher);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER;
        iconStack.addView(icon, iconParams);

        // 3-dot progress row, each dot pulsing in sequence.
        LinearLayout dotsRow = new LinearLayout(context);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotsRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dotsRowParams.topMargin = Math.round(32 * density);
        column.addView(dotsRow, dotsRowParams);

        int dotSize = Math.round(12 * density);
        int dotSpacing = Math.round(8 * density);
        int accent300 = ContextCompat.getColor(context, R.color.tv_accent_300);
        for (int i = 0; i < DOT_COUNT; i++) {
            View dot = new View(context);
            GradientDrawable dotShape = new GradientDrawable();
            dotShape.setShape(GradientDrawable.OVAL);
            dotShape.setColor(accent300);
            dot.setBackground(dotShape);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
            if (i > 0) dotParams.leftMargin = dotSpacing;
            dotsRow.addView(dot, dotParams);
            dotAnimators[i] = startDotPulse(dot, i * (DOT_CYCLE_MS / DOT_COUNT));
        }
    }

    private ValueAnimator startDotPulse(View dot, int startDelay) {
        ValueAnimator animator = ValueAnimator.ofFloat(0.35f, 1f, 0.35f);
        animator.setDuration(DOT_CYCLE_MS);
        animator.setStartDelay(startDelay);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> dot.setAlpha((float) a.getAnimatedValue()));
        animator.start();
        return animator;
    }

    /** Stops the dot animations — call once the splash is hidden, so they're
     * not still ticking in the background after it's no longer visible. */
    public void stopAnimating() {
        for (ValueAnimator animator : dotAnimators) {
            if (animator != null) animator.cancel();
        }
    }
}
