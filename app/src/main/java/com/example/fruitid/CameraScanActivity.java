package com.example.fruitid;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class CameraScanActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private OverlayView overlayView;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;

    private FruitClassifier fruitClassifier;

    // Variables for capturing the latest result
    private Bitmap latestBitmap = null;
    private List<FruitClassifier.BoxResult> latestResults = null;
    private boolean isCapturing = false;

    // Khởi tạo trình chọn ảnh từ thư viện
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processImageFromGallery(uri);
                }
            }
    );

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Bạn cần cấp quyền Camera để sử dụng tính năng này", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camerascan);

        viewFinder = findViewById(R.id.viewFinder);
        overlayView = findViewById(R.id.overlayView);
        cameraExecutor = Executors.newSingleThreadExecutor();

        setupButtons();

        // Kiểm tra và yêu cầu quyền Camera
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA);
        }

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(CameraScanActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
        });

        // Nạp model AI
        try {
            fruitClassifier = new FruitClassifier(this);
        } catch (Exception e) {
            Log.e("AI_Error", "Lỗi nạp não AI: ", e);
            Toast.makeText(this, "Lỗi tải mô hình AI!", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupButtons() {
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            Intent intent = new Intent(CameraScanActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnGallery).setOnClickListener(v -> {
            galleryLauncher.launch("image/*");
        });

        findViewById(R.id.btnCapture).setOnClickListener(v -> {
            if (!isCapturing && latestBitmap != null) {
                isCapturing = true;
                Toast.makeText(this, "Đang xử lý và lưu kết quả...", Toast.LENGTH_SHORT).show();
                // We run the capture flow asynchronously
                final Bitmap captureBitmap = latestBitmap;
                final List<FruitClassifier.BoxResult> captureResults = latestResults;
                new Thread(() -> {
                    analyzeFruit(captureBitmap, null, captureResults);
                }).start();
            } else {
                Toast.makeText(this, "Chưa có khung hình hoặc đang lưu!", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnFlipCamera).setOnClickListener(v -> {
            lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK) ?
                    CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            startCamera();
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImageProxy);

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Lỗi khởi tạo camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImageProxy(ImageProxy imageProxy) {
        if (fruitClassifier == null || isCapturing) {
            imageProxy.close();
            return;
        }

        try {
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap != null) {
                // Handle rotation
                Matrix matrix = new Matrix();
                matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                List<FruitClassifier.BoxResult> results = fruitClassifier.recognizeImage(rotatedBitmap);

                this.latestBitmap = rotatedBitmap;
                this.latestResults = results;

                // Update UI on main thread
                final List<FruitClassifier.BoxResult> finalResults = results;
                runOnUiThread(() -> {
                    overlayView.setResults(finalResults);
                });
            }
        } catch (Exception e) {
            Log.e("CameraX", "Lỗi phân tích khung hình", e);
        } finally {
            imageProxy.close();
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        java.nio.ByteBuffer yBuffer = planes[0].getBuffer();
        java.nio.ByteBuffer uBuffer = planes[1].getBuffer();
        java.nio.ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private void processImageFromGallery(Uri imageUri) {
        try {
            InputStream imageStream = getContentResolver().openInputStream(imageUri);
            Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);

            // Directly analyze the single static image
            if (fruitClassifier != null) {
                List<FruitClassifier.BoxResult> results = fruitClassifier.recognizeImage(selectedImage);
                analyzeFruit(selectedImage, null, results);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Không thể tải ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private String translateFruitName(String rawName) {
        String name = rawName.toLowerCase().trim();
        if (name.contains("apple")) return "Quả Táo";
        if (name.contains("banana")) return "Quả Chuối";
        if (name.contains("orange")) return "Quả Cam";
        if (name.contains("watermelon")) return "Dưa Hấu";
        if (name.contains("mango")) return "Quả Xoài";
        if (name.contains("pineapple")) return "Quả Dứa";
        if (name.contains("strawberry")) return "Quả Dâu";
        if (name.contains("cucumber")) return "Quả Dưa Chuột";
        if (name.contains("grapes")) return "Quả Nho";
        if (name.contains("kiwi")) return "Quả Kiwi";
        if (name.contains("pear")) return "Quả Lê";
        if (name.contains("pomegranate")) return "Quả Lựu";
        return rawName;
    }

    private Bitmap drawBoxesOnBitmap(Bitmap originalBitmap, List<FruitClassifier.BoxResult> results) {
        if (originalBitmap == null || results == null || results.isEmpty()) return originalBitmap;

        Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        android.graphics.Canvas canvas = new android.graphics.Canvas(mutableBitmap);

        android.graphics.Paint boxPaint = new android.graphics.Paint();
        boxPaint.setColor(android.graphics.Color.GREEN);
        boxPaint.setStrokeWidth(8F);
        boxPaint.setStyle(android.graphics.Paint.Style.STROKE);

        android.graphics.Paint textBackgroundPaint = new android.graphics.Paint();
        textBackgroundPaint.setColor(android.graphics.Color.BLACK);
        textBackgroundPaint.setStyle(android.graphics.Paint.Style.FILL);
        textBackgroundPaint.setAlpha(160);

        android.graphics.Paint textPaint = new android.graphics.Paint();
        textPaint.setColor(android.graphics.Color.WHITE);
        textPaint.setStyle(android.graphics.Paint.Style.FILL);
        textPaint.setTextSize(Math.max(30f, originalBitmap.getWidth() / 20f));

        for (FruitClassifier.BoxResult result : results) {
            android.graphics.RectF boundingBox = result.boundingBox;
            float left = boundingBox.left * mutableBitmap.getWidth();
            float top = boundingBox.top * mutableBitmap.getHeight();
            float right = boundingBox.right * mutableBitmap.getWidth();
            float bottom = boundingBox.bottom * mutableBitmap.getHeight();

            android.graphics.RectF drawableRect = new android.graphics.RectF(left, top, right, bottom);
            canvas.drawRect(drawableRect, boxPaint);

            String translatedName = translateFruitName(result.fruitName);
            String drawableText = translatedName + " " + String.format("%.1f%%", result.accuracy);

            android.graphics.Rect textBounds = new android.graphics.Rect();
            textPaint.getTextBounds(drawableText, 0, drawableText.length(), textBounds);

            float textWidth = textBounds.width();
            float textHeight = textBounds.height();

            float bgTop;
            float bgBottom;
            float textY;

            if (top < textHeight + 20) {
                bgTop = top;
                bgBottom = top + textHeight + 16;
                textY = top + textHeight + 8;
            } else {
                bgTop = top - textHeight - 16;
                bgBottom = top;
                textY = top - 8;
            }

            canvas.drawRect(left, bgTop, left + textWidth + 16, bgBottom, textBackgroundPaint);
            canvas.drawText(drawableText, left + 8, textY, textPaint);
        }

        return mutableBitmap;
    }

    // Logic lưu kết quả
    private void analyzeFruit(Bitmap bitmap, String existingImagePath, List<FruitClassifier.BoxResult> aiResults) {
        if (bitmap == null) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Lỗi: Ảnh không hợp lệ!", Toast.LENGTH_SHORT).show();
                isCapturing = false;
            });
            return;
        }

        String resultFruitName = "Không xác định";
        Bitmap processedBitmap = bitmap;

        if (aiResults != null && !aiResults.isEmpty()) {
            java.util.Map<String, List<Float>> groupedResults = new java.util.HashMap<>();
            
            for (int i = 0; i < aiResults.size(); i++) {
                FruitClassifier.BoxResult res = aiResults.get(i);
                String translated = translateFruitName(res.fruitName);
                if (!groupedResults.containsKey(translated)) {
                    groupedResults.put(translated, new java.util.ArrayList<>());
                }
                groupedResults.get(translated).add(res.accuracy);
            }

            StringBuilder namesBuilder = new StringBuilder();
            for (java.util.Map.Entry<String, List<Float>> entry : groupedResults.entrySet()) {
                String name = entry.getKey();
                List<Float> accuracies = entry.getValue();
                float sum = 0f;
                for (float acc : accuracies) {
                    sum += acc;
                }
                float avgAccuracy = sum / accuracies.size();
                
                if (namesBuilder.length() > 0) {
                    namesBuilder.append("\n");
                }
                namesBuilder.append(String.format("%s: %.1f%%", name, avgAccuracy));
            }

            resultFruitName = namesBuilder.length() > 0 ? namesBuilder.toString() : "Không xác định";
            
            // Draw boxes
            processedBitmap = drawBoxesOnBitmap(bitmap, aiResults);
        }

        String finalImagePath = saveBitmapToCache(processedBitmap);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            saveScanToCloud(auth.getCurrentUser().getUid(), resultFruitName, finalImagePath);
        } else {
            runOnUiThread(() -> {
                Toast.makeText(this, "Bạn cần đăng nhập để lưu lịch sử quét", Toast.LENGTH_SHORT).show();
            });
        }

        final String finalResultFruitName = resultFruitName;
        final String finalResultImagePath = finalImagePath;

        runOnUiThread(() -> {
            Intent intent = new Intent(CameraScanActivity.this, ResultActivity.class);
            intent.putExtra("EXTRA_FRUIT_NAME", finalResultFruitName);
            intent.putExtra("EXTRA_IMAGE_PATH", finalResultImagePath);
            startActivity(intent);
            isCapturing = false;
        });
    }

    private void saveScanToCloud(String uid, String fruitName, String localImagePath) {
        if (localImagePath == null || localImagePath.isEmpty()) {
            saveFirestoreData(uid, fruitName, "");
            return;
        }

        java.io.File imageFile = new java.io.File(localImagePath);
        if (!imageFile.exists()) {
            runOnUiThread(() -> Toast.makeText(this, "Lỗi: Không tìm thấy ảnh!", Toast.LENGTH_SHORT).show());
            return;
        }

        try {
            // 1. Đọc ảnh từ thư mục cache của điện thoại
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());

            // 2. Nén ảnh lại (chất lượng 30%) để đảm bảo không vượt quá giới hạn 1MB của Firestore
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, baos);
            byte[] imageBytes = baos.toByteArray();

            // 3. Chuyển đổi dữ liệu ảnh thành chuỗi ký tự Base64
            String base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT);

            // 4. Gọi hàm lưu Firestore hiện tại của bạn, truyền chuỗi Base64 vào vị trí của link URL
            saveFirestoreData(uid, fruitName, base64Image);

        } catch (Exception e) {
            Log.e("Firebase", "Lỗi mã hóa ảnh", e);
            runOnUiThread(() -> Toast.makeText(this, "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void saveFirestoreData(String uid, String fruitName, String finalImageUrl) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference docRef = db.collection("Users").document(uid).collection("History").document();

        Map<String, Object> scanData = new HashMap<>();
        scanData.put("fruitName", fruitName);
        scanData.put("scanDate", new java.util.Date());
        scanData.put("imagePath", finalImageUrl);

        docRef.set(scanData)
                .addOnSuccessListener(aVoid -> Log.d("Firebase", "Lưu lịch sử thành công: " + docRef.getId()))
                .addOnFailureListener(e -> {
                    Log.e("Firebase", "Lỗi lưu lịch sử", e);
                    runOnUiThread(() -> Toast.makeText(this, "Không thể lưu lịch sử: " + e.getMessage(), Toast.LENGTH_LONG).show());
                });
    }

    private String saveBitmapToCache(Bitmap bitmap) {
        try {
            if (bitmap == null) return null;
            String uniqueFileName = "gallery_" + System.currentTimeMillis() + ".jpg";
            java.io.File file = new java.io.File(getCacheDir(), uniqueFileName);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);

            fos.flush();
            fos.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e("Cache", "Lỗi khi lưu cache: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (fruitClassifier != null) {
            fruitClassifier.close();
        }
    }
}