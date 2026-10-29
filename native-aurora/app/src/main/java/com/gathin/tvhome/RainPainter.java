package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.Random;

final class RainPainter {
    private final RainField field = new RainField();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF target = new RectF();
    private final float[] position = new float[2];
    private final float[][] ripples = new float[22][5];
    private final Bitmap[] streak = new Bitmap[2], ring = new Bitmap[2];
    private int outputWidth;

    RainPainter() {
        Random random = new Random(701);
        for (float[] ripple : ripples) {
            ripple[0] = 90 + random.nextFloat() * 780;
            ripple[1] = 405 + random.nextFloat() * 118;
            ripple[2] = random.nextFloat();
            ripple[3] = 5 + random.nextInt(4);
            ripple[4] = 5 + random.nextFloat() * 10;
        }
    }

    void draw(Canvas canvas, long millis, boolean night, int width) {
        if (outputWidth != width) createSprites(width);
        int palette = night ? 1 : 0;
        double seconds = millis / 1000.0;
        for (int i = 0; i < RainField.COUNT; i++) {
            field.sample(i, seconds, position);
            float length = field.length[i];
            target.set(position[0] - length * .2f - 1, position[1],
                    position[0] + 1, position[1] + length);
            paint.setAlpha(Math.round(field.alpha[i] * 255));
            canvas.drawBitmap(streak[palette], null, target, paint);
        }
        for (float[] ripple : ripples) {
            double progress = seconds * ripple[3] / 12 + ripple[2];
            progress -= Math.floor(progress);
            float radius = 1 + ripple[4] * (float) progress;
            paint.setAlpha((int) Math.round(51 * Math.pow(Math.sin(Math.PI * progress), 2)));
            target.set(ripple[0] - radius, ripple[1] - radius * .15f,
                    ripple[0] + radius, ripple[1] + radius * .15f);
            canvas.drawBitmap(ring[palette], null, target, paint);
        }
    }

    private void createSprites(int width) {
        float scale = width / 960f;
        for (int i = 0; i < 2; i++) {
            int colour = i == 0 ? 0xff739dbf : 0xffbed7ea;
            Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setStrokeWidth(.8f);
            ink.setShader(new LinearGradient(0, 0, 0, 36,
                    colour & 0x00ffffff, colour, Shader.TileMode.CLAMP));
            streak[i] = Bitmap.createBitmap((int) Math.ceil(10 * scale),
                    (int) Math.ceil(36 * scale), Bitmap.Config.ARGB_8888);
            Canvas software = new Canvas(streak[i]);
            software.scale(scale, scale);
            software.drawLine(8, 1, 1, 35, ink);
            ink.setShader(null);
            ink.setColor(colour);
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(.4f);
            ring[i] = Bitmap.createBitmap((int) Math.ceil(34 * scale),
                    (int) Math.ceil(7 * scale), Bitmap.Config.ARGB_8888);
            software = new Canvas(ring[i]);
            software.scale(scale, scale);
            software.drawOval(1, 1, 33, 6, ink);
        }
        outputWidth = width;
    }
}
