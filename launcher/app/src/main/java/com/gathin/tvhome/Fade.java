package com.gathin.tvhome;

import android.os.SystemClock;
import android.view.View;

/**
 * An alpha fade that runs on the frame clock itself, so it still plays on TVs whose animator duration scale is 0
 * (there ViewPropertyAnimator jumps straight to the end). A new fade on the same view takes over from the alpha
 * the view has, so a fade can be reversed half way.
 */
final class Fade {
    private Fade() {
    }

    static void to(View v, float target, long durationMs) {
        Object tag = v.getTag(R.id.fade_token);
        int token = (tag instanceof Integer ? (Integer) tag : 0) + 1;
        v.setTag(R.id.fade_token, token);
        float from = v.getAlpha();
        if (target > 0f) v.setVisibility(View.VISIBLE);
        if (from == target || durationMs <= 0) {
            v.setAlpha(target);
            if (target == 0f && !v.hasFocus()) v.setVisibility(View.INVISIBLE);
            return;
        }
        long start = SystemClock.uptimeMillis();
        Runnable step = new Runnable() {
            @Override
            public void run() {
                if (!Integer.valueOf(token).equals(v.getTag(R.id.fade_token))) return;
                float t = Math.min(1f, (SystemClock.uptimeMillis() - start) / (float) durationMs);
                float eased = t * t * (3f - 2f * t);   // smoothstep: soft at both ends
                v.setAlpha(from + (target - from) * eased);
                if (t < 1f) {
                    v.postOnAnimation(this);
                } else if (target == 0f && !v.hasFocus()) {
                    v.setVisibility(View.INVISIBLE);   // nothing left to draw; a view holding the focus keeps it
                }
            }
        };
        v.postOnAnimation(step);
    }
}
