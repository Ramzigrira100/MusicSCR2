package com.xraymirror.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 1001;
    private static final String ASSET_HOME_URL = "https://appassets.androidplatform.net/assets/index.html";

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private PermissionRequest pendingWebPermissionRequest;
    private boolean isFullscreen = false;
    private OnBackPressedCallback backPressedCallback;

    private static final class PendingFile {
        final File tempFile;
        final FileOutputStream out;
        final String fileName;
        final String mimeType;

        PendingFile(File tempFile, FileOutputStream out, String fileName, String mimeType) {
            this.tempFile = tempFile;
            this.out = out;
            this.fileName = fileName;
            this.mimeType = mimeType;
        }
    }

    private final Map<String, PendingFile> pendingFiles = new ConcurrentHashMap<>();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#090C13"));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setContentView(webView);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if ("appassets.androidplatform.net".equals(host)) {
                    return false;
                }
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String[] resources = request.getResources();
                        boolean needsCamera = false;
                        for (String r : resources) {
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                                needsCamera = true;
                                break;
                            }
                        }
                        if (!needsCamera) {
                            request.grant(resources);
                            return;
                        }
                        if (hasCameraPermission()) {
                            request.grant(resources);
                        } else {
                            if (pendingWebPermissionRequest != null) {
                                pendingWebPermissionRequest.deny();
                            }
                            pendingWebPermissionRequest = request;
                            requestAppPermissions();
                        }
                    }
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermissionRequest == request) {
                    pendingWebPermissionRequest = null;
                }
            }
        });

        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView == null) {
                    finish();
                    return;
                }
                webView.evaluateJavascript(
                        "(function(){if(document.body&&document.body.classList.contains('sheet')){document.body.classList.remove('sheet');return 'closed';}return 'exit';})()",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                if (value != null && value.contains("closed")) {
                                    return;
                                }
                                finish();
                            }
                        }
                );
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

        requestAppPermissions();
        webView.loadUrl(ASSET_HOME_URL);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAppPermissions() {
        List<String> needed = new ArrayList<>();
        if (!hasCameraPermission()) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            boolean cameraGranted = hasCameraPermission();
            if (pendingWebPermissionRequest != null) {
                PermissionRequest req = pendingWebPermissionRequest;
                pendingWebPermissionRequest = null;
                if (cameraGranted) {
                    req.grant(req.getResources());
                } else {
                    req.deny();
                }
            }
            if (!cameraGranted) {
                Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        for (PendingFile pf : pendingFiles.values()) {
            try {
                pf.out.close();
            } catch (IOException ignored) {
            }
            if (pf.tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                pf.tempFile.delete();
            }
        }
        pendingFiles.clear();

        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void applyFullscreen(boolean enable) {
        isFullscreen = enable;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller == null) {
            return;
        }
        if (enable) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    private final class AndroidBridge {

        @JavascriptInterface
        public boolean isAndroidApp() {
            return true;
        }

        @JavascriptInterface
        public void setKeepScreenOn(final boolean keepOn) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (keepOn) {
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } else {
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
            });
        }

        @JavascriptInterface
        public void toggleFullscreen() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    applyFullscreen(!isFullscreen);
                }
            });
        }

        @JavascriptInterface
        public void setFullscreen(final boolean enable) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    applyFullscreen(enable);
                }
            });
        }

        @JavascriptInterface
        public String saveStart(String fileName, String mimeType) {
            try {
                String safeName = (fileName == null || fileName.trim().isEmpty())
                        ? ("xray-" + System.currentTimeMillis() + ".png")
                        : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                String safeMime = (mimeType == null || mimeType.trim().isEmpty())
                        ? "image/png"
                        : mimeType.trim();

                File dir = new File(getCacheDir(), "shared");
                if (!dir.exists() && !dir.mkdirs()) {
                    return "";
                }
                File temp = new File(dir, safeName);
                if (temp.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                }
                FileOutputStream fos = new FileOutputStream(temp);
                String id = UUID.randomUUID().toString();
                pendingFiles.put(id, new PendingFile(temp, fos, safeName, safeMime));
                return id;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean saveChunk(String id, String base64Chunk) {
            if (id == null || base64Chunk == null) {
                return false;
            }
            PendingFile pf = pendingFiles.get(id);
            if (pf == null) {
                return false;
            }
            try {
                byte[] bytes = Base64.decode(base64Chunk, Base64.DEFAULT);
                pf.out.write(bytes);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean saveFinish(String id, final boolean openShare) {
            if (id == null) {
                return false;
            }
            final PendingFile pf = pendingFiles.remove(id);
            if (pf == null) {
                return false;
            }
            try {
                pf.out.flush();
                pf.out.close();

                boolean isVideo = pf.mimeType.startsWith("video/");
                Uri galleryUri = saveTempFileToGallery(pf.tempFile, pf.fileName, pf.mimeType, isVideo);

                if (galleryUri != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                    MainActivity.this,
                                    getString(R.string.saved_to_gallery, pf.fileName),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });
                }

                if (openShare) {
                    Uri shareUri = FileProvider.getUriForFile(
                            MainActivity.this,
                            getPackageName() + ".fileprovider",
                            pf.tempFile
                    );
                    final Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType(pf.mimeType);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_title)));
                        }
                    });
                } else if (galleryUri != null) {
                    //noinspection ResultOfMethodCallIgnored
                    pf.tempFile.delete();
                }

                return galleryUri != null;
            } catch (final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(
                                MainActivity.this,
                                getString(R.string.save_failed, e.getMessage()),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
                return false;
            }
        }
    }

    @Nullable
    private Uri saveTempFileToGallery(File srcFile, String fileName, String mimeType, boolean isVideo)
            throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    (isVideo ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES)
                            + "/X-Ray Mirror");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri collection = isVideo
                    ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Uri itemUri = resolver.insert(collection, values);
            if (itemUri == null) {
                throw new IOException("MediaStore insert returned null");
            }
            try (InputStream in = new FileInputStream(srcFile);
                 OutputStream out = resolver.openOutputStream(itemUri)) {
                if (out == null) {
                    throw new IOException("Cannot open MediaStore stream");
                }
                copyStream(in, out);
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(itemUri, values, null, null);
            return itemUri;
        } else {
            boolean hasWrite = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            File targetDir = hasWrite
                    ? new File(Environment.getExternalStoragePublicDirectory(
                            isVideo ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES),
                            "X-Ray Mirror")
                    : new File(getExternalFilesDir(
                            isVideo ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES),
                            "X-Ray Mirror");
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw new IOException("Cannot create destination folder");
            }
            File destFile = new File(targetDir, fileName);
            try (InputStream in = new FileInputStream(srcFile);
                 OutputStream out = new FileOutputStream(destFile)) {
                copyStream(in, out);
            }
            MediaScannerConnection.scanFile(
                    this,
                    new String[]{destFile.getAbsolutePath()},
                    new String[]{mimeType},
                    null
            );
            return Uri.fromFile(destFile);
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16384];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
        out.flush();
    }
}
