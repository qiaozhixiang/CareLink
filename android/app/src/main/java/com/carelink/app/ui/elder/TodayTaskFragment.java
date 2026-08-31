package com.carelink.app.ui.elder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.MyLocationStyle;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.repository.LocationRepository;
import com.carelink.app.utils.FontScaleHelper;
import com.carelink.app.utils.MapMemberVisualizer;
import com.carelink.app.utils.ShareMapDataHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TodayTaskFragment extends Fragment implements AMapLocationListener {

    private static final String TAG = "TodayTaskFragment";
    private static final String MAP_VIEW_STATE_KEY = "today_task_map_view_state";
    private static final long AUTO_REFRESH_MS = 20_000L;
    private static final LatLng DEFAULT_SHARE_POINT = new LatLng(31.2304, 121.4737);
    private static final String AMAP_REVIEW_NO = "审图号：GS (2023)551号 | GS (2023)2175号";

    @Inject
    LocationRepository locationRepository;

    private PreferenceManager preferenceManager;
    private TextView statusTag;
    private TextView infoView;
    private TextView realtimeHintView;
    private TextView mapHintView;
    private TextView mapMetaView;
    private TextView memberListView;
    private TextView refreshStateView;
    private Button realtimeToggleButton;

    private MapView mapView;
    private Bundle mapViewState;
    private AMap aMap;
    private boolean mapReady;
    private LatLng lastCameraPoint;
    private float lastAccuracyMeters = -1f;

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
            if (!isAdded()) {
                return;
            }
            if (preferenceManager == null || !preferenceManager.isRealtimeLocationEnabled()) {
                return;
            }
            requestLocationOnlyRefresh("实时共享自动刷新", preferenceManager.getShareLastLocation());
            requestFamilyMembers(false);
            autoRefreshHandler.postDelayed(this, AUTO_REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        preferenceManager = new PreferenceManager(requireContext());
        mapViewState = savedInstanceState != null ? savedInstanceState.getBundle(MAP_VIEW_STATE_KEY) : null;
        setupPermissionLauncher();

        int titleSize = FontScaleHelper.title(requireContext());
        int bodySize = FontScaleHelper.body(requireContext());

        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(0xFFF4F7FB);
        scrollView.addView(root);

        root.addView(createHeroCard(titleSize, bodySize));
        root.addView(createActionCard(bodySize));
        root.addView(createMapCard(bodySize));
        root.addView(createMemberCard(bodySize));

        refreshShareViews();
        requestFamilyMembers(false);
        return scrollView;
    }

    private View createHeroCard(int titleSize, int bodySize) {
        LinearLayout hero = createCard(0xFF2F80ED, 32, 30);

        TextView eyebrow = new TextView(requireContext());
        eyebrow.setText("家庭守护 · 云端共享中心");
        eyebrow.setTextSize(FontScaleHelper.secondary(requireContext()));
        eyebrow.setTextColor(0xFFDCEBFF);
        hero.addView(eyebrow);

        TextView title = new TextView(requireContext());
        title.setText("位置共享");
        title.setTextSize(titleSize + 2);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 10, 0, 10);
        hero.addView(title);

        TextView desc = new TextView(requireContext());
        desc.setText("老人端可主动上传位置，家属端可查看家庭成员共享位置。开启实时共享后将每 20 秒自动刷新一次。");
        desc.setTextSize(bodySize);
        desc.setTextColor(0xFFEAF4FF);
        hero.addView(desc);

        realtimeHintView = new TextView(requireContext());
        realtimeHintView.setTextSize(FontScaleHelper.secondary(requireContext()));
        realtimeHintView.setTextColor(0xFFEAF4FF);
        realtimeHintView.setPadding(0, 14, 0, 0);
        hero.addView(realtimeHintView);
        return hero;
    }

    private View createActionCard(int bodySize) {
        LinearLayout section = createCard(0xFFFFFFFF, 28, 24);

        TextView title = new TextView(requireContext());
        title.setText("共享操作");
        title.setTextSize(bodySize + 2);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, 12);
        section.addView(title);

        LinearLayout row1 = new LinearLayout(requireContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(createActionButton("更新当前位置", v ->
                requestSingleLocation("手动更新位置", preferenceManager.getShareLastLocation(),
                        "单次共享", "本次共享已完成", null, false)));
        row1.addView(createActionButton("共享30分钟", v ->
                requestSingleLocation("开启 30 分钟共享", preferenceManager.getShareLastLocation(),
                        "临时共享中", "剩余 30 分钟", 30L * 60L * 1000L, false)));
        section.addView(row1);

        LinearLayout row2 = new LinearLayout(requireContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(createActionButton("共享1小时", v ->
                requestSingleLocation("开启 1 小时共享", preferenceManager.getShareLastLocation(),
                        "临时共享中", "剩余 1 小时", 60L * 60L * 1000L, false)));
        row2.addView(createActionButton("停止共享", v -> stopCloudSharing()));
        section.addView(row2);

        LinearLayout row3 = new LinearLayout(requireContext());
        row3.setOrientation(LinearLayout.HORIZONTAL);

        realtimeToggleButton = createActionButton("开启实时共享", v -> toggleRealtimeSharing());
        row3.addView(realtimeToggleButton);
        row3.addView(createActionButton("刷新家庭成员", v -> requestFamilyMembers(true)));
        section.addView(row3);

        refreshStateView = new TextView(requireContext());
        refreshStateView.setTextSize(FontScaleHelper.secondary(requireContext()));
        refreshStateView.setTextColor(0xFF4F6477);
        refreshStateView.setPadding(8, 8, 8, 0);
        section.addView(refreshStateView);
        return section;
    }

    private Button createActionButton(String text, View.OnClickListener listener) {
        int baseFont = FontScaleHelper.body(requireContext());
        Button button = new Button(requireContext());
        button.setText(text);
        button.setTextSize(baseFont);
        button.setAllCaps(false);
        button.setBackground(createRoundedBg(0xFFF7FAFD, 22));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(8, 0, 8, 12);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        return button;
    }

    private View createMapCard(int bodySize) {
        LinearLayout card = createCard(0xFFD9EAFE, 28, 22);

        mapHintView = new TextView(requireContext());
        mapHintView.setTextSize(bodySize - 1);
        mapHintView.setText("地图加载中...");
        mapHintView.setPadding(10, 8, 10, 6);
        mapHintView.setTextColor(0xFF35506B);
        mapHintView.setBackground(createRoundedBg(0xEEF7FBFF, 14));
        card.addView(mapHintView);

        mapMetaView = new TextView(requireContext());
        mapMetaView.setTextSize(FontScaleHelper.small(requireContext()));
        mapMetaView.setTextColor(0xFF627B92);
        mapMetaView.setPadding(10, 8, 10, 14);
        mapMetaView.setText(MapMemberVisualizer.buildLegendText());
        card.addView(mapMetaView);

        try {
            mapView = new MapView(requireContext());
            mapView.onCreate(mapViewState);

            FrameLayout mapContainer = new FrameLayout(requireContext());
            LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 680);
            mapContainer.setLayoutParams(mapParams);

            FrameLayout.LayoutParams mapViewParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            mapView.setLayoutParams(mapViewParams);
            mapContainer.addView(mapView);

            TextView reviewNoView = createAmapReviewNoView();
            FrameLayout.LayoutParams reviewNoParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            reviewNoParams.gravity = Gravity.BOTTOM | Gravity.START;
            reviewNoParams.leftMargin = 12;
            reviewNoParams.bottomMargin = 12;
            mapContainer.addView(reviewNoView, reviewNoParams);

            card.addView(mapContainer);
            initMap();
        } catch (Exception e) {
            Log.e(TAG, "地图初始化失败", e);
            mapReady = false;
            mapView = null;
            aMap = null;
            mapHintView.setText("地图初始化失败，已切换为文字模式显示最近共享位置。");
            mapMetaView.setText("你仍可通过上方按钮继续共享位置，家属端会读取同一份云端数据。");
        }
        return card;
    }

    private TextView createAmapReviewNoView() {
        TextView reviewNoView = new TextView(requireContext());
        reviewNoView.setText(AMAP_REVIEW_NO);
        reviewNoView.setTextSize(11f);
        reviewNoView.setTextColor(0xCCFFFFFF);
        reviewNoView.setPadding(8, 4, 8, 4);
        reviewNoView.setBackgroundColor(0x66000000);
        return reviewNoView;
    }

    private View createMemberCard(int bodySize) {
        LinearLayout card = createCard(0xFFFFFFFF, 26, 24);

        statusTag = new TextView(requireContext());
        statusTag.setTextSize(FontScaleHelper.secondary(requireContext()));
        statusTag.setPadding(20, 14, 20, 14);
        card.addView(statusTag);

        infoView = new TextView(requireContext());
        infoView.setTextSize(bodySize);
        infoView.setPadding(0, 12, 0, 14);
        card.addView(infoView);

        TextView memberTitle = new TextView(requireContext());
        memberTitle.setText("家庭成员共享信息");
        memberTitle.setTypeface(Typeface.DEFAULT_BOLD);
        memberTitle.setTextSize(bodySize + 1);
        memberTitle.setPadding(0, 6, 0, 8);
        card.addView(memberTitle);

        memberListView = new TextView(requireContext());
        memberListView.setTextSize(FontScaleHelper.secondary(requireContext()));
        memberListView.setTextColor(0xFF334155);
        memberListView.setText("正在同步成员共享信息...");
        card.addView(memberListView);
        return card;
    }

    private LinearLayout createCard(int color, int radius, int padding) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(createRoundedBg(color, radius));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 18;
        card.setLayoutParams(params);
        return card;
    }

    private void refreshShareViews() {
        String status = safeText(preferenceManager.getShareStatus(), "未共享");
        String location = safeText(preferenceManager.getShareLastLocation(), "等待主动共享");
        String time = safeText(preferenceManager.getShareLastTime(), "暂无");
        String endTime = safeText(preferenceManager.getShareEndTime(), "未设置");
        String owner = safeText(preferenceManager.getSharedLocationOwner(), "ELDER");

        statusTag.setText("  " + status + "  ");
        statusTag.setTextColor(0xFF334155);
        if ("实时共享中".equals(status)) {
            statusTag.setBackground(createRoundedBg(0xFFA5D6A7, 18));
        } else if ("临时共享中".equals(status)) {
            statusTag.setBackground(createRoundedBg(0xFFFFF59D, 18));
        } else if ("单次共享".equals(status)) {
            statusTag.setBackground(createRoundedBg(0xFFBBDEFB, 18));
        } else if ("已结束".equals(status)) {
            statusTag.setBackground(createRoundedBg(0xFFFFCDD2, 18));
        } else {
            statusTag.setBackground(createRoundedBg(0xFFDDE7F3, 18));
        }

        infoView.setText("最近位置：" + location
                + "\n最近更新时间：" + time
                + "\n共享结束说明：" + endTime
                + "\n共享来源：" + owner
                + "\n紧急联系人：" + safeText(preferenceManager.getEmergencyContact(), "120"));

        updateRealtimeStateCopy();
        updateMapForSavedLocation();
    }

    private void updateRealtimeStateCopy() {
        boolean realtimeEnabled = preferenceManager.isRealtimeLocationEnabled();
        if (realtimeToggleButton != null) {
            realtimeToggleButton.setText(realtimeEnabled ? "关闭实时共享" : "开启实时共享");
        }
        if (realtimeHintView != null) {
            realtimeHintView.setText(realtimeEnabled
                    ? "实时共享已开启：每 20 秒自动刷新一次并同步到云端。"
                    : "实时共享未开启：仅在你点击按钮时上传位置。");
        }
        if (refreshStateView != null) {
            String refreshText;
            if (realtimeEnabled && autoRefreshInProgress) {
                refreshText = "自动刷新中：正在获取当前位置并同步家庭成员数据...";
            } else if (realtimeEnabled) {
                refreshText = "自动刷新已开启：等待下一轮刷新。";
            } else {
                refreshText = "自动刷新已关闭。";
            }
            refreshStateView.setText(refreshText);
        }
    }

    private void toggleRealtimeSharing() {
        boolean target = !preferenceManager.isRealtimeLocationEnabled();
        preferenceManager.setRealtimeLocationEnabled(target);
        if (target) {
            preferenceManager.saveShareStatus("实时共享中");
            preferenceManager.saveShareEndTime("持续共享，直到手动关闭");
            refreshShareViews();
            requestLocationOnlyRefresh("开启实时共享", preferenceManager.getShareLastLocation());
            requestFamilyMembers(false);
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
                requestFamilyMembers(false);
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

    private void setupPermissionLauncher() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (fineGranted || coarseGranted) {
                        if (aMap != null) {
                            enableMyLocationLayer();
                        }
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
                        Toast.makeText(requireContext(), "请先授予定位权限，再进行位置共享", Toast.LENGTH_SHORT).show();
                        finishRequest(pendingFromAutoRefresh);
                        clearPendingRequest();
                    }
                });
    }

    private void initMap() {
        if (mapView == null) {
            return;
        }
        try {
            aMap = mapView.getMap();
        } catch (Exception e) {
            Log.e(TAG, "获取地图实例失败", e);
            aMap = null;
        }
        if (aMap == null) {
            mapReady = false;
            mapHintView.setText("地图初始化失败，请检查高德地图配置。");
            return;
        }

        mapReady = true;
        aMap.getUiSettings().setZoomControlsEnabled(true);
        aMap.getUiSettings().setCompassEnabled(false);
        aMap.getUiSettings().setScaleControlsEnabled(false);
        aMap.getUiSettings().setRotateGesturesEnabled(false);
        aMap.getUiSettings().setTiltGesturesEnabled(false);
        aMap.getUiSettings().setScrollGesturesEnabled(true);
        aMap.getUiSettings().setZoomGesturesEnabled(true);
        aMap.setTrafficEnabled(false);
        aMap.setMapTextZIndex(2);

        enableMyLocationLayer();
        updateMapForSavedLocation();
    }

    private void enableMyLocationLayer() {
        if (aMap == null) {
            return;
        }
        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fineGranted && !coarseGranted) {
            return;
        }
        try {
            MyLocationStyle myLocationStyle = new MyLocationStyle();
            myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE);
            myLocationStyle.showMyLocation(true);
            aMap.setMyLocationStyle(myLocationStyle);
            aMap.setMyLocationEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "启用地图定位层失败", e);
        }
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

            if (mapHintView != null) {
                mapHintView.setText(trigger + "：正在获取当前位置并同步到云端...");
            }
        } catch (Exception e) {
            Log.e(TAG, "发起定位失败", e);
            String now = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
            LatLng point = ShareMapDataHelper.getStoredOrFallback(
                    preferenceManager.getShareLatitude(),
                    preferenceManager.getShareLongitude(),
                    fallbackLocation);
            persistShareResult(
                    safeText(status, "单次共享"),
                    safeText(fallbackLocation, "当前位置"),
                    safeText(endText, "本次共享已完成"),
                    point,
                    now,
                    expireAtMs,
                    !"已结束".equals(status),
                    fromAutoRefresh
            );
        }
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
                preferenceManager.saveSharedLocationOwner("ELDER");
                refreshShareViews();
                requestFamilyMembers(false);
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

    private void persistShareResult(String status, String locationLabel, String endText, LatLng point, String time,
                                    @Nullable Long expireAtMs, boolean enabled, boolean fromAutoRefresh) {
        String normalizedLocation = safeText(locationLabel, "当前位置");
        preferenceManager.saveShareStatus(safeText(status, "单次共享"));
        preferenceManager.saveShareLastLocation(normalizedLocation);
        preferenceManager.saveShareLastTime(time);
        preferenceManager.saveShareEndTime(safeText(endText, "未设置"));
        preferenceManager.saveShareLatitude(point.latitude);
        preferenceManager.saveShareLongitude(point.longitude);
        preferenceManager.saveSharedLocationOwner("ELDER");
        preferenceManager.saveSharedSessionId("CLOUD-LOCATION-SESSION");
        preferenceManager.setSharedLocationVisibleToBoth(enabled);

        locationRepository.uploadMemberLocation(point.latitude, point.longitude, normalizedLocation, enabled, expireAtMs,
                new LocationRepository.ResultCallback<Map<String, Object>>() {
                    @Override
                    public void onSuccess(Map<String, Object> data) {
                        if (!isAdded()) {
                            finishRequest(fromAutoRefresh);
                            return;
                        }
                        refreshShareViews();
                        requestFamilyMembers(false);
                        if (!fromAutoRefresh) {
                            Toast.makeText(requireContext(), "位置已同步到云端，家属端可查看", Toast.LENGTH_SHORT).show();
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
                        requestFamilyMembers(false);
                        if (!fromAutoRefresh) {
                            Toast.makeText(requireContext(), safeText(message, "位置同步失败"), Toast.LENGTH_SHORT).show();
                        }
                        finishRequest(fromAutoRefresh);
                    }
                });
    }

    private void requestFamilyMembers(boolean showToast) {
        locationRepository.fetchFamilyLatestLocations(new LocationRepository.ResultCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (!isAdded()) {
                    return;
                }
                renderFamilyMembers(data);
                if (showToast) {
                    Toast.makeText(requireContext(), "家庭成员共享信息已刷新", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                memberListView.setText("家庭成员共享信息刷新失败：" + safeText(message, "请稍后重试"));
            }
        });
    }

    private void renderFamilyMembers(@Nullable List<Map<String, Object>> rawData) {
        List<MemberSnapshot> allMembers = new ArrayList<>();
        List<MemberSnapshot> locatedMembers = new ArrayList<>();
        int elderIndex = 0;
        int familyIndex = 0;

        if (rawData != null) {
            for (int i = 0; i < rawData.size(); i++) {
                Map<String, Object> item = rawData.get(i);
                if (item == null) {
                    continue;
                }

                MemberSnapshot snapshot = new MemberSnapshot();
                snapshot.userId = extractLong(item.get("userId"));
                snapshot.memberId = "member_" + (snapshot.userId > 0 ? snapshot.userId : (i + 1));
                snapshot.name = firstNonEmpty(asText(item.get("nickname")), asText(item.get("name")));
                if (snapshot.name.isEmpty()) {
                    snapshot.name = "成员" + (i + 1);
                }
                snapshot.role = resolveRole(asText(item.get("role")));
                snapshot.roleLabel = "ELDER".equals(snapshot.role) ? "老人" : "家属";
                snapshot.address = safeText(asText(item.get("address")), "未知位置");
                snapshot.timeText = safeText(asText(item.get("updatedAt")), "暂无");
                snapshot.status = resolveStatusText(item.get("enabled"), asText(item.get("locationError")));
                snapshot.hasLocation = item.get("hasLocation") instanceof Boolean && (Boolean) item.get("hasLocation");

                Object latObj = item.get("latitude");
                Object lngObj = item.get("longitude");
                if (snapshot.hasLocation && latObj instanceof Number && lngObj instanceof Number) {
                    snapshot.latLng = new LatLng(((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue());
                    snapshot.colorHue = MapMemberVisualizer.resolveColorHue(
                            snapshot.role,
                            "ELDER".equals(snapshot.role) ? elderIndex++ : familyIndex++
                    );
                    locatedMembers.add(snapshot);
                }
                allMembers.add(snapshot);
            }
        }

        StringBuilder text = new StringBuilder();
        if (allMembers.isEmpty()) {
            text.append("暂无家庭成员共享记录。\n");
            text.append("请确认家属端和老人端均已开启定位权限，并至少执行一次共享。");
        } else {
            text.append("已同步 ").append(allMembers.size()).append(" 位成员，含 ")
                    .append(locatedMembers.size()).append(" 个有效位置。\n\n");
            int index = 1;
            for (MemberSnapshot member : allMembers) {
                text.append(index++).append(". ")
                        .append(member.name).append("（").append(member.roleLabel).append("）\n")
                        .append("   状态：").append(member.status).append("\n")
                        .append("   位置：").append(member.hasLocation ? member.address : "未共享或未上传坐标").append("\n")
                        .append("   更新时间：").append(member.timeText).append("\n");
            }
        }
        memberListView.setText(text.toString().trim());
        renderFamilyMarkers(locatedMembers);
    }

    private void renderFamilyMarkers(List<MemberSnapshot> locatedMembers) {
        if (!mapReady || aMap == null) {
            return;
        }
        aMap.clear();

        if (locatedMembers.isEmpty()) {
            updateMapForSavedLocation();
            if (mapMetaView != null) {
                mapMetaView.setText("当前家庭暂无可展示共享位置。");
            }
            return;
        }

        String primaryMemberId = choosePrimaryMemberId(locatedMembers);
        LatLng primaryPoint = null;
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

        for (MemberSnapshot member : locatedMembers) {
            boolean isPrimary = member.memberId.equals(primaryMemberId);
            MapMemberVisualizer.MemberPoint point = new MapMemberVisualizer.MemberPoint(
                    member.memberId,
                    member.name + "（" + member.roleLabel + "）",
                    member.role,
                    member.latLng,
                    member.status,
                    member.timeText,
                    member.colorHue,
                    isPrimary
            );
            aMap.addMarker(MapMemberVisualizer.createMarkerOptions(point, isPrimary));
            boundsBuilder.include(member.latLng);
            if (isPrimary) {
                primaryPoint = member.latLng;
            }
        }

        if (primaryPoint == null) {
            primaryPoint = locatedMembers.get(0).latLng;
        }
        moveCameraForMembers(boundsBuilder, primaryPoint);

        if (mapHintView != null) {
            mapHintView.setText("地图已展示 " + locatedMembers.size() + " 位共享成员位置。");
        }
        if (mapMetaView != null) {
            String now = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
            mapMetaView.setText("最近同步：" + now + " · " + MapMemberVisualizer.buildLegendText());
        }
    }

    private String choosePrimaryMemberId(List<MemberSnapshot> locatedMembers) {
        long selfUserId = preferenceManager.getUserId();
        for (MemberSnapshot member : locatedMembers) {
            if (member.userId > 0 && member.userId == selfUserId) {
                return member.memberId;
            }
        }
        for (MemberSnapshot member : locatedMembers) {
            if ("ELDER".equals(member.role)) {
                return member.memberId;
            }
        }
        return locatedMembers.get(0).memberId;
    }

    private void moveCameraForMembers(LatLngBounds.Builder boundsBuilder, LatLng primaryPoint) {
        if (aMap == null || primaryPoint == null) {
            return;
        }
        if (lastCameraPoint == null) {
            try {
                aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 130));
            } catch (Exception e) {
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(primaryPoint, 15.5f));
            }
            lastCameraPoint = primaryPoint;
            return;
        }

        if (distance(lastCameraPoint, primaryPoint) > 0.00005d) {
            aMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition(primaryPoint, 16f, 0, 0)));
            lastCameraPoint = primaryPoint;
        }
    }

    private void updateMapForSavedLocation() {
        LatLng point = ShareMapDataHelper.getStoredOrFallback(
                preferenceManager.getShareLatitude(),
                preferenceManager.getShareLongitude(),
                preferenceManager.getShareLastLocation());
        showSinglePointOnMap(point,
                "最近共享位置",
                safeText(preferenceManager.getShareLastLocation(), "未知位置"));
    }

    private void showSinglePointOnMap(@Nullable LatLng point, String title, String address) {
        LatLng target = point == null ? DEFAULT_SHARE_POINT : point;
        if (mapHintView != null) {
            mapHintView.setText(title + "：" + safeText(address, "未知位置"));
        }
        if (mapMetaView != null) {
            String accuracyText = lastAccuracyMeters > 0
                    ? " · 精度约 " + String.format(Locale.CHINA, "%.1f 米", lastAccuracyMeters)
                    : "";
            mapMetaView.setText("更新时间：" + safeText(preferenceManager.getShareLastTime(), "暂无")
                    + " · 坐标：" + ShareMapDataHelper.formatLatLng(target)
                    + accuracyText);
        }
        if (aMap == null) {
            return;
        }
        try {
            aMap.clear();
            aMap.addMarker(new com.amap.api.maps.model.MarkerOptions()
                    .position(target)
                    .title(title)
                    .snippet(address))
                    .showInfoWindow();
            if (lastCameraPoint == null || distance(lastCameraPoint, target) > 0.00005d) {
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16f));
                lastCameraPoint = target;
            }
        } catch (Exception e) {
            Log.e(TAG, "地图更新失败", e);
        }
    }

    private double distance(LatLng a, LatLng b) {
        double dx = a.latitude - b.latitude;
        double dy = a.longitude - b.longitude;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation == null) {
            finishRequest(pendingFromAutoRefresh);
            clearPendingRequest();
            return;
        }
        if (aMapLocation.getErrorCode() != 0) {
            Log.e(TAG, "定位失败 code=" + aMapLocation.getErrorCode() + " info=" + aMapLocation.getErrorInfo());
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
        lastAccuracyMeters = aMapLocation.getAccuracy();
        LatLng point = new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude());

        String status = safeText(pendingShareStatus, "单次共享");
        String endText = safeText(pendingShareEndText, "本次共享已完成");
        Long expire = pendingExpireAtMs;
        boolean fromAuto = pendingFromAutoRefresh;
        boolean enabled = !"已结束".equals(status);

        persistShareResult(status, address, endText, point, time, expire, enabled, fromAuto);
        clearPendingRequest();
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

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        refreshShareViews();
        requestFamilyMembers(false);
        if (preferenceManager != null && preferenceManager.isRealtimeLocationEnabled()) {
            requestLocationOnlyRefresh("进入页面自动刷新", preferenceManager.getShareLastLocation());
            startAutoRefresh();
        } else {
            stopAutoRefresh();
        }
    }

    @Override
    public void onPause() {
        stopAutoRefresh();
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            Bundle state = new Bundle();
            mapView.onSaveInstanceState(state);
            outState.putBundle(MAP_VIEW_STATE_KEY, state);
        }
    }

    @Override
    public void onDestroyView() {
        stopAutoRefresh();
        if (mapView != null) {
            mapView.onDestroy();
            mapView = null;
        }
        if (locationClient != null) {
            try {
                locationClient.stopLocation();
                locationClient.onDestroy();
            } catch (Exception ignored) {
            }
            locationClient = null;
        }
        aMap = null;
        mapReady = false;
        lastCameraPoint = null;
        clearPendingRequest();
        super.onDestroyView();
    }

    private String resolveRole(String role) {
        return "ELDER".equalsIgnoreCase(role) ? "ELDER" : "FAMILY";
    }

    private String resolveStatusText(Object enabledObj, String fallback) {
        boolean enabled = !(enabledObj instanceof Boolean) || (Boolean) enabledObj;
        if (!enabled) {
            return safeText(fallback, "未开启共享");
        }
        return "共享中";
    }

    private long extractLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return -1L;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
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

    private GradientDrawable createRoundedBg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static final class MemberSnapshot {
        private long userId;
        private String memberId;
        private String name;
        private String role;
        private String roleLabel;
        private String status;
        private String timeText;
        private String address;
        private boolean hasLocation;
        private LatLng latLng;
        private float colorHue;
    }
}
