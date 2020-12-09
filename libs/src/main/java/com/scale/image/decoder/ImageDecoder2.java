package com.scale.image.decoder;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageDecoder2 {
    private static final String FILE_PREFIX = "file://";
    private static final String ASSET_PREFIX = FILE_PREFIX + "/android_asset/";
    private static final String RESOURCE_PREFIX = ContentResolver.SCHEME_ANDROID_RESOURCE + "://";

    public static InputStream getImageInput(Context context, @NonNull Uri uri) throws Exception {
        String uriString = uri.toString();
        if (uriString.startsWith(RESOURCE_PREFIX)) {
            Resources res;
            String packageName = uri.getAuthority();
            if (context.getPackageName().equals(packageName)) {
                res = context.getResources();
            } else {
                PackageManager pm = context.getPackageManager();
                res = pm.getResourcesForApplication(packageName);
            }

            int id = 0;
            List<String> segments = uri.getPathSegments();
            int size = segments.size();
            if (size == 2 && segments.get(0).equals("drawable")) {
                String resName = segments.get(1);
                id = res.getIdentifier(resName, "drawable", packageName);
            } else if (size == 1 && TextUtils.isDigitsOnly(segments.get(0))) {
                try {
                    id = Integer.parseInt(segments.get(0));
                } catch (NumberFormatException ignored) {
                }
            }
            final TypedValue value = new TypedValue();
            return res.openRawResource(id, value);
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            String assetName = uriString.substring(ASSET_PREFIX.length());
            return context.getAssets().open(assetName);
        } else if (uriString.startsWith(FILE_PREFIX)) {
            return new FileInputStream(uriString.substring(FILE_PREFIX.length()));
        } else {
            ContentResolver contentResolver = context.getContentResolver();
            return contentResolver.openInputStream(uri);
        }
    }

    public static int getOrientation(Context context,Uri uri) {
        InputStream inputStream = null;
        try {
            inputStream = getImageInput(context,uri);

            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    if ("Orientation".equals(tag.getTagName())) {
                        String description = tag.getDescription();
                        Pattern pattern = Pattern.compile(" (\\d*) ");
                        Matcher matcher = pattern.matcher(description);
                        if (matcher.find()) {
                            String group = matcher.group(1);
                            return Integer.parseInt(group);
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
        }
        return 0;
    }

}
