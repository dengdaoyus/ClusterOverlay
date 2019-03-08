package com.amap.apis.cluster;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.widget.TextView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.animation.AlphaAnimation;
import com.amap.api.maps.model.animation.Animation;
import com.amap.api.maps.model.animation.ScaleAnimation;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

import static com.amap.apis.cluster.ClusterOverlay.SignClusterHandler.ZOOM_ADD_SINALE;
import static com.amap.apis.cluster.ClusterOverlay.SignClusterHandler.ZOOM_IN;
import static com.amap.apis.cluster.ClusterOverlay.SignClusterHandler.ZOOM_INIT;
import static com.amap.apis.cluster.ClusterOverlay.SignClusterHandler.ZOOM_OUT;


/**
 * Created by yiyi.qi on 16/10/10.
 * 整体设计采用了两个线程,一个线程用于计算组织聚合数据,一个线程负责处理Marker相关操作
 */
public class ClusterOverlay implements AMap.OnCameraChangeListener,
        AMap.OnMarkerClickListener {
    private AMap mAMap;
    private Context mContext;
    private List<ClusterItem> mClusterItems;
    private List<Cluster> mClusters;
    private int mClusterSize;
    private ClusterClickListener mClusterClickListener;
    private ClusterRender mClusterRender;
    private double mClusterDistance;
    private LruCache<Marker, MarkIconGlideGenerator> mLruCache;
    private HandlerThread mSignClusterThread = new HandlerThread("calculateCluster");
    private Handler mMarkerhandler;
    private Handler mSignClusterHandler;
    private float mPXInMeters;

    /**
     * 构造函数
     *
     * @param amap
     * @param clusterSize 聚合范围的大小（指点像素单位距离内的点会聚合到一个点显示）
     * @param context
     */
    public ClusterOverlay(AMap amap, int clusterSize, Context context) {
        this(amap, null, clusterSize, context);
    }

    /**
     * 设置聚合元素的渲染样式，不设置则默认为气泡加数字形式进行渲染
     *
     * @param render
     */
    public void setClusterRenderer(ClusterRender render) {
        mClusterRender = render;
    }

    /**
     * 构造函数,批量添加聚合元素时,调用此构造函数
     *
     * @param amap
     * @param clusterItems 聚合元素
     * @param clusterSize
     * @param context
     */
    ClusterOverlay(AMap amap, List<ClusterItem> clusterItems,
                   int clusterSize, Context context) {
        //默认最多会缓存80张图片作为聚合显示元素图片,根据自己显示需求和app使用内存情况,可以修改数量
        mLruCache = new LruCache<Marker, MarkIconGlideGenerator>(80) {
            @Override
            protected void entryRemoved(boolean evicted, Marker key, MarkIconGlideGenerator oldValue, MarkIconGlideGenerator newValue) {
                if (evicted && oldValue != null) {
                    oldValue.destroyMarker();
                }
            }
        };
        if (clusterItems != null) {
            mClusterItems = clusterItems;
        } else {
            mClusterItems = new ArrayList<ClusterItem>();
        }
        mContext = context;
        mClusters = new ArrayList<Cluster>();
        this.mAMap = amap;
        mClusterSize = clusterSize;
        mPXInMeters = mAMap.getScalePerPixel();
        mClusterDistance = mPXInMeters * mClusterSize;
        amap.setOnCameraChangeListener(this);
        amap.setOnMarkerClickListener(this);
        initThreadHandler();
        initAssignClusters();
    }

    //对点进行聚合
    private void initAssignClusters() {
        mSignClusterHandler.removeMessages(ZOOM_INIT);//先移除后添加（为了减少资源浪费）
        mSignClusterHandler.sendEmptyMessage(ZOOM_INIT);
    }

    /**
     * 设置聚合点的点击事件
     *
     * @param clusterClickListener
     */
    public void setOnClusterClickListener(
            ClusterClickListener clusterClickListener) {
        mClusterClickListener = clusterClickListener;
    }

    //添加一个聚合点
    public void addClusterItem(ClusterItem item) {
        Message message = Message.obtain();
        message.what = ZOOM_ADD_SINALE;
        message.obj = item;
        mSignClusterHandler.sendMessage(message);
    }

    //初始化Handler
    private void initThreadHandler() {
        mSignClusterThread.start();
        mMarkerhandler = new MarkerHandler(mContext.getMainLooper());
        mSignClusterHandler = new SignClusterHandler(mSignClusterThread.getLooper());
    }

    @Override
    public void onCameraChange(CameraPosition arg0) {

    }

    float level = 0f;

    //放大缩小完成后对聚合点进行重新计算
    @Override
    public void onCameraChangeFinish(CameraPosition arg0) {
        mPXInMeters = mAMap.getScalePerPixel();
        mClusterDistance = mPXInMeters * mClusterSize;
        float levelTemp = arg0.zoom;
        if (levelTemp != level) {
            if (level > levelTemp) assignClusters(ZOOM_OUT);
            else assignClusters(ZOOM_IN);
            level = levelTemp;
        }
    }

    //重新分配聚合点
    private void assignClusters(int zoomType) {
        mSignClusterHandler.sendEmptyMessage(zoomType);
    }

    //点击事件
    @Override
    public boolean onMarkerClick(Marker arg0) {
        if (mClusterClickListener == null) {
            return true;
        }
        Cluster cluster = (Cluster) arg0.getObject();
        if (cluster != null) {
            mClusterClickListener.onClick(arg0, cluster.getClusterItems());
            return true;
        }
        return false;
    }

    //初始化数据，判断聚合点是否在当前显示页面
    private void calculateClusters() {
        mClusters.clear();
        LatLngBounds visibleBounds = mAMap.getProjection().getVisibleRegion().latLngBounds;
        for (ClusterItem clusterItem : mClusterItems) {
            LatLng latlng = clusterItem.getPosition();
            if (visibleBounds.contains(latlng)) {
                Cluster cluster = getCluster(latlng, mClusters);
                if (cluster != null) {
                    cluster.addClusterItem(clusterItem);
                } else {
                    cluster = new Cluster(latlng, clusterItem.getUrl());
                    mClusters.add(cluster);
                    cluster.addClusterItem(clusterItem);
                }

            }
        }

        //复制一份数据，规避同步
        List<Cluster> clusters = new ArrayList<>(mClusters);
        Message message = Message.obtain();
        message.what = MarkerHandler.ADD_CLUSTER_LIST;
        message.obj = clusters;
        mMarkerhandler.sendMessage(message);
    }

    //放大地图
    private void zoomInClusters() {
        mClusters.clear();
        LatLngBounds visibleBounds = mAMap.getProjection().getVisibleRegion().latLngBounds;
        for (ClusterItem clusterItem : mClusterItems) {
            LatLng latlng = clusterItem.getPosition();
            if (visibleBounds.contains(latlng)) {
                Cluster cluster = getCluster(latlng, mClusters);
                if (cluster != null) {
                    cluster.addClusterItem(clusterItem);
                } else {
                    cluster = new Cluster(latlng, clusterItem.getUrl());
                    mClusters.add(cluster);
                    cluster.addClusterItem(clusterItem);
                }

            }
        }

        //复制一份数据，规避同步
        List<Cluster> clusters = new ArrayList<>(mClusters);
        Message message = Message.obtain();
        message.what = MarkerHandler.ADD_CLUSTER_LIST;
        message.obj = clusters;
        mMarkerhandler.sendMessage(message);
    }

    //缩小地图
    private void zoomOutClusters() {
        mClusters.clear();
        LatLngBounds visibleBounds = mAMap.getProjection().getVisibleRegion().latLngBounds;
        for (ClusterItem clusterItem : mClusterItems) {
            LatLng latlng = clusterItem.getPosition();
            if (visibleBounds.contains(latlng)) {
                Cluster cluster = getCluster(latlng, mClusters);
                if (cluster != null) {
                    cluster.addClusterItem(clusterItem);
                } else {
                    cluster = new Cluster(latlng, clusterItem.getUrl());
                    mClusters.add(cluster);
                    cluster.addClusterItem(clusterItem);
                }

            }
        }

        //复制一份数据，规避同步
        List<Cluster> clusters = new ArrayList<>(mClusters);
        Message message = Message.obtain();
        message.what = MarkerHandler.ADD_CLUSTER_LIST;
        message.obj = clusters;
        mMarkerhandler.sendMessage(message);
    }

    //在已有的聚合基础上，对添加的单个元素进行聚合
    private void calculateSingleCluster(ClusterItem clusterItem) {
        LatLngBounds visibleBounds = mAMap.getProjection().getVisibleRegion().latLngBounds;
        LatLng latlng = clusterItem.getPosition();
        if (visibleBounds.contains(latlng)) {
            Cluster cluster = getCluster(latlng, mClusters);
            if (cluster != null) {
                cluster.addClusterItem(clusterItem);
                Message message = Message.obtain();
                message.what = MarkerHandler.UPDATE_SINGLE_CLUSTER;
                message.obj = cluster;
                mMarkerhandler.removeMessages(MarkerHandler.UPDATE_SINGLE_CLUSTER);
                mMarkerhandler.sendMessageDelayed(message, 5);
            } else {
                cluster = new Cluster(latlng, clusterItem.getUrl());
                mClusters.add(cluster);
                cluster.addClusterItem(clusterItem);
                Message message = Message.obtain();
                message.what = MarkerHandler.ADD_SINGLE_CLUSTER;
                message.obj = cluster;
                mMarkerhandler.sendMessage(message);
            }
        }
    }

    //将聚合元素添加至地图上
    private void addClusterToMap(List<Cluster> clusters) {
        if (mLruCache != null && mLruCache.snapshot().size() != 0) {
            for (Map.Entry<Marker, MarkIconGlideGenerator> entry : mLruCache.snapshot().entrySet()) {
                if (entry.getKey() != null) entry.getKey().remove();
            }
        }
        for (Cluster cluster : clusters) {
            addSingleClusterToMap(cluster);
        }
    }

    //将单个聚合元素添加至地图显示
    private void addSingleClusterToMap(Cluster cluster) {
        LatLng latlng = cluster.getCenterLatLng();
        int count = cluster.getClusterCount();
        String avatar = cluster.getAvatar();
        if (cluster.getMarker() == null) {
            MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(latlng);
            markerOptions.anchor(0.5f, 0.5f);
            View view = mClusterRender.getView(null, count);
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromView(view);
            markerOptions.icon(bitmapDescriptor);
            Marker marker = mAMap.addMarker(markerOptions);
            MyAnimationListener myAnimationListener = new MyAnimationListener(marker);
            marker.setAnimationListener(myAnimationListener);
            marker.setAnimation(getScaleAnimation());
            marker.startAnimation();
            marker.setObject(cluster);
            cluster.setMarker(marker);
            mLruCache.put(marker, new MarkIconGlideGenerator(mContext, marker, count, avatar, mClusterRender));
            loadIcon(cluster);
        } else {
            //重新生成icon
            MarkIconGlideGenerator markIconGenerator = mLruCache.get(cluster.getMarker());
            markIconGenerator.seRefreshIcon(count, avatar);
        }
    }

    //加载icon
    private void loadIcon(Cluster cluster) {
        if (!TextUtils.isEmpty(cluster.getAvatar())) {
            MarkIconGlideGenerator target = mLruCache.get(cluster.getMarker());
            if (target != null)
                Glide.with(mContext).load(cluster.getAvatar()).asBitmap().diskCacheStrategy(DiskCacheStrategy.SOURCE).placeholder(R.mipmap.userheadholder).override(40, 40).into(target);
        }
    }

    // 根据一个点获取是否可以依附的聚合点，没有则返回null
    private Cluster getCluster(LatLng latLng, List<Cluster> clusters) {
        for (Cluster cluster : clusters) {
            LatLng clusterCenterPoint = cluster.getCenterLatLng();
            double distance = AMapUtils.calculateLineDistance(latLng, clusterCenterPoint);
            if (distance < mClusterDistance && mAMap.getCameraPosition().zoom < 19) {
                return cluster;
            }
        }
        return null;
    }

    //更新已加入地图聚合点的样式
    private void updateCluster(Cluster cluster) {
        LatLng latlng = cluster.getCenterLatLng();
        int count = cluster.getClusterCount();
        String avatar = cluster.getAvatar();
        if (cluster.getMarker() == null) {
            MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(latlng);
            markerOptions.anchor(0.5f, 0.5f);
            View view = mClusterRender.getView(null, count);
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromView(view);
            markerOptions.icon(bitmapDescriptor);
            Marker marker = mAMap.addMarker(markerOptions);
            marker.setObject(cluster);
            cluster.setMarker(marker);
            mLruCache.put(marker, new MarkIconGlideGenerator(mContext, marker, count, avatar, mClusterRender));
            loadIcon(cluster);
        } else {
            loadIcon(cluster);
        }
    }

    private ScaleAnimation scaleAnimation;

    private ScaleAnimation getScaleAnimation() {
        if (scaleAnimation == null) {
            scaleAnimation = new ScaleAnimation(0f, 1f, 0f, 1f);
            scaleAnimation.setInterpolator(new BounceInterpolator());
            scaleAnimation.setDuration(600);
        }
        return scaleAnimation;
    }


    // 放大缩小地图
    private void moveMarkerAnim(final Marker marker, LatLng start, LatLng end, final Boolean isVisible) {
        ValueAnimator animator = new ValueAnimator();
        animator.setDuration(300);
        animator.setObjectValues(start, end);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        animator.setEvaluator(new TypeEvaluator() {
            @Override
            public Object evaluate(float fraction, Object startValue, Object endValue) {
                LatLng startLatLng = (LatLng) startValue;
                LatLng endLatLng = (LatLng) endValue;

                double longitude = startLatLng.longitude + fraction * (endLatLng.longitude - startLatLng.longitude);
                double latitude = startLatLng.latitude + fraction * (endLatLng.latitude - startLatLng.latitude);
                return new LatLng(latitude, longitude);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation, boolean isReverse) {
                if (isVisible) {
                    marker.setVisible(true);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation, boolean isReverse) {
                marker.setVisible(false);
                animation.removeListener(this);
            }
        });
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                LatLng latLng = (LatLng) animation.getAnimatedValue();
                marker.setPosition(latLng);
            }
        });
        animator.start();
    }


    public void onDestroy() {
        mSignClusterHandler.removeCallbacksAndMessages(null);
        mMarkerhandler.removeCallbacksAndMessages(null);
        mSignClusterThread.quit();

        if (mLruCache != null && mLruCache.snapshot().size() != 0) {
            for (Map.Entry<Marker, MarkIconGlideGenerator> entry : mLruCache.snapshot().entrySet()) {
                if (entry.getKey() != null) entry.getKey().remove();
            }
        }
        mLruCache.evictAll();
    }

//-----------------------辅助内部类用---------------------------------------------

    /**
     * marker渐变动画，动画结束后将Marker删除
     */
    class MyAnimationListener implements Animation.AnimationListener {
        private Marker mRemoveMarkers;

        MyAnimationListener(Marker removeMarkers) {
            mRemoveMarkers = removeMarkers;
        }

        @Override
        public void onAnimationStart() {

        }

        @Override
        public void onAnimationEnd() {
            if (mRemoveMarkers != null) mRemoveMarkers.remove();
        }
    }

    /**
     * 处理market添加，更新等操作
     */
    class MarkerHandler extends Handler {

        static final int ADD_CLUSTER_LIST = 0;

        static final int ADD_SINGLE_CLUSTER = 1;

        static final int UPDATE_SINGLE_CLUSTER = 2;

        MarkerHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {

            switch (message.what) {
                case ADD_CLUSTER_LIST://将聚合点添加在地图上
                    List<Cluster> clusters = (List<Cluster>) message.obj;
                    addClusterToMap(clusters);
                    break;
                case ADD_SINGLE_CLUSTER://将单个聚合点添加在地图上
                    Cluster cluster = (Cluster) message.obj;
                    addSingleClusterToMap(cluster);
                    break;
                case UPDATE_SINGLE_CLUSTER:
                    Cluster updateCluster = (Cluster) message.obj;
                    updateCluster(updateCluster);
                    break;
            }
        }
    }

    /**
     * 处理聚合点算法线程
     */
    class SignClusterHandler extends Handler {

        static final int ZOOM_IN = 0x38; //放大
        static final int ZOOM_OUT = 0x39; //缩小
        static final int ZOOM_INIT = 0x37; //初始化
        static final int ZOOM_ADD_SINALE = 0x36; //添加一个新的点

        SignClusterHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {
            switch (message.what) {
                case ZOOM_INIT:
                    calculateClusters();
                    break;
                case ZOOM_IN:
                    zoomInClusters();
                    break;
                case ZOOM_OUT:
                    zoomOutClusters();
                    break;
                case ZOOM_ADD_SINALE:
                    ClusterItem item = (ClusterItem) message.obj;
                    mClusterItems.add(item);
                    calculateSingleCluster(item);
                    break;
            }
        }
    }
}