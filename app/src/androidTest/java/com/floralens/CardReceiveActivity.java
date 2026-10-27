package com.floralens;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Separate APK deliberately uses only platform Java, not libraries supplied by the target APK. */
public class CardReceiveActivity extends Activity {
    @SuppressWarnings("deprecation")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String result;
        try {
            Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null) throw new IllegalArgumentException("No URI");
            Bitmap image;
            byte[] magic = new byte[8];
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null || stream.read(magic) != 8 || (magic[0] & 255) != 137 || magic[1] != 80 || magic[2] != 78 || magic[3] != 71)
                    throw new IllegalArgumentException("Not PNG");
            }
            try (InputStream stream = getContentResolver().openInputStream(uri)) { image = BitmapFactory.decodeStream(stream); }
            if (image == null) throw new IllegalArgumentException("PNG decode failed");
            result = "READ_OK uid=" + android.os.Process.myUid() + " uri=" + uri + " size=" + image.getWidth() + "x" + image.getHeight() + " PNG_MAGIC_OK";
            image.recycle();
        } catch (Exception error) { result = "READ_FAILED " + error.getClass().getSimpleName(); }
        try (FileOutputStream stream = new FileOutputStream(new File(getFilesDir(), "phase4-received.txt"))) {
            stream.write(result.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) { result += " RESULT_WRITE_FAILED"; }
        TextView view = new TextView(this); view.setText(result); view.setTextSize(20f); view.setPadding(24,64,24,24); setContentView(view);
    }
}
