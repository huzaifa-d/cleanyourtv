package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PrivateProfileTest {
    @Test public void privatePhotoIsBoundedCircularAndZoomed() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = File.createTempFile("profile-fixture-", ".jpg", context.getCacheDir());
        Bitmap source = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Bitmap photo = null;
        try {
            Canvas canvas = new Canvas(source);
            canvas.drawColor(Color.GREEN);
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            canvas.drawRect(0, 0, 128, 512, paint);
            paint.setColor(Color.BLUE);
            canvas.drawRect(384, 0, 512, 512, paint);
            try (FileOutputStream output = new FileOutputStream(file)) {
                assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 95, output));
            }
            photo = ProfilePhoto.load(file);
            assertNotNull(photo);
            assertEquals(256, photo.getWidth());
            assertEquals(256, photo.getHeight());
            assertEquals(0, Color.alpha(photo.getPixel(0, 0)));
            assertEquals(255, Color.alpha(photo.getPixel(128, 128)));
            assertTrue("The 1.5x crop excludes the outer colour band",
                    Color.green(photo.getPixel(40, 128)) > 220);
            Bitmap loaded = photo;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                OutputResolutionIconView view = new OutputResolutionIconView(context);
                view.setImageResource(R.drawable.aurora_profile);
                view.layout(0, 0, 21, 21);
                view.setBitmapContent(loaded);
                view.setOutputScale(4f);
                view.layout(0, 0, 30, 30);
                assertSame(loaded, ((BitmapDrawable) view.getDrawable()).getBitmap());
                view.setImageResource(R.drawable.aurora_profile);
                assertNotSame(loaded, ((BitmapDrawable) view.getDrawable()).getBitmap());
            });
        } finally {
            source.recycle();
            if (photo != null) photo.recycle();
            assertTrue(file.delete());
        }
    }
}
