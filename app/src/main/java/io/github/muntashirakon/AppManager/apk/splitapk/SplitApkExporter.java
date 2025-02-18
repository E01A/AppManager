// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Used to generate app bundle with .apks extension. This file has all the apks as well as 3 other
 * file, such as icon.png, meta.sai_v1.json, meta.sai_v2.json.<br />
 * meta.sai_v1.json contains the following properties: export_timestamp (long), label (string),
 * package (string), version_code (long) and version_name (string).<br />
 * meta.sai_v2.json contains the following properties: export_timestamp (long), split_apk (boolean),
 * label (string), meta_version (long), min_sdk (long), package (string), target_sdk (long),
 * version_code (long), version_name (string), backup_components [ size (long), type (string) ]
 */
public final class SplitApkExporter {
    @WorkerThread
    public static void saveApks(PackageInfo packageInfo, Path apksFile) throws IOException {
        try (OutputStream outputStream = apksFile.openOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.setMethod(ZipOutputStream.DEFLATED);
            zipOutputStream.setLevel(Deflater.BEST_COMPRESSION);

            List<Path> apkFiles = getAllApkFiles(packageInfo);
            Collections.sort(apkFiles);

            // Count total file size
            long totalApkBytesCount = 0;
            for (Path apkFile : apkFiles) {
                totalApkBytesCount += apkFile.length();
            }

            // Metadata
            ApksMetadata apksMetadata = new ApksMetadata(packageInfo);
            apksMetadata.setupMetadata();
            apksMetadata.backupComponents = Collections.singletonList(new ApksMetadata.BackupComponent("apk_files", totalApkBytesCount));

            // Add metadata v2
            byte[] metaV2 = apksMetadata.getMetadataV2().getBytes();
            ZipEntry metaV2ZipEntry = new ZipEntry(ApksMetadata.META_V2_FILE);
            metaV2ZipEntry.setMethod(ZipEntry.DEFLATED);
            metaV2ZipEntry.setSize(metaV2.length);
            metaV2ZipEntry.setCrc(DigestUtils.calculateCrc32(metaV2));
            metaV2ZipEntry.setTime(apksMetadata.exportTimestamp);
            zipOutputStream.putNextEntry(metaV2ZipEntry);
            zipOutputStream.write(metaV2);
            zipOutputStream.closeEntry();

            // Add metadata V1
            byte[] metaV1 = apksMetadata.getMetadataV1().getBytes();
            ZipEntry metaV1ZipEntry = new ZipEntry(ApksMetadata.META_V1_FILE);
            metaV1ZipEntry.setMethod(ZipEntry.DEFLATED);
            metaV1ZipEntry.setSize(metaV1.length);
            metaV1ZipEntry.setCrc(DigestUtils.calculateCrc32(metaV1));
            metaV1ZipEntry.setTime(apksMetadata.exportTimestamp);
            zipOutputStream.putNextEntry(metaV1ZipEntry);
            zipOutputStream.write(metaV1);
            zipOutputStream.closeEntry();

            // Add icon
            Bitmap bitmap = FileUtils.getBitmapFromDrawable(packageInfo.applicationInfo.loadIcon(AppManager.getContext().getPackageManager()));
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngOutputStream);
            byte[] pngIcon = pngOutputStream.toByteArray();
            ZipEntry pngZipEntry = new ZipEntry(ApksMetadata.ICON_FILE);
            pngZipEntry.setMethod(ZipEntry.DEFLATED);
            pngZipEntry.setSize(pngIcon.length);
            pngZipEntry.setCrc(DigestUtils.calculateCrc32(pngIcon));
            pngZipEntry.setTime(apksMetadata.exportTimestamp);
            zipOutputStream.putNextEntry(pngZipEntry);
            zipOutputStream.write(pngIcon);
            zipOutputStream.closeEntry();

            // Add files
            for (Path apkFile : apkFiles) {
                ZipEntry zipEntry = new ZipEntry(apkFile.getName());
                zipEntry.setMethod(ZipEntry.DEFLATED);
                zipEntry.setSize(apkFile.length());
                zipEntry.setCrc(DigestUtils.calculateCrc32(apkFile));
                zipEntry.setTime(apksMetadata.exportTimestamp);
                zipOutputStream.putNextEntry(zipEntry);
                try (InputStream apkInputStream = apkFile.openInputStream()) {
                    IoUtils.copy(apkInputStream, zipOutputStream);
                }
                zipOutputStream.closeEntry();
            }
        }
    }

    @NonNull
    private static List<Path> getAllApkFiles(@NonNull PackageInfo packageInfo) {
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        List<Path> apkFiles = new ArrayList<>();
        apkFiles.add(Paths.get(applicationInfo.publicSourceDir));
        if (applicationInfo.splitPublicSourceDirs != null) {
            // FIXME: 8/5/22 This does not work for disabled apps
            for (String splitPath : applicationInfo.splitPublicSourceDirs)
                apkFiles.add(Paths.get(splitPath));
        }
        return apkFiles;
    }
}
