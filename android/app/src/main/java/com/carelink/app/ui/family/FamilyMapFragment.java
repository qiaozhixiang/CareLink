package com.carelink.app.ui.family;

import android.content.Intent;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.repository.FamilyRepository;
import com.carelink.app.data.repository.LocationRepository;
import com.carelink.app.remote.FamilyRemoteViewerActivity;
import com.carelink.app.utils.MapMemberVisualizer;
import com.carelink.app.utils.ShareMapDataHelper;
import com.carelink.app.utils.UiThemeHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * 家属端地图页面：展示家庭成员共享位置，并支持快速发起远程协助。
 */
@AndroidEntryPoint
public class FamilyMapFragment extends Fragment {

    private static final String TAG = "FamilyMapFragment";
    private static final String MAP_STATE_KEY = "family_map_view_state";
    private static final long AUTO_REFRESH_MS = 10000L;
    private static final String AMAP_REVIEW_NO = "审图号：GS (2023)551号 | GS (2023)2175号";

    @Inject
    LocationRepository locationRepository;
    @Inject
    FamilyRepository familyRepository;

    private TextView mapHintView;
    private TextView syncTipView;
    private TextView mapMetaView;
    private TextView memberDetailView;
    private TextView summaryInfoView;
    private MapView mapView;
    private Bundle mapViewState;

    private AMap aMap;
    private boolean mapReady;
    private final Map<String, Marker> markerMap = new HashMap<>();
    private final Map<String, MapMemberVisualizer.MemberPoint> pointMap = new HashMap<>();
    private final Map<String, String> addressMap = new HashMap<>();
    private final List<MapMemberVisualizer.MemberPoint> latestMembers = new ArrayList<>();
    private String highlightedId;
    private LatLng lastFocusedPoint;
    private boolean firstCameraMoveDone;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) {
                return;
            }
            requestFamilyLocations(true);
            refreshHandler.postDelayed(this, AUTO_REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mapViewState = savedInstanceState != null ? savedInstanceState.getBundle(MAP_STATE_KEY) : null;
        PreferenceManager preferenceManager = new PreferenceManager(requireContext());

        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(UiThemeHelper.color(requireContext(),
                com.carelink.app.R.color.surface_page));
        scrollView.addView(root);

        root.addView(createHeroCard(
                safeName(preferenceManager.getShareStatus(), "未共享"),
                safeName(preferenceManager.getShareLastLocation(), "暂无")));
        root.addView(createRemoteAssistCard());
        root.addView(createMapCard());
        root.addView(createMemberDetailCard());
        root.addView(createSummaryCard(preferenceManager));
        root.addView(createTipCard());
        return scrollView;
    }

    private View createHeroCard(String status, String location) {
        LinearLayout card = createCard(0xFF2F80ED, 30, 28);

        TextView title = new TextView(requireContext());
        title.setText("家庭共享位置");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        card.addView(title);

        TextView content = new TextView(requireContext());
        content.setText("当前共享状态：" + status + "\n最近同步位置：" + location);
        content.setTextSize(15);
        content.setTextColor(0xFFEAF4FF);
        content.setPadding(0, 12, 0, 0);
        card.addView(content);

        syncTipView = new TextView(requireContext());
        syncTipView.setTextSize(14);
        syncTipView.setTextColor(0xFFEAF4FF);
        syncTipView.setPadding(0, 14, 0, 0);
        syncTipView.setText("正在同步家庭成员共享位置...");
        card.addView(syncTipView);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
        params.bottomMargin = 16;
        return card;
    }

    private View createRemoteAssistCard() {
        LinearLayout card = createCard(0xFF7C3AED, 24, 22);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> showRemoteAssistPicker());

        TextView title = new TextView(requireContext());
        title.setText("远程协助");
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 0, 0, 6);
        card.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("按成员列表选择需要协助的老人，建立双端远程协助连接。");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xCCFFFFFF);
        card.addView(subtitle);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
        params.bottomMargin = 16;
        return card;
    }

    private View createMapCard() {
        LinearLayout card = createCard(0xFFF2F7FD, 24, 20);

        mapHintView = new TextView(requireContext());
        mapHintView.setText("正在加载家庭成员位置地图...");
        mapHintView.setTextSize(15);
        mapHintView.setPadding(6, 6, 6, 10);
        card.addView(mapHintView);

        mapMetaView = new TextView(requireContext());
        mapMetaView.setTextSize(13);
        mapMetaView.setTextColor(0xFF4E6A85);
        mapMetaView.setPadding(6, 0, 6, 16);
        mapMetaView.setText(MapMemberVisualizer.buildLegendText());
        card.addView(mapMetaView);

        try {
            mapView = new MapView(requireContext());
            mapView.onCreate(mapViewState);

            FrameLayout mapContainer = new FrameLayout(requireContext());
            LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 640);
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
            Log.e(TAG, "地图组件初始化失败", e);
            mapReady = false;
            mapView = null;
            if (mapHintView != null) {
                mapHintView.setText("地图初始化失败，当前已降级为文字模式展示。");
            }
            if (mapMetaView != null) {
                mapMetaView.setText("可先查看下方成员共享信息，稍后再尝试进入地图。 ");
            }
        }

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
        params.bottomMargin = 16;
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

    private View createMemberDetailCard() {
        LinearLayout card = createCard(0xFFEEF6FF, 22, 22);

        TextView label = new TextView(requireContext());
        label.setText("成员详情");
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(0xFF2F80ED);
        label.setPadding(0, 0, 0, 10);
        card.addView(label);

        memberDetailView = new TextView(requireContext());
        memberDetailView.setTextSize(14);
        memberDetailView.setTextColor(0xFF334155);
        memberDetailView.setText("点击地图上的成员标记，可在此查看详细信息。");
        card.addView(memberDetailView);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
        params.bottomMargin = 14;
        return card;
    }

    private View createSummaryCard(PreferenceManager preferenceManager) {
        summaryInfoView = new TextView(requireContext());
        summaryInfoView.setText(buildSummaryInfo(
                safeName(preferenceManager.getNickname(), "家庭成员"),
                "成员共享状态待同步",
                safeName(preferenceManager.getShareLastLocation(), "暂无"),
                safeName(preferenceManager.getShareLastTime(), "暂无"),
                ShareMapDataHelper.getStoredOrFallback(
                        preferenceManager.getShareLatitude(),
                        preferenceManager.getShareLongitude(),
                        preferenceManager.getShareLastLocation()),
                0,
                0));
        summaryInfoView.setTextSize(15);
        summaryInfoView.setPadding(20, 20, 20, 20);
        summaryInfoView.setBackground(createRoundedBg(0xFFFFFFFF, 24));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 16;
        summaryInfoView.setLayoutParams(params);
        return summaryInfoView;
    }

    private View createTipCard() {
        TextView tip = new TextView(requireContext());
        tip.setText("仅显示已开启共享并上报了坐标的成员。若成员未共享，会在详情里显示“未开启共享”。");
        tip.setTextSize(13);
        tip.setPadding(22, 22, 22, 22);
        tip.setBackground(createRoundedBg(0xFFFFFFFF, 22));
        return tip;
    }

    private LinearLayout createCard(int color, int radius, int padding) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(createRoundedBg(color, radius));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
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
            if (mapHintView != null) {
                mapHintView.setText("地图初始化失败，请确认高德配置是否生效。");
            }
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

        aMap.setOnMarkerClickListener(marker -> {
            Object tag = marker.getObject();
            if (tag instanceof String) {
                onMemberMarkerClicked((String) tag);
            }
            return false;
        });
        aMap.setOnMapClickListener(latLng -> clearHighlight());

        requestFamilyLocations(false);
    }

    private void requestFamilyLocations(boolean showRefreshingCopy) {
        if (!isAdded()) {
            return;
        }
        if (mapHintView != null) {
            mapHintView.setText(showRefreshingCopy
                    ? "正在刷新家庭共享位置..."
                    : "正在同步家庭成员共享位置...");
        }

        locationRepository.fetchFamilyLatestLocations(new LocationRepository.ResultCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (!isAdded()) {
                    return;
                }
                BuildResult result = buildMemberPoints(data);
                applyMemberPoints(result);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                showMapError(message);
            }
        });
    }

    private BuildResult buildMemberPoints(List<Map<String, Object>> rawMembers) {
        BuildResult result = new BuildResult();
        result.totalCount = safeSize(rawMembers);
        if (rawMembers == null || rawMembers.isEmpty()) {
            return result;
        }

        long selfUserId = new PreferenceManager(requireContext()).getUserId();
        List<TempMember> temp = new ArrayList<>();
        int elderIndex = 0;
        int familyIndex = 0;

        for (Map<String, Object> item : rawMembers) {
            if (item == null) {
                continue;
            }

            String roleType = resolveRoleType(asText(item.get("role")));
            long userId = extractLong(item.get("userId"));
            String memberId = "member_" + (userId > 0 ? userId : (temp.size() + 1));
            String displayName = safeName(
                    firstNonEmpty(asText(item.get("nickname")), asText(item.get("name"))),
                    "成员" + (temp.size() + 1));

            Object hasLocationObj = item.get("hasLocation");
            boolean hasLocation = hasLocationObj instanceof Boolean && (Boolean) hasLocationObj;
            if (!hasLocation) {
                continue;
            }

            Object latObj = item.get("latitude");
            Object lngObj = item.get("longitude");
            if (!(latObj instanceof Number) || !(lngObj instanceof Number)) {
                continue;
            }

            LatLng latLng = new LatLng(((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue());
            String address = safeName(asText(item.get("address")), "未知位置");
            String status = resolveStatusText(item.get("enabled"), asText(item.get("locationError")));
            String timeText = safeName(asText(item.get("updatedAt")), "暂无更新时间");
            float hue = MapMemberVisualizer.resolveColorHue(roleType,
                    "ELDER".equals(roleType) ? elderIndex++ : familyIndex++);

            TempMember m = new TempMember();
            m.memberId = memberId;
            m.displayName = displayName;
            m.role = roleType;
            m.latLng = latLng;
            m.status = status;
            m.timeText = timeText;
            m.hue = hue;
            m.address = address;
            m.userId = userId;
            temp.add(m);
        }

        result.sharedCount = temp.size();
        if (temp.isEmpty()) {
            return result;
        }

        String primaryId = "";
        for (TempMember m : temp) {
            if (m.userId > 0 && m.userId == selfUserId) {
                primaryId = m.memberId;
                break;
            }
        }
        if (primaryId.isEmpty()) {
            for (TempMember m : temp) {
                if ("ELDER".equals(m.role)) {
                    primaryId = m.memberId;
                    break;
                }
            }
        }
        if (primaryId.isEmpty()) {
            primaryId = temp.get(0).memberId;
        }

        for (TempMember m : temp) {
            result.points.add(new MapMemberVisualizer.MemberPoint(
                    m.memberId,
                    m.displayName,
                    m.role,
                    m.latLng,
                    m.status,
                    m.timeText,
                    m.hue,
                    m.memberId.equals(primaryId)
            ));
            result.addressById.put(m.memberId, m.address);
        }
        return result;
    }

    private void applyMemberPoints(BuildResult result) {
        latestMembers.clear();
        latestMembers.addAll(result.points);
        addressMap.clear();
        addressMap.putAll(result.addressById);

        clearMarkers();
        pointMap.clear();
        highlightedId = null;

        if (!mapReady || aMap == null) {
            return;
        }

        if (result.points.isEmpty()) {
            if (mapHintView != null) {
                mapHintView.setText("当前家庭暂无成员在共享位置。");
            }
            if (syncTipView != null) {
                syncTipView.setText("家庭成员总数 " + result.totalCount + "，共享中 0 位");
            }
            if (mapMetaView != null) {
                mapMetaView.setText("成员开启共享后会显示在地图上。");
            }
            updateSummaryInfo(null, null, result.totalCount, 0);
            return;
        }

        LatLng primaryPoint = null;
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (MapMemberVisualizer.MemberPoint mp : result.points) {
            MarkerOptions options = MapMemberVisualizer.createMarkerOptions(mp, false);
            Marker marker = aMap.addMarker(options);
            if (marker != null) {
                marker.setObject(mp.memberId);
                markerMap.put(mp.memberId, marker);
            }
            pointMap.put(mp.memberId, mp);
            boundsBuilder.include(mp.latLng);
            if (mp.primary) {
                primaryPoint = mp.latLng;
            }
        }

        if (primaryPoint == null) {
            primaryPoint = result.points.get(0).latLng;
        }
        moveCameraIfNeeded(boundsBuilder.build(), primaryPoint);
        lastFocusedPoint = primaryPoint;

        MapMemberVisualizer.MemberPoint primary = findPrimaryMember();
        String primaryAddress = primary == null ? "暂无" : safeName(addressMap.get(primary.memberId), "未知位置");

        if (mapHintView != null) {
            mapHintView.setText(primary == null
                    ? "家庭共享位置已更新"
                    : "当前主成员：" + primary.displayName + " · " + primaryAddress);
        }
        if (syncTipView != null) {
            syncTipView.setText("已同步 " + result.sharedCount + " 位成员定位 / 家庭总人数 " + result.totalCount);
        }
        if (mapMetaView != null) {
            mapMetaView.setText(MapMemberVisualizer.buildLegendText());
        }
        updateSummaryInfo(primary, primaryAddress, result.totalCount, result.sharedCount);
    }

    private void updateSummaryInfo(@Nullable MapMemberVisualizer.MemberPoint primary,
                                   @Nullable String primaryAddress,
                                   int familyCount,
                                   int sharedCount) {
        if (summaryInfoView == null) {
            return;
        }
        if (primary == null) {
            summaryInfoView.setText("暂无可展示的共享定位。\n家庭成员数：" + familyCount + "\n共享中成员：" + sharedCount);
            return;
        }
        summaryInfoView.setText(buildSummaryInfo(
                primary.displayName,
                primary.status,
                safeName(primaryAddress, "未知位置"),
                primary.timeText,
                primary.latLng,
                familyCount,
                sharedCount));
    }

    private String buildSummaryInfo(String name,
                                    String status,
                                    String address,
                                    String time,
                                    LatLng point,
                                    int familyCount,
                                    int sharedCount) {
        return "主成员姓名：" + safeName(name, "家庭成员")
                + "\n共享状态：" + safeName(status, "未知")
                + "\n最近位置：" + safeName(address, "暂无")
                + "\n更新时间：" + safeName(time, "暂无")
                + "\n地图坐标：" + ShareMapDataHelper.formatLatLng(point)
                + "\n家庭成员数：" + familyCount
                + "\n共享中成员：" + sharedCount;
    }

    private void showMapError(String message) {
        Log.w(TAG, "家庭共享位置同步失败: " + message);
        if (mapHintView != null) {
            mapHintView.setText("家庭共享位置同步失败：" + message);
        }
        if (syncTipView != null) {
            syncTipView.setText("请确认家庭成员已开启位置共享并保持网络正常。");
        }
    }

    private void onMemberMarkerClicked(String memberId) {
        if (highlightedId != null && !highlightedId.equals(memberId)) {
            restoreNormalMarker(highlightedId);
        }

        Marker marker = markerMap.get(memberId);
        MapMemberVisualizer.MemberPoint mp = pointMap.get(memberId);
        if (marker == null || mp == null) {
            return;
        }

        marker.setZIndex(5f);
        marker.setIcon(BitmapDescriptorFactory.defaultMarker(mp.colorHue));
        marker.showInfoWindow();
        highlightedId = memberId;

        if (memberDetailView != null) {
            memberDetailView.setText("姓名：" + mp.displayName
                    + "\n角色：" + roleLabel(mp.role)
                    + "\n状态：" + mp.status
                    + "\n位置：" + safeName(addressMap.get(mp.memberId), "未知位置")
                    + "\n更新：" + mp.timeText
                    + "\n坐标：" + ShareMapDataHelper.formatLatLng(mp.latLng));
        }
    }

    private void restoreNormalMarker(String memberId) {
        Marker marker = markerMap.get(memberId);
        MapMemberVisualizer.MemberPoint mp = pointMap.get(memberId);
        if (marker != null && mp != null) {
            marker.setZIndex(mp.primary ? 2f : 1f);
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(mp.colorHue));
        }
    }

    private void clearHighlight() {
        if (highlightedId != null) {
            restoreNormalMarker(highlightedId);
            highlightedId = null;
        }
        if (memberDetailView != null) {
            memberDetailView.setText("点击地图上的成员标记，可在此查看详细信息。");
        }
    }

    private void moveCameraIfNeeded(LatLngBounds bounds, LatLng primaryPoint) {
        if (aMap == null || primaryPoint == null) {
            return;
        }
        boolean positionChanged = lastFocusedPoint == null
                || Math.abs(lastFocusedPoint.latitude - primaryPoint.latitude) > 0.00003
                || Math.abs(lastFocusedPoint.longitude - primaryPoint.longitude) > 0.00003;

        if (!firstCameraMoveDone) {
            firstCameraMoveDone = true;
            try {
                aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120));
            } catch (Exception e) {
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(primaryPoint, 15.5f));
            }
            return;
        }

        if (positionChanged) {
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(primaryPoint, 16.0f));
        }
    }

    private void clearMarkers() {
        for (Marker marker : markerMap.values()) {
            if (marker != null) {
                marker.remove();
            }
        }
        markerMap.clear();
    }

    private GradientDrawable createRoundedBg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void showRemoteAssistPicker() {
        familyRepository.getElders(new FamilyRepository.ResultCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                if (!isAdded()) {
                    return;
                }
                if (data == null || data.isEmpty()) {
                    launchRemoteAssistWithPrimaryFallback();
                    return;
                }

                CharSequence[] names = new CharSequence[data.size()];
                for (int i = 0; i < data.size(); i++) {
                    names[i] = safeName(asText(data.get(i).get("nickname")), "老人" + (i + 1));
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle("选择要协助的老人")
                        .setItems(names, (dialog, which) -> {
                            String elderId = String.valueOf(extractLong(data.get(which).get("userId")));
                            Intent intent = new Intent(requireContext(), FamilyRemoteViewerActivity.class);
                            intent.putExtra("elder_id", elderId);
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                launchRemoteAssistWithPrimaryFallback();
            }
        });
    }

    private void launchRemoteAssistWithPrimaryFallback() {
        Intent intent = new Intent(requireContext(), FamilyRemoteViewerActivity.class);
        MapMemberVisualizer.MemberPoint primary = findPrimaryMember();
        if (primary != null) {
            String targetId = extractUserIdFromMemberId(primary.memberId);
            if (!targetId.isEmpty()) {
                intent.putExtra("elder_id", targetId);
            }
        }
        startActivity(intent);
    }

    @Nullable
    private MapMemberVisualizer.MemberPoint findPrimaryMember() {
        for (MapMemberVisualizer.MemberPoint member : latestMembers) {
            if (member.primary) {
                return member;
            }
        }
        return latestMembers.isEmpty() ? null : latestMembers.get(0);
    }

    private String resolveStatusText(Object enabledObj, String fallback) {
        boolean enabled = !(enabledObj instanceof Boolean) || (Boolean) enabledObj;
        if (!enabled) {
            return safeName(fallback, "未开启共享");
        }
        return "共享中";
    }

    private String resolveRoleType(String role) {
        return "ELDER".equalsIgnoreCase(role) ? "ELDER" : "FAMILY";
    }

    private String roleLabel(String role) {
        return "ELDER".equalsIgnoreCase(role) ? "老人" : "家属";
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private String extractUserIdFromMemberId(String memberId) {
        if (memberId == null) {
            return "";
        }
        String prefix = "member_";
        if (!memberId.startsWith(prefix)) {
            return "";
        }
        return memberId.substring(prefix.length()).trim();
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

    private int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static String safeName(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }

    private void startAutoRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, AUTO_REFRESH_MS);
    }

    private void stopAutoRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            try {
                mapView.onResume();
            } catch (Exception e) {
                Log.e(TAG, "地图恢复失败", e);
            }
        }
        requestFamilyLocations(true);
        startAutoRefresh();
    }

    @Override
    public void onPause() {
        stopAutoRefresh();
        if (mapView != null) {
            try {
                mapView.onPause();
            } catch (Exception e) {
                Log.e(TAG, "地图暂停失败", e);
            }
        }
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            Bundle childState = new Bundle();
            mapView.onSaveInstanceState(childState);
            outState.putBundle(MAP_STATE_KEY, childState);
        }
    }

    @Override
    public void onDestroyView() {
        stopAutoRefresh();
        clearMarkers();
        pointMap.clear();
        latestMembers.clear();
        addressMap.clear();
        highlightedId = null;

        if (mapView != null) {
            try {
                mapView.onDestroy();
            } catch (Exception e) {
                Log.e(TAG, "地图销毁失败", e);
            }
            mapView = null;
        }

        mapReady = false;
        aMap = null;
        mapViewState = null;
        lastFocusedPoint = null;
        firstCameraMoveDone = false;
        summaryInfoView = null;
        super.onDestroyView();
    }

    private static final class BuildResult {
        private final List<MapMemberVisualizer.MemberPoint> points = new ArrayList<>();
        private final Map<String, String> addressById = new HashMap<>();
        private int totalCount;
        private int sharedCount;
    }

    private static final class TempMember {
        private String memberId;
        private String displayName;
        private String role;
        private LatLng latLng;
        private String status;
        private String timeText;
        private float hue;
        private String address;
        private long userId;
    }
}
