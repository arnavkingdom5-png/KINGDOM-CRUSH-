package com.kingdom.crush;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Handles AndroidBridge.shareAchievement(base64Png, title, text).
 * c.html generates the achievement image itself (canvas.toDataURL) and hands
 * us the raw base64 PNG bytes — we just need to write it to a shareable file
 * and hand it to the system share sheet.
 */
final class ShareHelper {
    private static final String TAG = "KingdomCrush";

    private ShareHelper() {}

    static void shareImage(Context context, String base64Png, String title, String text) {
        try {
            File dir = new File(context.getCacheDir(), "shared");
            if (!dir.exists()) dir.mkdirs();
            File imgFile = new File(dir, "kingdom-crush-share.png");

            byte[] bytes = Base64.decode(base64Png, Base64.DEFAULT);
            try (FileOutputStream out = new FileOutputStream(imgFile)) {
                out.write(bytes);
            }

            Uri uri = FileProvider.getUriForFile(context, "com.kingdom.crush.fileprovider", imgFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(Intent.createChooser(shareIntent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w(TAG, "shareAchievement failed: " + e.getMessage());
        }
    }
                                  }
