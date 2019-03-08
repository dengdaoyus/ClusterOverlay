package com.amap.apis.cluster;

import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yiyi.qi on 16/10/10.
 */

public class Cluster {


    private LatLng mLatLng;
    private List<ClusterItem> mClusterItems;
    private Marker mMarker;
    private String avatar;


    Cluster( LatLng latLng,String avatar) {
        mLatLng = latLng;
        this.avatar = avatar;
        mClusterItems = new ArrayList<ClusterItem>();

    }

    void addClusterItem(ClusterItem clusterItem) {
        mClusterItems.add(clusterItem);
    }

    int getClusterCount() {
        return mClusterItems.size();
    }


    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    LatLng getCenterLatLng() {
        return mLatLng;
    }

    void setMarker(Marker marker) {
        mMarker = marker;
    }

    Marker getMarker() {
        return mMarker;
    }

    List<ClusterItem> getClusterItems() {
        return mClusterItems;
    }
}
