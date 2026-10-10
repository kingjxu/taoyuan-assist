package com.example.screencaptureapp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
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
    private static final String UPLOAD_URL = "https://ddbaby.site/lyxz/gto_decision";
    private static final int WRITE_TIMEOUT_SECONDS = 10;
    private static final String TRACE_ID_HEADER = "X-GTO-Trace-ID";
    private static final String CLIENT_STAGES_HEADER = "X-GTO-Client-Stages";
    private static final String PREVIOUS_TRACE_HEADER = "X-GTO-Previous-Trace";
    private static final String SERVER_TIMING_HEADER = "X-GTO-Server-Timing";
    private static final String DECISION_ACTION_HEADER = "X-GTO-Decision-Action";
    private static final String CAN_CHECK_HEADER = "X-GTO-Can-Check";
    private Context context;
    private String token;
    private String uuid;
    private final AtomicReference<Call> inFlightUploadCall = new AtomicReference<>(null);
    private final ReusableByteArrayOutputStream reusableStream = new ReusableByteArrayOutputStream(262144);
    private OkHttpClient client = createHttpClient();

    public NetworkManager(Context context, String accountId, String token) {
        this.context = context;
        this.uuid = accountId;
        this.token = token;
    }

    private OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public UploadResult uploadImage(
            Bitmap bitmap,
            long timestamp,
            LatencyTraceStore.TraceRecord trace
    ) throws IOException {
        trace.compressStartMs = SystemClock.elapsedRealtime();
        reusableStream.reset();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, reusableStream);
        trace.compressEndMs = SystemClock.elapsedRealtime();
        int jpegSize = reusableStream.getCount();
        ScreenCaptureService.blog("NET: compress " + jpegSize + "B "
                + (trace.compressEndMs - trace.compressStartMs) + "ms trace=" + trace.traceId);
        ScreenCaptureService.blog("NET: sending streaming body jpeg=" + jpegSize + "B");

        trace.httpStartMs = SystemClock.elapsedRealtime();
        Request.Builder requestBuilder = new Request.Builder()
                .url(UPLOAD_URL)
                .post(createUploadRequestBody(reusableStream.getBuffer(), jpegSize, timestamp, trace))
                .addHeader("User-Agent", "ScreenCaptureApp/1.0")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + (token == null ? "" : token))
                .addHeader(TRACE_ID_HEADER, trace.traceId)
                .addHeader(CLIENT_STAGES_HEADER, trace.currentStagesHeader());
        LatencyTraceStore.TraceRecord previousTrace = LatencyTraceStore.peekCompleted();
        if (previousTrace != null) {
            requestBuilder.addHeader(PREVIOUS_TRACE_HEADER, previousTrace.completedHeader());
        }
        Request request = requestBuilder.build();
        Call call = client.newCall(request);
        if (!inFlightUploadCall.compareAndSet(null, call)) {
            throw new IOException("Upload request already in flight");
        }

        try (Response response = call.execute()) {
            if (previousTrace != null) {
                LatencyTraceStore.acknowledgeCompleted(previousTrace.traceId);
            }
            String responseTraceId = response.header(TRACE_ID_HEADER);
            if (responseTraceId != null && !trace.traceId.equals(responseTraceId)) {
                throw new IOException("Trace id mismatch");
            }
            trace.applyServerTiming(response.header(SERVER_TIMING_HEADER));
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                trace.responseEndMs = SystemClock.elapsedRealtime();
                ScreenCaptureService.blog("NET: OK " + (trace.responseEndMs - trace.httpStartMs)
                        + "ms resp=" + responseBody.length() + "B trace=" + trace.traceId);
                return new UploadResult(
                        responseBody,
                        trace.traceId,
                        response.header(DECISION_ACTION_HEADER),
                        "1".equals(response.header(CAN_CHECK_HEADER))
                );
            }
            trace.responseEndMs = SystemClock.elapsedRealtime();
            ScreenCaptureService.blog("NET: FAIL " + response.code() + " "
                    + (trace.responseEndMs - trace.httpStartMs) + "ms trace=" + trace.traceId);
            throw new IOException("Server error: " + response.code());
        } finally {
            inFlightUploadCall.compareAndSet(call, null);
        }
    }

    public void cancelInFlightUpload() {
        Call call = inFlightUploadCall.getAndSet(null);
        if (call != null) {
            ScreenCaptureService.blog("NET: cancel in-flight upload");
            call.cancel();
        }
    }

    private RequestBody createUploadRequestBody(
            final byte[] jpegBuffer,
            final int jpegLength,
            final long timestamp,
            final LatencyTraceStore.TraceRecord trace
    ) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                trace.requestBodyStartMs = SystemClock.elapsedRealtime();
                sink.writeUtf8("{\"images\":[\"");
                Base64OutputStream base64Stream = new Base64OutputStream(
                        new NonClosingOutputStream(sink.outputStream()),
                        android.util.Base64.NO_WRAP
                );
                try {
                    base64Stream.write(jpegBuffer, 0, jpegLength);
                } finally {
                    base64Stream.close();
                }
                sink.writeUtf8("\"],\"timestamp\":");
                sink.writeUtf8(Long.toString(timestamp));
                sink.writeUtf8(",\"uuid\":\"");
                writeEscapedJsonString(sink, uuid);
                sink.writeUtf8("\"}");
                trace.requestBodyEndMs = SystemClock.elapsedRealtime();
            }
        };
    }

    public static final class UploadResult {
        public final String responseBody;
        public final String traceId;
        public final String decisionAction;
        public final boolean canCheck;

        UploadResult(String responseBody, String traceId, String decisionAction, boolean canCheck) {
            this.responseBody = responseBody;
            this.traceId = traceId;
            this.decisionAction = decisionAction;
            this.canCheck = canCheck;
        }
    }

    private void writeEscapedJsonString(BufferedSink sink, String value) throws IOException {
        if (value == null) {
            return;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\t') {
                sink.writeUtf8("\\t");
            } else if (character == '\n') {
                sink.writeUtf8("\\n");
            } else if (character == '\r') {
                sink.writeUtf8("\\r");
            } else if (character == '"' || character == '\\') {
                sink.writeByte('\\');
                sink.writeByte(character);
            } else {
                sink.writeUtf8(value, index, index + 1);
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

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            delegate.write(buffer, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.flush();
        }
    }

    private void saveToPublicPictures(Bitmap bitmap) {
        String fileName = "DEBUG_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date()) + ".jpg";

        // 自动清理旧文件
        cleanOldPublicImages();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        // 指定子目录: Pictures/ScreenCaptureDebug
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScreenCaptureDebug");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri imageUri = resolver.insert(collection, values);

        if (imageUri != null) {
            try (OutputStream os = resolver.openOutputStream(imageUri)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(imageUri, values, null, null);
                }
                Log.i(TAG, "screen capture success save : " + fileName +", to public store!!!");
            } catch (Exception e) {
                Log.e(TAG, "screen capture save to public store failed", e);
                resolver.delete(imageUri, null, null);
            }
        }
    }

    /**
     * 清理逻辑：删除 MediaStore 中属于本应用的旧调试图
     */
    private void cleanOldPublicImages() {
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

            // 匹配文件名开头为 DEBUG_ 的图片
            String selection = MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?";
            String[] selectionArgs = new String[]{"DEBUG_%"};
            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " ASC";

            try (Cursor cursor = resolver.query(collection, new String[]{MediaStore.Images.Media._ID}, selection, selectionArgs, sortOrder)) {
                if (cursor != null && cursor.getCount() > MAX_FILE_COUNT) {
                    int deleteCount = cursor.getCount() - MAX_FILE_COUNT;
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);

                    for (int i = 0; i < deleteCount && cursor.moveToNext(); i++) {
                        long id = cursor.getLong(idColumn);
                        Uri deleteUri = Uri.withAppendedPath(collection, String.valueOf(id));
                        resolver.delete(deleteUri, null, null);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "screen capture clean public store failed", e);
        }
    }

    private void saveBmpToLocalDebugDir(Bitmap bitmap) {
        try {
            File debugDir = new File(context.getExternalFilesDir(null),"debug_images");
            if(!debugDir.exists() && !debugDir.mkdirs())
            {
                Log.e(TAG, "cannot create debug dir for screen capture app...");
                return;
            }

            cleanOldFiles(debugDir);

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date());
            File file = new File(debugDir, "SCREEN_" + timeStamp + ".jpg");

            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                Log.i(TAG, "screen capture success save image: " + file.getName());
            }
        } catch (Exception e)
        {
            Log.e(TAG, "screen capture save image failed...");
        }
    }

    private void cleanOldFiles(File dir) {
        File[] files = dir.listFiles();
        if (files != null && files.length > MAX_FILE_COUNT) {
            // 按修改时间排序（从旧到新）
            Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));

            int deleteCount = files.length - MAX_FILE_COUNT;
            for (int i = 0; i < deleteCount; i++) {
                if (files[i].delete()) {
                    Log.d(TAG, "screen capture success clean image file: " + files[i].getName());
                }
            }
        }
    }
}
