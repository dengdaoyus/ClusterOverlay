package com.amap.apis.cluster;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.Marker;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;

/**
 * Created by Administrator on 2016/7/9.
 */
public class MarkIconGlideGenerator extends SimpleTarget<Bitmap> {

    private Context mContext;
    private Marker marker;
    private Drawable circleDrawable;
    private ClusterRender render;
    private int num;
    private String avatar;

    MarkIconGlideGenerator(Context context,Marker marker, int number, String avatar, ClusterRender render) {
        this.mContext = context;
        this.marker = marker;
        this.num = number;
        this.render = render;
        this.avatar = avatar;
    }

    synchronized void seRefreshIcon(int num, String avatar) {
        this.num = num;
        this.avatar = avatar;
        refreshIcon(circleDrawable);
    }


    void destroyMarker() {
        //销毁应用的逻辑
        marker.destroy();
        marker = null;
        circleDrawable = null;
    }

    private void refreshIcon(Drawable drawable) {
        BitmapDescriptor bitmapDescriptor =
                BitmapDescriptorFactory
                        .fromView(render.getView(drawable,num));
        if (marker != null && bitmapDescriptor != null
                && bitmapDescriptor.getWidth() > 0
                && bitmapDescriptor.getHeight() > 0) {
            marker.setIcon(bitmapDescriptor);
        }
    }

    @Override
    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
        circleDrawable = new BitmapDrawable(mContext.getResources(), resource);
        refreshIcon(circleDrawable);
    }
}
