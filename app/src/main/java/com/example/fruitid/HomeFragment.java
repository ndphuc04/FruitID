package com.example.fruitid;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomeFragment extends Fragment {

    private TextView tvGreeting;
    private TextView tvAvatar;
    private Button btnScan;

    private TextView tvTip1Text;
    private TextView tvTip2Text;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupScanButton();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Cập nhật lại thông tin mỗi khi HomeFragment hiển thị lại trên màn hình
        loadUserInfo();
        setupNutritionTips(); // Tải mẹo cá nhân hóa
    }

    // TỰ ĐỘNG CẬP NHẬT KHI CHUYỂN TAB
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadUserInfo();
            setupNutritionTips();
        }
    }

    private void bindViews(View root) {
        tvGreeting = root.findViewById(R.id.tvGreeting);
        tvAvatar   = root.findViewById(R.id.tvAvatar);
        btnScan    = root.findViewById(R.id.btnScan);

        tvTip1Text = root.findViewById(R.id.tvTip1Text);
        tvTip2Text = root.findViewById(R.id.tvTip2Text);
    }

    // Tải tên người dùng từ SharedPreferences
    private void loadUserInfo() {
        if (getContext() == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences(
                WelcomeActivity.PREFS_NAME, Context.MODE_PRIVATE);

        String name = prefs.getString(WelcomeActivity.KEY_NAME, "người dùng");

        tvGreeting.setText("Xin chào, " + name);

        // Avatar: lấy chữ cái đầu của tên
        String initial = name.trim().isEmpty()
                ? "?"
                : String.valueOf(name.trim().charAt(0)).toUpperCase();
        tvAvatar.setText(initial);
    }

    // Tải mẹo dựa trên chuỗi mục tiêu
    private void setupNutritionTips() {
        if (getContext() == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences(
                WelcomeActivity.PREFS_NAME, Context.MODE_PRIVATE);

        // Lấy chuỗi mục tiêu từ WelcomeActivity
        String goals = prefs.getString(WelcomeActivity.KEY_GOALS, "");
        List<String> activeTips = new ArrayList<>();

        if (goals.contains("clean")) {
            activeTips.add("Làm sạch an toàn: Ngâm trái cây với nước muối loãng hoặc baking soda trong 15 phút giúp làm sạch dư lượng thuốc trừ sâu.");
            activeTips.add("Mẹo ăn sạch: Hãy ưu tiên chọn hoa quả đúng mùa vụ để hạn chế tối đa thuốc kích thích và chất bảo quản.");
        }

        if (goals.contains("calorie")) {
            activeTips.add("Quản lý vóc dáng: Táo và Bưởi là 'chân ái' giảm cân vì chứa cực ít calo nhưng lại giàu chất xơ giúp bạn no lâu.");
            activeTips.add("Mẹo giữ dáng: Nên ăn trái cây nguyên quả/nguyên múi thay vì ép lấy nước để không bỏ lỡ lượng chất xơ quý giá.");
        }

        if (goals.contains("nutrition")) {
            activeTips.add("Tăng đề kháng: Ổi và Cam chứa lượng Vitamin C khổng lồ, là 'vũ khí' tuyệt vời để bảo vệ sức khỏe và làm sáng da.");
            activeTips.add("Nạp năng lượng: Chuối giàu Kali và Tryptophan, giúp phục hồi cơ bắp và giảm stress cực kỳ hiệu quả.");
        }

        if (goals.contains("shopping")) {
            activeTips.add("Đi chợ thông minh: Chọn dưa hấu gõ nghe 'bộp bộp' vang, cuống nhỏ héo khô chứng tỏ dưa mỏng vỏ và rất ngọt.");
            activeTips.add("Mẹo chọn quả: Trái cây tươi ngon thường cầm nặng chắc tay, phần cuống tươi xanh và có mùi thơm tự nhiên đặc trưng.");
        }

        if (activeTips.isEmpty()) {
            activeTips.add("Ăn 1-2 khẩu phần trái cây đa dạng màu sắc mỗi ngày giúp cung cấp đủ lượng khoáng chất thiết yếu cho cơ thể.");
            activeTips.add("Nên ăn trái cây trước bữa ăn chính khoảng 30 phút để hệ tiêu hóa hấp thụ vitamin tốt nhất.");
        } else {
            // Đảo vị trí ngẫu nhiên tất cả các mẹo có trong danh sách
            Collections.shuffle(activeTips);
        }

        // Luôn lấy 2 mẹo nằm trên cùng sau khi đã xáo trộn
        String tip1 = activeTips.size() > 0 ? activeTips.get(0) : "";
        String tip2 = activeTips.size() > 1 ? activeTips.get(1) : activeTips.get(0);

        tvTip1Text.setText(tip1);
        tvTip2Text.setText(tip2);
    }

    // Nút quét
    private void setupScanButton() {
        btnScan.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CameraScanActivity.class);
            startActivity(intent);
        });
    }
}