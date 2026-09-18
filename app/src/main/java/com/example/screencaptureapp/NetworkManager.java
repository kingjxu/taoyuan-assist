package com.example.screencaptureapp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64OutputStream;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class NetworkManager {
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_FILE_COUNT = 50;
    private static final int READ_TIMEOUT_SECONDS = 12;
    private static final String TAG = "NetworkManager";
    private static final String UPLOAD_URL = "http://ddbaby.site/lyxz/gto_decision";
    private static final int WRITE_TIMEOUT_SECONDS = 10;
    private Context context;
    private String token;
    private String uuid;
    private final AtomicReference<Call> inFlightUploadCall = new AtomicReference<>(null);
    private final ReusableByteArrayOutputStream reusableStream = new ReusableByteArrayOutputStream(262144);
    private OkHttpClient client = createHttpClient();

    public NetworkManager(Context context, String str, String str2) {
        this.context = context;
        this.uuid = str;
        this.token = str2;
    }

    private OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder().connectTimeout(5L, TimeUnit.SECONDS).readTimeout(12L, TimeUnit.SECONDS).writeTimeout(10L, TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
    }

    public String uploadImage(Bitmap bitmap, long j) throws IOException {
        long jCurrentTimeMillis = System.currentTimeMillis();
        this.reusableStream.reset();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, this.reusableStream);
        long jCurrentTimeMillis2 = System.currentTimeMillis();
        int count = this.reusableStream.getCount();
        ScreenCaptureService.blog("NET: compress " + count + "B " + (jCurrentTimeMillis2 - jCurrentTimeMillis) + "ms");
        ScreenCaptureService.blog("NET: sending streaming body jpeg=" + count + "B");
        Request.Builder builderAddHeader = new Request.Builder().url(UPLOAD_URL).post(createUploadRequestBody(this.reusableStream.getBuffer(), this.reusableStream.getCount(), j)).addHeader("User-Agent", "ScreenCaptureApp/1.0").addHeader("Content-Type", "application/json");
        StringBuilder sb = new StringBuilder("Bearer ");
        String str = this.token;
        if (str == null) {
            str = "";
        }
        Call callNewCall = this.client.newCall(builderAddHeader.addHeader("Authorization", sb.append(str).toString()).build());
        if (!this.inFlightUploadCall.compareAndSet(null, callNewCall)) {
            throw new IOException("Upload request already in flight");
        }
        try {
            Response responseExecute = callNewCall.execute();
            try {
                long jCurrentTimeMillis3 = System.currentTimeMillis();
                if (responseExecute.isSuccessful() && responseExecute.body() != null) {
                    String strString = responseExecute.body().string();
                    ScreenCaptureService.blog("NET: OK " + (jCurrentTimeMillis3 - jCurrentTimeMillis2) + "ms resp=" + strString.length() + "B");
                    if (responseExecute != null) {
                        responseExecute.close();
                    }
                    return strString;
                }
                ScreenCaptureService.blog("NET: FAIL " + responseExecute.code() + " " + (jCurrentTimeMillis3 - jCurrentTimeMillis2) + "ms");
                throw new IOException("Server error: " + responseExecute.code());
            } finally {
            }
        } finally {
            this.inFlightUploadCall.compareAndSet(callNewCall, null);
        }
    }

    public void cancelInFlightUpload() {
        Call andSet = this.inFlightUploadCall.getAndSet(null);
        if (andSet != null) {
            ScreenCaptureService.blog("NET: cancel in-flight upload");
            andSet.cancel();
        }
    }

    private RequestBody createUploadRequestBody(final byte[] bArr, final int i, final long j) {
        return new RequestBody() { // from class: com.example.screencaptureapp.NetworkManager.1
            @Override // okhttp3.RequestBody
            public MediaType contentType() {
                return NetworkManager.JSON;
            }

            @Override // okhttp3.RequestBody
            public void writeTo(BufferedSink bufferedSink) throws IOException {
                bufferedSink.writeUtf8("{\"images\":[\"");
                Base64OutputStream base64OutputStream = new Base64OutputStream(new NonClosingOutputStream(bufferedSink.outputStream()), 2);
                try {
                    base64OutputStream.write(bArr, 0, i);
                    base64OutputStream.close();
                    bufferedSink.writeUtf8("\"],\"timestamp\":");
                    bufferedSink.writeUtf8(Long.toString(j));
                    bufferedSink.writeUtf8(",\"uuid\":\"");
                    NetworkManager networkManager = NetworkManager.this;
                    networkManager.writeEscapedJsonString(bufferedSink, networkManager.uuid);
                    bufferedSink.writeUtf8("\"}");
                } catch (Throwable th) {
                    base64OutputStream.close();
                    throw th;
                }
            }
        };
    }

    private void writeEscapedJsonString(BufferedSink bufferedSink, String str) throws IOException {
        if (str == null) {
            return;
        }
        for (int i = 0; i < str.length(); i++) {
            char cCharAt = str.charAt(i);
            if (cCharAt == '\t') {
                bufferedSink.writeUtf8("\\t");
            } else if (cCharAt == '\n') {
                bufferedSink.writeUtf8("\\n");
            } else if (cCharAt == '\r') {
                bufferedSink.writeUtf8("\\r");
            } else if (cCharAt == '\"' || cCharAt == '\\') {
                bufferedSink.writeByte(92);
                bufferedSink.writeByte(cCharAt);
            } else {
                bufferedSink.writeUtf8(str, i, i + 1);
            }
        }
    }

    private static final class ReusableByteArrayOutputStream extends ByteArrayOutputStream {
        ReusableByteArrayOutputStream(int i) {
            super(i);
        }

        byte[] getBuffer() {
            return this.buf;
        }

        int getCount() {
            return this.count;
        }
    }

    private static final class NonClosingOutputStream extends OutputStream {
        private final OutputStream delegate;

        NonClosingOutputStream(OutputStream outputStream) {
            this.delegate = outputStream;
        }

        @Override // java.io.OutputStream
        public void write(int i) throws IOException {
            this.delegate.write(i);
        }

        @Override // java.io.OutputStream
        public void write(byte[] bArr, int i, int i2) throws IOException {
            this.delegate.write(bArr, i, i2);
        }

        @Override // java.io.OutputStream, java.io.Flushable
        public void flush() throws IOException {
            this.delegate.flush();
        }

        @Override // java.io.OutputStream, java.io.Closeable, java.lang.AutoCloseable
        public void close() throws IOException {
            this.delegate.flush();
        }
    }

    private void saveToPublicPictures(Bitmap bitmap) throws IOException {
        String str = "DEBUG_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date()) + ".jpg";
        cleanOldPublicImages();
        ContentValues contentValues = new ContentValues();
        contentValues.put("_display_name", str);
        contentValues.put("mime_type", "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            contentValues.put("relative_path", Environment.DIRECTORY_PICTURES + "/ScreenCaptureDebug");
            contentValues.put("is_pending", (Integer) 1);
        }
        ContentResolver contentResolver = this.context.getContentResolver();
        Uri uriInsert = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        if (uriInsert != null) {
            try {
                OutputStream outputStreamOpenOutputStream = contentResolver.openOutputStream(uriInsert);
                try {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStreamOpenOutputStream);
                    if (Build.VERSION.SDK_INT >= 29) {
                        contentValues.clear();
                        contentValues.put("is_pending", (Integer) 0);
                        contentResolver.update(uriInsert, contentValues, null, null);
                    }
                    Log.i(TAG, "screen capture success save : " + str + ", to public store!!!");
                    if (outputStreamOpenOutputStream != null) {
                        outputStreamOpenOutputStream.close();
                    }
                } finally {
                }
            } catch (Exception e) {
                Log.e(TAG, "screen capture save to public store failed", e);
                contentResolver.delete(uriInsert, null, null);
            }
        }
    }

    private void cleanOldPublicImages() {
        try {
            ContentResolver contentResolver = this.context.getContentResolver();
            Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Cursor cursorQuery = contentResolver.query(uri, new String[]{"_id"}, "_display_name LIKE ?", new String[]{"DEBUG_%"}, "date_added ASC");
            if (cursorQuery != null) {
                try {
                    if (cursorQuery.getCount() > 50) {
                        int count = cursorQuery.getCount() - 50;
                        int columnIndexOrThrow = cursorQuery.getColumnIndexOrThrow("_id");
                        for (int i = 0; i < count; i++) {
                            if (!cursorQuery.moveToNext()) {
                                break;
                            }
                            contentResolver.delete(Uri.withAppendedPath(uri, String.valueOf(cursorQuery.getLong(columnIndexOrThrow))), null, null);
                        }
                    }
                } finally {
                }
            }
            if (cursorQuery != null) {
                cursorQuery.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "screen capture clean public store failed", e);
        }
    }

    private void saveBmpToLocalDebugDir(Bitmap bitmap) throws IOException {
        try {
            File file = new File(this.context.getExternalFilesDir(null), "debug_images");
            if (!file.exists() && !file.mkdirs()) {
                Log.e(TAG, "cannot create debug dir for screen capture app...");
                return;
            }
            cleanOldFiles(file);
            File file2 = new File(file, "SCREEN_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date()) + ".jpg");
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fileOutputStream);
                Log.i(TAG, "screen capture success save image: " + file2.getName());
                fileOutputStream.close();
            } finally {
            }
        } catch (Exception unused) {
            Log.e(TAG, "screen capture save image failed...");
        }
    }

    private void cleanOldFiles(File file) {
        File[] fileArrListFiles = file.listFiles();
        if (fileArrListFiles == null || fileArrListFiles.length <= 50) {
            return;
        }
        Arrays.sort(fileArrListFiles, (first, second) ->
                Long.compare(first.lastModified(), second.lastModified()));
        int length = fileArrListFiles.length - 50;
        for (int i = 0; i < length; i++) {
            if (fileArrListFiles[i].delete()) {
                Log.d(TAG, "screen capture success clean image file: " + fileArrListFiles[i].getName());
            }
        }
    }
}
