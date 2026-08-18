package com.example.fruitid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class WelcomeActivity extends AppCompatActivity {

    public static final String PREFS_NAME   = "fruitid_prefs";
    public static final String KEY_NAME     = "user_name";
    public static final String KEY_GOALS    = "user_goals";
    public static final String KEY_WELCOMED = "has_welcomed";

    private EditText       etName;
    private ChipGroup      chipGroupGoal;
    private Chip           chipClean, chipCalorie, chipNutrition, chipShopping;
    private MaterialButton btnStart;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Nếu đã từng mở app → bỏ qua màn này, vào thẳng MainActivity
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_WELCOMED, false)) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_welcome);
        bindViews();
        setupListeners();
    }

    private void bindViews() {
        etName        = findViewById(R.id.etName);
        chipGroupGoal = findViewById(R.id.chipGroupGoal);
        chipClean     = findViewById(R.id.chipClean);
        chipCalorie   = findViewById(R.id.chipCalorie);
        chipNutrition = findViewById(R.id.chipNutrition);
        chipShopping  = findViewById(R.id.chipShopping);
        btnStart      = findViewById(R.id.btnStart);
    }


    private void setupListeners() {

        // Nút disabled cho đến khi người dùng nhập tên
        btnStart.setEnabled(false);

        // Bật / tắt nút theo nội dung ô nhập
        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnStart.setEnabled(s != null && s.toString().trim().length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        // Xử lý nút Bắt đầu ngay
        btnStart.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";

            if (name.isEmpty()) {
                etName.setError("Vui lòng nhập tên của bạn");
                etName.requestFocus();
                return;
            }
            etName.setError(null);

            // Thu thập mục tiêu đã chọn
            String goals = collectGoals();

            // LƯU VÀO SHAREDPREFERENCES
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_NAME, name)
                    .putString(KEY_GOALS, goals)
                    .putBoolean(KEY_WELCOMED, true)
                    .apply();

            // ĐẨY LÊN FIRESTORE
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) {
                String uid = auth.getCurrentUser().getUid();
                FirebaseFirestore db = FirebaseFirestore.getInstance();

                // Tạo một bản ghi chứa Tên và Mục tiêu
                Map<String, Object> profileData = new HashMap<>();
                profileData.put("name", name);
                profileData.put("goals", goals);

                db.collection("Users").document(uid)
                        .set(profileData)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Xin chào, " + name, Toast.LENGTH_SHORT).show();
                            goToMain();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Lỗi lưu hồ sơ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            goToMain();
                        });
            } else {
                goToMain();
            }
        });
    }
    // Helper: thu thập mục tiêu đã chọn

    private String collectGoals() {
        List<String> selected = new ArrayList<>();
        if (chipClean.isChecked())     selected.add("clean");
        if (chipCalorie.isChecked())   selected.add("calorie");
        if (chipNutrition.isChecked()) selected.add("nutrition");
        if (chipShopping.isChecked())  selected.add("shopping");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            sb.append(selected.get(i));
            if (i < selected.size() - 1) sb.append(",");
        }
        return sb.toString();
    }

    //Chuyển màn hình

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish(); // Không cho quay lại màn này bằng nút Back
    }
}