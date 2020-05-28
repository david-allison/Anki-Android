/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package androidx.test.runner;

import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

import com.ichi2.compat.CompatHelper;

import org.junit.runner.Request;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import androidx.annotation.NonNull;
import androidx.multidex.MultiDex;
import androidx.test.internal.runner.BuilderAA;
import androidx.test.internal.runner.RunnerArgs;
import androidx.test.internal.runner.TestRequestBuilder;
import dalvik.system.DexFile;
import timber.log.Timber;

public class CustomRunner extends AndroidJUnitRunner {

    public CustomRunner() {
        Log.e("HELLO", "WORLD");
    }


    @Override
    public void start() {
        Log.w("CustomRunner", "start");
        super.start();
    }


    @Override
    public void onCreate(Bundle arguments)
    {
        //MultiDex.install(getTargetContext());
        MultiDex.installInstrumentation(this.getContext(), getTargetContext());
        Log.w("CustomRunner", "onCreate");
        super.onCreate(arguments);
    }

    @Override
    Request buildRequest(RunnerArgs runnerArgs, Bundle bundleArgs) {



        return super.buildRequest(runnerArgs, bundleArgs);
//
//        for (String k: bundleArgs.keySet()) {
//            Log.e("CustomRunner", String.format("%s - %s", k, bundleArgs.get(k)));
//        }
//
//        String packageCodePath = getContext().getPackageCodePath();
//        listEntries(packageCodePath);
//
//
//        return customBuildRequest(runnerArgs, bundleArgs);
    }


    private void listEntries2(String path) {
        ZipFile zip;
        try {
            zip = new ZipFile(new File(path), ZipFile.OPEN_READ);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<String> fileNames = new ArrayList<>();

        try {
            String targetDirectory = getTargetContext().getCacheDir().getAbsolutePath();

            File tempDir = new File(targetDirectory);
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                throw new IOException("Failed to create target directory: " + targetDirectory);
            }

            for (ZipEntry ze : Collections.list(zip.entries())) {
                String name = ze.getName();
                if (!name.endsWith(".dex") || ze.isDirectory()) {
                    continue;
                }

                File destFile = new File(tempDir, name);
                if (!isInside(tempDir, destFile)) {
                    Timber.e("Refusing to decompress invalid path: %s", destFile.getCanonicalPath());
                    throw new IOException("File is outside extraction target directory.");
                }

                try (InputStream zis = zip.getInputStream(ze)) {
                    writeToFile(destFile, zis);
                }

                fileNames.add(destFile.getAbsolutePath());

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Log.e("CustomRunner", "Waiting");
        try {
            Thread.sleep(3000);
        } catch (Exception e) {

        }

        for (String dexPath: fileNames) {
            Log.e("CustomRunner", "loading path: " + dexPath);
            listEntries(dexPath);
        }
    }


    private void writeToFile(File destFile, InputStream zis) throws IOException {
        // sometimes this fails and works on retries (hardware issue?)
        final int retries = 5;
        int retryCnt = 0;
        boolean success = false;
        while (!success && retryCnt++ < retries) {
            try {
                writeToFileImpl(destFile, zis);
                success = true;
            } catch (IOException e) {
                if (retryCnt == retries) {
                    Timber.e("IOException while writing to file, out of retries.");
                    throw e;
                } else {
                    Timber.e("IOException while writing to file, retrying...");
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
    }


    private void writeToFileImpl(File destFile, InputStream zis) throws IOException {
        String destination = destFile.getAbsolutePath();
        File f = new File(destination);
        try {
            Timber.d("Creating new file... = %s", destination);
            f.createNewFile();

            long startTimeMillis = System.currentTimeMillis();
            long sizeBytes = copyFile(zis, destination);
            long endTimeMillis = System.currentTimeMillis();

            Timber.d("Finished writeToFile!");
            long durationSeconds = (endTimeMillis - startTimeMillis) / 1000;
            long sizeKb = sizeBytes / 1024;
            long speedKbSec = 0;
            if (endTimeMillis != startTimeMillis) {
                speedKbSec = sizeKb * 1000 / (endTimeMillis - startTimeMillis);
            }
            Timber.d("Utils.writeToFile: Size: %d Kb, Duration: %d s, Speed: %d Kb/s", sizeKb, durationSeconds, speedKbSec);
        } catch (IOException e) {
            throw new IOException(f.getName() + ": " + e.getLocalizedMessage(), e);
        }
    }


    private long copyFile(InputStream source, String target) throws IOException {
        long bytesCopied;

        try (OutputStream targetStream = new FileOutputStream(target)) {
            bytesCopied = copyFile(source, targetStream);
        } catch (IOException ioe) {
            Timber.e(ioe, "Error while copying to file %s", target);
            throw ioe;
        }
        return bytesCopied;
    }

    private long copyFile(@NonNull InputStream source, @NonNull OutputStream target) throws IOException {
        // balance memory and performance, it appears 32k is the best trade-off
        // https://stackoverflow.com/questions/10143731/android-optimal-buffer-size
        final byte[] buffer = new byte[1024 * 32];
        long count = 0;
        int n;
        while ((n = source.read(buffer)) != -1) {
            target.write(buffer, 0, n);
            count += n;
        }
        target.flush();
        return count;
    }

    //inlined from Utils
    private boolean isInside(File tempDir, File destFile) throws IOException {
        return destFile.getCanonicalPath().startsWith(tempDir.getCanonicalPath());
    }


    private void listEntries(String packageCodePath) {
        DexFile dexFile = null;
        try {
            dexFile = new DexFile(packageCodePath);
            //dexFile = new DexFile(getTargetContext().getPackageCodePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to handle " + packageCodePath + " " + e.getLocalizedMessage(), e);
        }
        Enumeration<String> classNames = dexFile.entries();
        Log.e("CustomRunner", "Completed list");
        for (String entry: Collections.list(classNames)) {
            if (!entry.contains("ichi")) {
                continue;
            }
            Log.e("CustomRunner", String.format("Entry - %s", entry));
        }
        Log.e("CustomRunner", "Completed list");
    }


    private Request customBuildRequest(RunnerArgs runnerArgs, Bundle bundleArgs) {
        TestRequestBuilder builder = createTestRequestBuilder(this, bundleArgs);
        builder.addPathsToScan(runnerArgs.classpathToScan);
        if (runnerArgs.classpathToScan.isEmpty()) {
            // Only scan for tests for current apk aka testContext
            // Note that this represents a change from InstrumentationTestRunner where
            // getTargetContext().getPackageCodePath() aka app under test was also scanned
            // Only add the package classpath when no custom classpath is provided in order to
            // avoid duplicate class issues.
            builder.addPathToScan(getTargetContext().getPackageCodePath());
        }

        builder.addFromRunnerArgs(runnerArgs);

        return builder.build();
    }


    @Override
    TestRequestBuilder createTestRequestBuilder(Instrumentation instr, Bundle arguments) {
        return new BuilderAA(instr, arguments);
        //return super.createTestRequestBuilder(instr, arguments);
    }
}
