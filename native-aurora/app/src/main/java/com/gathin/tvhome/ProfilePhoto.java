package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import java.io.File;
import java.io.IOException;

final class ProfilePhoto {
    private static final int MAX_SIZE = 256;

    static int sampleSize(int width, int height) {
        int sample = 1;
        while (Math.max(width, height) / (sample * 2) >= MAX_SIZE) sample *= 2;
        return sample;
    }

    static Bitmap load(File file) throws IOException {
        if (file == null || !file.exists()) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw new IOException("Profile photo is unreadable or unsupported");
        }
        options.inSampleSize = sampleSize(options.outWidth, options.outHeight);
        options.inJustDecodeBounds = false;
        Bitmap source = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (source == null) throw new IOException("Could not decode profile photo");
        try {
            int size = Math.min(MAX_SIZE, Math.min(source.getWidth(), source.getHeight()));
            Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            result.setDensity(Bitmap.DENSITY_NONE);
            BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float scale = 1.5f * size / Math.min(source.getWidth(), source.getHeight());
            Matrix transform = new Matrix();
            transform.setScale(scale, scale);
            transform.postTranslate((size - source.getWidth() * scale) / 2,
                    (size - source.getHeight() * scale) / 2);
            shader.setLocalMatrix(transform);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setShader(shader);
            new Canvas(result).drawCircle(size / 2f, size / 2f, size / 2f, paint);
            return result;
        } finally {
            source.recycle();
        }
    }
}
