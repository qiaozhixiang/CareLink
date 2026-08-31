package com.carelink.app.ui.map;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.carelink.app.R;
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MarkerOptions;

public class MapPreviewActivity extends AppCompatActivity {

    private MapView mapView;
    private AMap aMap;
    private TextView debugText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_preview);

        debugText = findViewById(R.id.tv_debug);
        mapView = findViewById(R.id.map_view);

        mapView.onCreate(savedInstanceState);

        try {
            aMap = mapView.getMap();

            if (aMap == null) {
                debugText.setText("AMap 获取失败：aMap == null");
                return;
            } else {
                debugText.setText("AMap 获取成功，正在加载地图...");
            }

            LatLng point = new LatLng(31.2304, 121.4737);
            aMap.addMarker(new MarkerOptions()
                    .position(point)
                    .title("测试位置"));

            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 15f));

            debugText.append("\nMarker 已添加，镜头已移动到测试点。");
            debugText.append("\n请观察地图区域是否显示底图。");
        } catch (Exception e) {
            debugText.setText("地图初始化异常：\n"
                    + e.getClass().getSimpleName()
                    + "\n"
                    + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }
}
