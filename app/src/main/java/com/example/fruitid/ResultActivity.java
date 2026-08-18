package com.example.fruitid;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;

public class ResultActivity extends AppCompatActivity {

    private ShapeableImageView imgResult;
    private TextView tvName;
    private MaterialButton btnHome, btnScanAgain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        imgResult = findViewById(R.id.imgResult);
        tvName = findViewById(R.id.tvName);
        btnHome = findViewById(R.id.btnHome);
        btnScanAgain = findViewById(R.id.btnScanAgain);

        Intent intent = getIntent();
        String fruitName = intent.getStringExtra("EXTRA_FRUIT_NAME");
        String imagePath = intent.getStringExtra("EXTRA_IMAGE_PATH");

        tvName.setText(fruitName != null ? fruitName : "Không xác định");

        // Xử lý hiển thị ảnh từ file tạm
        if (imagePath != null) {
            File imgFile = new File(imagePath);
            if (imgFile.exists()) {
                // Giải mã file ảnh thành Bitmap và đưa lên màn hình
                Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                imgResult.setImageBitmap(myBitmap);
            } else {
                Toast.makeText(this, "Không tìm thấy file ảnh", Toast.LENGTH_SHORT).show();
            }
        }

        setupListeners();

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void setupListeners() {
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnScanAgain.setOnClickListener(v -> {
            finish();
        });
    }
}