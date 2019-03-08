package com.amap.apis.cluster.demo;

import com.amap.api.maps.model.LatLng;
import com.amap.apis.cluster.ClusterItem;

/**
 * Created by yiyi.qi on 16/10/10.
 */

public class RegionItem implements ClusterItem {
    private LatLng mLatLng;
    private String mTitle;
    private String mUrl;
    public RegionItem(LatLng latLng, String title,String url) {
        mLatLng=latLng;
        mTitle=title;
        mUrl=url;
    }

    @Override
    public String getUrl() {
        return mUrl;
    }

    @Override
    public LatLng getPosition() {
        return mLatLng;
    }
    public String getTitle(){
        return mTitle;
    }

}
