package com.example.fruitid;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    // Giữ tham chiếu fragment để tránh tạo lại mỗi lần chuyển tab
    private HomeFragment    homeFragment;
    private HistoryFragment historyFragment;
    private AccountFragment accountFragment;

    // Fragment đang được hiển thị
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupFragments(savedInstanceState);
        setupBottomNav();

        // Kiểm tra Intent phòng trường hợp Activity bị kill và tạo lại
        handleNavigationIntent(getIntent());
    }

    // BẮT TÍN HIỆU TỪ MÀN HÌNH KHÁC

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // Cập nhật intent mới nhất
        handleNavigationIntent(intent);
    }

    private void handleNavigationIntent(Intent intent) {
        if (intent != null) {
            String navigateTo = intent.getStringExtra("NAVIGATE_TO");
            if ("HOME_FRAGMENT".equals(navigateTo)) {
                // Điều hướng Bottom Navigation về lại tab Home
                if (bottomNavigationView != null) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_home);
                }
            }
        }
    }

    private void bindViews() {
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
    }

    private void setupFragments(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            // Lần đầu mở: tạo mới tất cả fragment
            homeFragment    = new HomeFragment();
            historyFragment = new HistoryFragment();
            accountFragment = new AccountFragment();

            // Thêm tất cả vào container, ẩn 2 tab không dùng
            fm.beginTransaction()
                    .add(R.id.fragmentContainer, accountFragment, "account").hide(accountFragment)
                    .add(R.id.fragmentContainer, historyFragment, "history").hide(historyFragment)
                    .add(R.id.fragmentContainer, homeFragment,    "home")
                    .commit();

            activeFragment = homeFragment;

        } else {
            // Khôi phục sau khi xoay màn hình / hệ thống kill app
            homeFragment    = (HomeFragment)    fm.findFragmentByTag("home");
            historyFragment = (HistoryFragment) fm.findFragmentByTag("history");
            accountFragment = (AccountFragment) fm.findFragmentByTag("account");

            // Xác định fragment đang active từ tab đang chọn
            int selectedId = bottomNavigationView.getSelectedItemId();
            if (selectedId == R.id.nav_history)      activeFragment = historyFragment;
            else if (selectedId == R.id.nav_account) activeFragment = accountFragment;
            else                                     activeFragment = homeFragment;
        }
    }

    private void setupBottomNav() {
        // Mặc định chọn Tab Trang chủ khi mở app
        if (bottomNavigationView.getSelectedItemId() == 0) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                switchFragment(homeFragment);
                return true;
            } else if (itemId == R.id.nav_history) {
                switchFragment(historyFragment);
                return true;
            } else if (itemId == R.id.nav_account) {
                switchFragment(accountFragment);
                return true;
            }
            return false;
        });
    }

    // ─── Chuyển Fragment bằng hide/show (giữ nguyên trạng thái scroll/data) ─

    private void switchFragment(Fragment target) {
        // Không làm gì nếu tab đang chọn đã là fragment hiện tại
        if (target == activeFragment) return;

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.hide(activeFragment);
        transaction.show(target);
        transaction.commit();

        activeFragment = target;
    }
}