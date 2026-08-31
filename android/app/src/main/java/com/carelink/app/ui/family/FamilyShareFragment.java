package com.carelink.app.ui.family;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.carelink.app.R;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.repository.FamilyRepository;
import com.carelink.app.data.repository.LocationRepository;
import com.carelink.app.remote.FamilyRemoteViewerActivity;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class FamilyShareFragment extends Fragment implements AMapLocationListener {

    private static final int MAP_HOST_VIEW_ID = View.generateViewId();
    private static final long AUTO_REFRESH_MS = 20_000L;

    @Inject
    FamilyRepository familyRepository;
    @Inject
    LocationRepository locationRepository;

    private final List<Map<String, Object>> elderList = new ArrayList<>();
    private PreferenceManager preferenceManager;

    private TextView shareStatusView;
    private TextView shareDetailView;
    private TextView realtimeHintView;
    private Button realtimeToggleButton;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private AMapLocationClient locationClient;
    private AMapLocationClientOption locationOption;

    private String pendingTrigger;
    private String pendingShareStatus;
    private String pendingShareEndText;
    private Long pendingExpireAtMs;
    private boolean pendingFromAutoRefresh;
    private boolean autoRefreshInProgress;

    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || preferenceManager == null || !preferenceManager.isRealtimeLocationEnabled()) {
                return;
            }
            requestLocationOnlyRefresh("家属实时共享自动刷新", preferenceManager.getShareLastLocation());
            autoRefreshHandler.postDelayed(this, AUTO_REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        preferenceManager = new PreferenceManager(requireContext());
        setupPermissionLauncher();

        androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_page));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scrollView.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(createHeaderCard());
        root.addView(createShareControlCard());
        root.addView(createHostCard());
        root.addView(createRemoteAssistCard());
        refreshShareViews();
        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        attachMapFragment(savedInstanceState == null);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshShareViews();
        if (preferenceManager != null && preferenceManager.isRealtimeLocationEnabled()) {
            requestLocationOnlyRefresh("进入共享页自动刷新", preferenceManager.getShareLastLocation());
            startAutoRefresh();
        } else {
            stopAutoRefresh();
        }
    }

    @Override
    public void onPause() {
        stopAutoRefresh();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        stopAutoRefresh();
        if (locationClient != null) {
            try {
                locationClient.stopLocation();
                locationClient.onDestroy();
            } catch (Exception ignored) {
            }
            locationClient = null;
        }
        clearPendingRequest();
        super.onDestroyView();
    }

    private View createHeaderCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(dp(22));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_blue));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        card.setLayoutParams(params);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.addView(content);

        TextView title = new TextView(requireContext());
        title.setText("共享");
        title.setTextSize(23);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        content.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("家属端支持主动上传共享位置，与老人端共享同一份家庭地图数据。");
        subtitle.setTextSize(15);
        subtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.white_80));
        subtitle.setPadding(0, dp(10), 0, 0);
        content.addView(subtitle);
        return card;
    }

    private View createShareControlCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(dp(20));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_card));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        card.setLayoutParams(params);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(content);

        TextView title = new TextView(requireContext());
        title.setText("我的共享位置");
        title.setTextSize(17);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        content.addView(title);

        shareStatusView = new TextView(requireContext());
        shareStatusView.setTextSize(14);
        shareStatusView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        shareStatusView.setPadding(0, dp(8), 0, 0);
        content.addView(shareStatusView);

        shareDetailView = new TextView(requireContext());
        shareDetailView.setTextSize(14);
        shareDetailView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        shareDetailView.setPadding(0, dp(4), 0, 0);
        content.addView(shareDetailView);

        LinearLayout row1 = new LinearLayout(requireContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(12), 0, 0);
        row1.addView(createShareActionButton("更新当前位置", "手动更新位置", "单次共享", "本次共享已完成", null, false), flexLpWithRightGap());
        row1.addView(createShareActionButton("共享30分钟", "开启 30 分钟共享", "临时共享中", "剩余 30 分钟", 30L * 60L * 1000L, false), flexLp());
        content.addView(row1);

        LinearLayout row2 = new LinearLayout(requireContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        row2.addView(createShareActionButton("共享1小时", "开启 1 小时共享", "临时共享中", "剩余 1 小时", 60L * 60L * 1000L, false), flexLpWithRightGap());

        Button stopBtn = createPrimaryButton("停止共享", 0xFFE05C5C);
        stopBtn.setOnClickListener(v -> stopCloudSharing());
        row2.addView(stopBtn, flexLp());
        content.addView(row2);

        LinearLayout row3 = new LinearLayout(requireContext());
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(0, dp(8), 0, 0);
        realtimeToggleButton = createPrimaryButton("开启实时共享", ContextCompat.getColor(requireContext(), R.color.brand_blue));
        realtimeToggleButton.setOnClickListener(v -> toggleRealtimeSharing());
        row3.addView(realtimeToggleButton, flexLpWithRightGap());

        Button refreshBtn = createPrimaryButton("刷新家庭共享", 0xFF52C97A);
        refreshBtn.setOnClickListener(v -> requestFamilySnapshot(true));
        row3.addView(refreshBtn, flexLp());
        content.addView(row3);

        realtimeHintView = new TextView(requireContext());
        realtimeHintView.setTextSize(13);
        realtimeHintView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        realtimeHintView.setPadding(0, dp(10), 0, 0);
        content.addView(realtimeHintView);
        return card;
    }

    private Button createShareActionButton(String text,
                                           String trigger,
                                           String status,
                                           String endText,
                                           @Nullable Long expireAtMs,
                                           boolean fromAutoRefresh) {
        Button button = createPrimaryButton(text, ContextCompat.getColor(requireContext(), R.color.brand_blue));
        button.setOnClickListener(v -> requestSingleLocation(
                trigger,
                preferenceManager.getShareLastLocation(),
                status,
                endText,
                expireAtMs,
                fromAutoRefresh
        ));
        return button;
    }

    private View createHostCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(dp(20));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_card));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.addView(content);

        TextView hint = new TextView(requireContext());
        hint.setText("下方显示家庭共享地图与成员位置详情（含老人与家属）。");
        hint.setTextSize(14);
        hint.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        hint.setPadding(dp(6), dp(2), dp(6), dp(10));
        content.addView(hint);

        FrameLayout fragmentHost = new FrameLayout(requireContext());
        fragmentHost.setId(MAP_HOST_VIEW_ID);
        fragmentHost.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(fragmentHost);
        return card;
    }

    private void attachMapFragment(boolean firstCreate) {
        if (!isAdded()) {
            return;
        }
        Fragment existing = getChildFragmentManager().findFragmentById(MAP_HOST_VIEW_ID);
        if (existing != null) {
            return;
        }
        if (!firstCreate && getChildFragmentManager().isStateSaved()) {
            return;
        }
        getChildFragmentManager().beginTransaction()
                .replace(MAP_HOST_VIEW_ID, new FamilyMapFragment(), "family_share_map")
                .commitAllowingStateLoss();
    }

    private View createRemoteAssistCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(dp(20));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_card));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        card.setLayoutParams(params);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(16));
        card.addView(content);

        TextView title = new TextView(requireContext());
        title.setText("远程协助");
        title.setTextSize(17);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        content.addView(title);

        TextView hint = new TextView(requireContext());
        hint.setText("可先选择老人，再发起远程协助并查看其共享屏幕。");
        hint.setTextSize(14);
        hint.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        hint.setPadding(0, dp(6), 0, dp(12));
        content.addView(hint);

        Button btn = createPrimaryButton("发起远程协助", ContextCompat.getColor(requireContext(), R.color.brand_blue));
        btn.setOnClickListener(v -> startRemoteAssistWithPicker());
        content.addView(btn, fullLp());
        return card;
    }

    private void refreshShareViews() {
        if (shareStatusView == null || shareDetailView == null || realtimeHintView == null) {
            return;
        }
        String status = safeText(preferenceManager.getShareStatus(), "未共享");
        String location = safeText(preferenceManager.getShareLastLocation(), "暂无");
        String update = safeText(preferenceManager.getShareLastTime(), "暂无");
        String end = safeText(preferenceManager.getShareEndTime(), "未设置");
        String owner = safeText(preferenceManager.getSharedLocationOwner(), "FAMILY");

        shareStatusView.setText("状态：" + status);
        shareDetailView.setText("位置：" + location
                + "\n最近更新：" + update
                + "\n结束说明：" + end
                + "\n共享来源：" + owner);

        updateRealtimeStateCopy();
    }

    private void updateRealtimeStateCopy() {
        boolean realtimeEnabled = preferenceManager != null && preferenceManager.isRealtimeLocationEnabled();
        if (realtimeToggleButton != null) {
            realtimeToggleButton.setText(realtimeEnabled ? "关闭实时共享" : "开启实时共享");
        }
        if (realtimeHintView != null) {
            if (realtimeEnabled && autoRefreshInProgress) {
                realtimeHintView.setText("实时共享中：正在自动刷新位置并同步到家庭地图。");
            } else if (realtimeEnabled) {
                realtimeHintView.setText("实时共享已开启：每 20 秒自动刷新一次。");
            } else {
                realtimeHintView.setText("实时共享未开启：仅在手动点击按钮时上传位置。");
            }
        }
    }

    private void setupPermissionLauncher() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (fineGranted || coarseGranted) {
                        if (pendingShareStatus != null) {
                            requestSingleLocation(
                                    safeText(pendingTrigger, "定位权限已授予"),
                                    preferenceManager.getShareLastLocation(),
                                    pendingShareStatus,
                                    pendingShareEndText,
                                    pendingExpireAtMs,
                                    pendingFromAutoRefresh
                            );
                        }
                    } else {
                        Toast.makeText(requireContext(), "请先授予定位权限，再进行共享", Toast.LENGTH_SHORT).show();
                        finishRequest(pendingFromAutoRefresh);
                        clearPendingRequest();
                    }
                });
    }

    private void toggleRealtimeSharing() {
        boolean target = !preferenceManager.isRealtimeLocationEnabled();
        preferenceManager.setRealtimeLocationEnabled(target);
        if (target) {
            preferenceManager.saveShareStatus("实时共享中");
            preferenceManager.saveShareEndTime("持续共享，直到手动关闭");
            refreshShareViews();
            requestLocationOnlyRefresh("开启实时共享", preferenceManager.getShareLastLocation());
            startAutoRefresh();
            Toast.makeText(requireContext(), "已开启实时共享", Toast.LENGTH_SHORT).show();
            return;
        }

        stopAutoRefresh();
        locationRepository.toggleSharing(false, new LocationRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!isAdded()) {
                    return;
                }
                preferenceManager.saveShareStatus("已结束");
                preferenceManager.saveShareEndTime("已关闭实时共享");
                refreshShareViews();
                Toast.makeText(requireContext(), "已关闭实时共享", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), safeText(message, "关闭实时共享失败"), Toast.LENGTH_SHORT).show();
                refreshShareViews();
            }
        });
    }

    private void startAutoRefresh() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_MS);
        updateRealtimeStateCopy();
    }

    private void stopAutoRefresh() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        autoRefreshInProgress = false;
        updateRealtimeStateCopy();
    }

    private void requestLocationOnlyRefresh(String trigger, String fallbackLocation) {
        if (autoRefreshInProgress) {
            return;
        }
        String status = preferenceManager.isRealtimeLocationEnabled()
                ? "实时共享中"
                : safeText(preferenceManager.getShareStatus(), "单次共享");
        String endText = preferenceManager.isRealtimeLocationEnabled()
                ? "持续共享，直到手动关闭"
                : safeText(preferenceManager.getShareEndTime(), "未设置");
        requestSingleLocation(trigger, fallbackLocation, status, endText, null, true);
    }

    private void requestSingleLocation(String trigger, String fallbackLocation, String status,
                                       String endText, @Nullable Long expireAtMs,
                                       boolean fromAutoRefresh) {
        pendingTrigger = trigger;
        pendingShareStatus = status;
        pendingShareEndText = endText;
        pendingExpireAtMs = expireAtMs;
        pendingFromAutoRefresh = fromAutoRefresh;

        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fineGranted && !coarseGranted) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        if (fromAutoRefresh && autoRefreshInProgress) {
            return;
        }
        if (fromAutoRefresh) {
            autoRefreshInProgress = true;
            updateRealtimeStateCopy();
        }

        try {
            if (locationClient == null) {
                locationClient = new AMapLocationClient(requireContext());
                locationClient.setLocationListener(this);
            }
            if (locationOption == null) {
                locationOption = new AMapLocationClientOption();
                locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                locationOption.setOnceLocation(true);
                locationOption.setOnceLocationLatest(true);
                locationOption.setGpsFirst(true);
                locationOption.setNeedAddress(true);
                locationOption.setMockEnable(false);
                locationOption.setWifiScan(true);
                locationOption.setLocationCacheEnable(false);
                locationOption.setHttpTimeOut(12000);
            }
            locationClient.setLocationOption(locationOption);
            locationClient.startLocation();
            if (realtimeHintView != null) {
                realtimeHintView.setText(trigger + "：正在获取当前位置...");
            }
        } catch (Exception e) {
            String now = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
            double lat = preferenceManager.getShareLatitude();
            double lng = preferenceManager.getShareLongitude();
            persistShareResult(
                    safeText(status, "单次共享"),
                    safeText(fallbackLocation, "当前位置"),
                    safeText(endText, "本次共享已完成"),
                    lat,
                    lng,
                    now,
                    expireAtMs,
                    !"已结束".equals(status),
                    fromAutoRefresh
            );
        }
    }

    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation == null) {
            finishRequest(pendingFromAutoRefresh);
            clearPendingRequest();
            return;
        }
        if (aMapLocation.getErrorCode() != 0) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "定位失败，请稍后重试", Toast.LENGTH_SHORT).show();
            }
            finishRequest(pendingFromAutoRefresh);
            clearPendingRequest();
            return;
        }

        String address = firstNonEmpty(aMapLocation.getAddress(), aMapLocation.getPoiName());
        if (address.isEmpty()) {
            address = String.format(Locale.CHINA, "%.5f, %.5f",
                    aMapLocation.getLatitude(), aMapLocation.getLongitude());
        }
        String time = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());

        String status = safeText(pendingShareStatus, "单次共享");
        String endText = safeText(pendingShareEndText, "本次共享已完成");
        Long expire = pendingExpireAtMs;
        boolean fromAuto = pendingFromAutoRefresh;
        boolean enabled = !"已结束".equals(status);

        persistShareResult(
                status,
                address,
                endText,
                aMapLocation.getLatitude(),
                aMapLocation.getLongitude(),
                time,
                expire,
                enabled,
                fromAuto
        );
        clearPendingRequest();
    }

    private void persistShareResult(String status, String locationLabel, String endText,
                                    double lat, double lng, String time,
                                    @Nullable Long expireAtMs, boolean enabled, boolean fromAutoRefresh) {
        String normalizedLocation = safeText(locationLabel, "当前位置");
        preferenceManager.saveShareStatus(safeText(status, "单次共享"));
        preferenceManager.saveShareLastLocation(normalizedLocation);
        preferenceManager.saveShareLastTime(time);
        preferenceManager.saveShareEndTime(safeText(endText, "未设置"));
        preferenceManager.saveShareLatitude(lat);
        preferenceManager.saveShareLongitude(lng);
        preferenceManager.saveSharedLocationOwner("FAMILY");
        preferenceManager.saveSharedSessionId("CLOUD-LOCATION-SESSION");
        preferenceManager.setSharedLocationVisibleToBoth(enabled);

        locationRepository.uploadMemberLocation(lat, lng, normalizedLocation, enabled, expireAtMs,
                new LocationRepository.ResultCallback<Map<String, Object>>() {
                    @Override
                    public void onSuccess(Map<String, Object> data) {
                        if (!isAdded()) {
                            finishRequest(fromAutoRefresh);
                            return;
                        }
                        refreshShareViews();
                        if (!fromAutoRefresh) {
                            Toast.makeText(requireContext(), "位置已同步到家庭共享地图", Toast.LENGTH_SHORT).show();
                        }
                        finishRequest(fromAutoRefresh);
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) {
                            finishRequest(fromAutoRefresh);
                            return;
                        }
                        refreshShareViews();
                        if (!fromAutoRefresh) {
                            Toast.makeText(requireContext(), safeText(message, "位置同步失败"), Toast.LENGTH_SHORT).show();
                        }
                        finishRequest(fromAutoRefresh);
                    }
                });
    }

    private void stopCloudSharing() {
        preferenceManager.setRealtimeLocationEnabled(false);
        stopAutoRefresh();
        locationRepository.toggleSharing(false, new LocationRepository.ResultCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                if (!isAdded()) {
                    return;
                }
                preferenceManager.saveShareStatus("已结束");
                preferenceManager.saveShareEndTime("已手动结束");
                preferenceManager.saveSharedLocationOwner("FAMILY");
                refreshShareViews();
                Toast.makeText(requireContext(), "已停止共享并同步到云端", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), safeText(message, "停止共享失败"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestFamilySnapshot(boolean showToast) {
        locationRepository.fetchFamilyLatestLocations(new LocationRepository.ResultCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (!isAdded()) {
                    return;
                }
                refreshShareViews();
                if (showToast) {
                    Toast.makeText(requireContext(), "家庭共享信息已刷新", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                if (showToast) {
                    Toast.makeText(requireContext(), safeText(message, "刷新失败"), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void finishRequest(boolean fromAutoRefresh) {
        if (fromAutoRefresh) {
            autoRefreshInProgress = false;
        }
        updateRealtimeStateCopy();
    }

    private void clearPendingRequest() {
        pendingTrigger = null;
        pendingShareStatus = null;
        pendingShareEndText = null;
        pendingExpireAtMs = null;
        pendingFromAutoRefresh = false;
    }

    private void startRemoteAssistWithPicker() {
        if (!isAdded()) {
            return;
        }
        familyRepository.getElders(new FamilyRepository.ResultCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                elderList.clear();
                if (data != null) {
                    elderList.addAll(data);
                }
                if (elderList.isEmpty()) {
                    startRemoteAssist("");
                    return;
                }
                CharSequence[] names = new CharSequence[elderList.size()];
                for (int i = 0; i < elderList.size(); i++) {
                    names[i] = getElderName(elderList.get(i));
                }
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("选择要协助的老人")
                        .setItems(names, (dialog, which) -> {
                            String elderId = String.valueOf(getLong(elderList.get(which).get("userId")));
                            startRemoteAssist(elderId);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }

            @Override
            public void onError(String message) {
                startRemoteAssist("");
            }
        });
    }

    private void startRemoteAssist(String elderId) {
        if (getContext() == null) {
            return;
        }
        Intent intent = new Intent(getContext(), FamilyRemoteViewerActivity.class);
        if (elderId != null && !elderId.trim().isEmpty() && !"0".equals(elderId)) {
            intent.putExtra("elder_id", elderId.trim());
        }
        startActivity(intent);
    }

    private Button createPrimaryButton(String text, int color) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        button.setBackgroundColor(color);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams fullLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams flexLp() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);
    }

    private LinearLayout.LayoutParams flexLpWithRightGap() {
        LinearLayout.LayoutParams params = flexLp();
        params.rightMargin = dp(8);
        return params;
    }

    private String getElderName(Map<String, Object> elder) {
        if (elder == null) {
            return "老人";
        }
        Object nickname = elder.get("nickname");
        String name = nickname == null ? "" : String.valueOf(nickname).trim();
        if (name.isEmpty()) {
            long id = getLong(elder.get("userId"));
            return id > 0 ? ("老人(" + id + ")") : "老人";
        }
        return name;
    }

    private long getLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        if (v == null) {
            return -1L;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return "";
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int dp(int value) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}
