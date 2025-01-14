package com.z3pipe.z3location.content;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.BatteryManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.z3pipe.z3core.util.GeomMathUtil;
import com.z3pipe.z3location.config.PositionCollectionConfig;
import com.z3pipe.z3location.model.ELocationQuality;
import com.z3pipe.z3location.model.Position;
import com.z3pipe.z3location.util.Constants;
import com.z3pipe.z3location.util.FileUtil;
import com.z3pipe.z3location.util.GpsFileUtil;

import java.io.File;

/**
 * @author zhengzhuanzi
 * @link https://www.z3pipe.com
 * @date 2019-04-10
 */
public abstract class PositionProvider {

    public static final String SPLITTER = "#";
    public static final String SPLITTERPOS = "%";
    public static final String SPLITTERPOSLONLAT = ",";
    public static final String NULLSTRING = null;

    public interface PositionListener {
        /***
         * 坐标已经更新
         * @param position
         */
        void onPositionUpdate(Position position);

        /**
         * 定位异常
         *
         * @param error
         */
        void onPositionError(Throwable error);

        /**
         * 定位质量
         *
         * @param quality
         */
        void onLocationQuality(ELocationQuality quality);
    }

    protected final PositionListener listener;

    protected final Context context;
    protected PositionCollectionConfig positionCollectionConfig;
    protected Location lastLocation;

    public PositionProvider(Context context, PositionCollectionConfig positionCollectionConfig, PositionListener listener) {
        this.context = context;
        this.listener = listener;
        this.positionCollectionConfig = positionCollectionConfig;
//        deviceId = preferences.getString(MainFragment.KEY_DEVICE, "undefined");
//        interval = Long.parseLong(preferences.getString(MainFragment.KEY_INTERVAL, "600")) * 1000;
//        distance = Integer.parseInt(preferences.getString(MainFragment.KEY_DISTANCE, "0"));
//        angle = Integer.parseInt(preferences.getString(MainFragment.KEY_ANGLE, "0"));
    }

    /**
     * 开始定位
     */
    public abstract void startUpdates();

    /**
     * 停止定位
     */
    public abstract void stopUpdates();

    /**
     * 定位一次
     */
    public abstract void requestSingleLocation();

    /**
     * 更新坐标
     *
     * @param location
     */
    protected void processLocation(Location location) {
        if (null == location||location.getAccuracy()>200) {
            return;
        }

        if (null == lastLocation) {
            lastLocation = location;
            if (!"0".equals(Constants.USERID)){
                Position position = new Position(Constants.USER_NAME,Constants.TRUE_NAME, Constants.USERID, location, getBatteryLevel(context));
                listener.onPositionUpdate(position);
                writeToFile(position);
            } else {
                Log.d("PositionProvider","用户id无效");
                if (context != null){
                    Toast.makeText(context,"用户id无效，请在智慧运营重新启动位置服务",Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }

        boolean conditionTime = location.getTime() - lastLocation.getTime() >= (5 * 1000);
        boolean conditionDistance =  GeomMathUtil.calculateEllipseDistance(lastLocation.getLatitude(), lastLocation.getLongitude(), location.getLatitude(), location.getLongitude()) >= 0.1;
        boolean conditionAngle = positionCollectionConfig.getAngle() > 0 && Math.abs(location.getBearing() - lastLocation.getBearing()) >= positionCollectionConfig.getAngle();
        if (conditionTime || conditionDistance || conditionAngle) {
            lastLocation = location;
            if (!"0".equals(Constants.USERID)){
                Position position = new Position(Constants.USER_NAME,Constants.TRUE_NAME, Constants.USERID, location, getBatteryLevel(context));
                listener.onPositionUpdate(position);
                writeToFile(position);
            } else {
                Log.d("PositionProvider","用户id无效");
                if (context != null){
                    Toast.makeText(context,"用户id无效，请在智慧运营重新启动位置服务",Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void writeToFile(Position position){
        // 将轨迹点写入文件
        String result = buildPositionString(position);
        if (!TextUtils.isEmpty(result)){
            String filePath = getFilePath(position.getUserId());
            try {
                File file = new File(filePath);
                if (file.exists()) {
                    //file.createNewFile();
                    GpsFileUtil.writeBinaryStream(getFilePath(position.getUserId()),result,true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * 定位出现错误
     *
     * @param message
     */
    protected void onLocationError(String message) {
        listener.onPositionError(new Throwable(message));
    }

    /**
     * 定位质量
     *
     * @param quality
     */
    protected void onLocationQuality(ELocationQuality quality) {
        listener.onLocationQuality(quality);
    }

    /**
     * 获取设备电量
     *
     * @param context
     * @return
     */
    protected static double getBatteryLevel(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            return (level * 100.0) / scale;
        }

        return 0;
    }

    /**
     * 轨迹文件路径
     *
     * @param userID
     * @return
     */
    private String getFilePath(String userID) {
        return FileUtil.getInstance(context).getConfPath() + userID + ".t3.bin";
    }

    /**
     * 从GPS点创建坐标字符串
     *
     * @return
     */
    private String buildPositionString(Position location) {
        if (null == location || location.getAccuracy() > 200) {
            return NULLSTRING;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(location.getLon());
        sb.append(SPLITTERPOSLONLAT);
        sb.append(location.getLat());
        sb.append(SPLITTERPOSLONLAT);
        sb.append(location.getTime());
        sb.append(SPLITTERPOSLONLAT);
        sb.append(location.getAccuracy());
        sb.append(SPLITTERPOSLONLAT);
        sb.append(location.getSpeed());
        sb.append(SPLITTERPOS);
        return sb.toString();
    }

}
