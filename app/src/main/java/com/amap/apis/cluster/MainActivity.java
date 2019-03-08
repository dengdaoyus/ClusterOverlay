package com.amap.apis.cluster;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.apis.cluster.demo.RegionItem;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends Activity implements
        AMap.OnMapLoadedListener, ClusterClickListener, ClusterRender {

    private MapView mMapView;
    private AMap mAMap;

    private int clusterRadius = 100;
    private ClusterOverlay mClusterOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMapView = (MapView) findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        init();

    }

    private void init() {
        if (mAMap == null) {
            // 初始化地图
            mAMap = mMapView.getMap();
            mAMap.setOnMapLoadedListener(this);
            //点击可以动态添加点
            mAMap.setOnMapClickListener(new AMap.OnMapClickListener() {
                @Override
                public void onMapClick(LatLng latLng) {
                    double lat = Math.random() + 39.474923;
                    double lon = Math.random() + 116.027116;

                    LatLng latLng1 = new LatLng(lat, lon, false);
                    RegionItem regionItem = new RegionItem(latLng1,
                            "test","http://k.zol-img.com.cn/dcbbs/24684/a24683960_01000.jpg");
                    mClusterOverlay.addClusterItem(regionItem);

                }
            });
        }
    }

    protected void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    protected void onDestroy() {
        super.onDestroy();
        //销毁资源
        mClusterOverlay.onDestroy();
        mMapView.onDestroy();
    }

    @Override
    public void onMapLoaded() {
        //添加测试数据

        List<ClusterItem> items = new ArrayList<ClusterItem>();

        //随机10000个点
        for (int i = 0; i < Images.imageUrls.length; i++) {

            double lat = Math.random() + 39.474923;
            double lon = Math.random() + 116.027116;

            LatLng latLng = new LatLng(lat, lon, false);
            RegionItem regionItem = new RegionItem(latLng,
                    "test" + i, Images.imageUrls[i]);
            items.add(regionItem);

        }
        mClusterOverlay = new ClusterOverlay(mAMap, items,
                dp2px(getApplicationContext(), clusterRadius),
                getApplicationContext());
        mClusterOverlay.setOnClusterClickListener(MainActivity.this);
        mClusterOverlay.setClusterRenderer(this);

    }


    @Override
    public void onClick(Marker marker, List<ClusterItem> clusterItems) {
        if(clusterItems.size()>1){
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (ClusterItem clusterItem : clusterItems) {
                builder.include(clusterItem.getPosition());
            }
            LatLngBounds latLngBounds = builder.build();
            mAMap.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 0)
            );
        }
    }


    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    public int dp2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }


    @Override
    public View getView(Drawable drawable, int clusterNum) {
        final View markerView = LayoutInflater.from(this).inflate(R.layout.marker_bg, null);
        final CircleImageView circleImageView = markerView.findViewById(R.id.marker_item_icon);
        final TextView textView = markerView.findViewById(R.id.textView);
        if (clusterNum > 1) {
            textView.setText(String.valueOf(clusterNum));
        } else {
            textView.setText("");
        }
        if (drawable != null) {
            circleImageView.setImageDrawable(drawable);
        }
        return markerView;
    }
}
