package utils;
/*
 * Created by fanwe on 2017/5/4.
 */

import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static utils.Quaternion.getQuaternionInverse;
import static utils.Quaternion.getQuaternionMulit;

public class MyUtils {

    private static double Sx = 14f, Sy = 0f;
    private static Double[] accBias = {0.0,0.0,0.0}, accStaDev =  {0.0,0.0,0.0};
    private static LongSparseArray<double[]> locationBasedOnSensor = new LongSparseArray<>();

    public static double getAngleFixed(float angle, double angleBiased){
        double angleBiasedInR = angleBiased * Math.PI / 180;
        double angleFixed = angle - angleBiasedInR;
        if(angleFixed < - Math.PI){
            return angleFixed + 2 * Math.PI;
        }else if(angleFixed > Math.PI){
            return angleFixed - 2 * Math.PI;
        }else
            return angleFixed;
    }

    private static void positiveOrientationList(ArrayList<Double> listOfOrientation){
        for(int i = 0; i < listOfOrientation.size(); i++){
            double value = listOfOrientation.get(i);
            if(value < 0) {
                value += 2 * Math.PI;
                listOfOrientation.set(i,value);
            }
        }
    }

    //每步产生时记录位置，记录时间，为了以后的查询中使用
    public static double[] makeOneStepProcess(ArrayList<Double> listOfOrientation, ArrayList<Long>listOfTime){
        if(listOfOrientation.size() == 0)
            return new double[]{Sx,Sy};
        else {
            long currentTime = Calendar.getInstance().getTimeInMillis();
            double staDevOOrientation = getStaDev(listOfOrientation, getAvg(listOfOrientation), "not sure");
            if (staDevOOrientation > 1.5) {       //方差过大时，认为方向应该为朝向南，处于正负180度之间造成的平均值出现误差，处理方法是将所有负数值加2π。
                positiveOrientationList(listOfOrientation);
            }
            double avgOrientation = getAvg(listOfOrientation);
            double staDev = getStaDev(listOfOrientation, avgOrientation, "not sure");
            double stepLength = 0.6;
            Sx += -Math.sin(avgOrientation) * stepLength;   //原坐标轴以东为正，现在坐标轴以西为正
            Sy += Math.cos(avgOrientation) * stepLength;
            int length = listOfOrientation.size();
            double[] location = {Sx, Sy, avgOrientation, staDev, length};
            locationBasedOnSensor.put(currentTime, location);
            listOfTime.add(0, currentTime);
            listOfOrientation.clear();
            return location;
        }
    }

    //求向量夹角的cos
    public static double getVectorAngle(double[] v1, double[] v2){
        double up = v1[0]*v2[0] + v1[1]*v2[1];
        double down1 = Math.sqrt(v1[0]*v1[0] + v1[1]*v1[1]);
        double down2 = Math.sqrt(v2[0]*v2[0] + v2[1]*v2[1]);
        double angle;
        try {
            angle = up / (down1*down2);
        }catch (ArithmeticException e){
            angle = -2.0;   //当某个向量为0向量时，特殊处理
        }
        return angle;
    }

    //寻找列表中与传入时间最接近的时间，返回这个时间对应的坐标。维持时间和位置列表的长度不至于过长
    public static double[] searchTimeList(long time, ArrayList<Long> listOfTime, String howToDealWithTimeList){
        long theDiff = 1000000;
        int j = 0;
        for(int i = 0; i<listOfTime.size(); i++){
            long diff = Math.abs(listOfTime.get(i) - time);
            if(diff <= theDiff){
                theDiff = diff;
                j = i;
            }else{
                j = i - 1;
                break;
            }
        }
        long timeQuery = listOfTime.get(j);
        long sensorLatestUpdateTime = listOfTime.get(0);    //时间列表最后更新的时间

        double[] hahh = {0.0,0.0};     //初始进入时，有可能传感器定位还没有值，此时给定值为0,0
        double[] testReturn = locationBasedOnSensor.get(timeQuery, hahh);
        String mark = "cutFromOldTime";                  //正常情况下，测试是否为对旧时间的query，如果是的话对list进行维持长度的操作。
        if (howToDealWithTimeList.equals(mark) && (listOfTime.size() - 1 != j)) {
            for (int k = listOfTime.size() - 1; k > j; k--) {
                locationBasedOnSensor.delete(listOfTime.get(k));
                listOfTime.remove(k);
            }
        }
        return testReturn;
    }

    //在locationListOfNearest中选择多数作为当前的位置。
    public static String filterLocation(ArrayList<String> locationListOfNearest, int LOCATION_NUM_LIMIT){
        String toReturn;
        if (locationListOfNearest.size() > LOCATION_NUM_LIMIT) {
            Map<String, Integer> locationCountMap = new HashMap<>();
            locationCountMap.put(locationListOfNearest.get(0), 1);
            for (int i = 1; i < locationListOfNearest.size(); i++) {
                String location = locationListOfNearest.get(i);
                if(locationCountMap.containsKey(location)){
                    locationCountMap.put(location, locationCountMap.get(location) + 1);
                }else {
                    locationCountMap.put(location,1);
                }
            }
            toReturn = sortLocationBasedOnCount(locationCountMap);
            locationListOfNearest.remove(locationListOfNearest.size() - 1);
        }else{
            toReturn = locationListOfNearest.get(0);   //在列表长度小于限制时，以列表头元素作为频率最高的值，
        }
        return  toReturn;
    }

    //返回map中对应最大值的键
    private static String sortLocationBasedOnCount(Map<String,Integer> locationCountMap){
        List<Map.Entry<String, Integer>> infoIds =
                new ArrayList<>(locationCountMap.entrySet());
        Collections.sort(infoIds, new Comparator<Map.Entry<String, Integer>>() {        //排序
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        return infoIds.get(0).getKey();
    }


    public static Quaternion getAccCompleted(float[] acc){
        Quaternion Q = new Quaternion();
        Q.value[0] = 0.0;
        Q.value[1] = acc[0];
        Q.value[2] = acc[1];
        Q.value[3] = acc[2];
        return Q;
    }

    //根据四元数转换方式，将加速度矢量转换到地理坐标系
    public static Double[] getConvertAcc(Quaternion p_1, Quaternion q) {
        Quaternion q_1p_1 = getQuaternionMulit(getQuaternionInverse(q), p_1);
        Quaternion p = getQuaternionMulit(q_1p_1, q);
        Double[] pDouble = new Double[3];
        pDouble[0] = p.value[1];
        pDouble[1] = p.value[2];
        pDouble[2] = p.value[3];
        return pDouble;
    }

    public static double[] filterAccValues(double[] accValues, SparseArray<ArrayList<Double>> accValueList) {
        for(int i = 0; i< accValues.length; i++) {
            //将得到的加速度值存储起来
            ArrayList<Double> valueList = accValueList.get(i);   //valueList 加速计一个轴的值的连续计数
            valueList.add(0, accValues[i]);
            if (valueList.size() > 10) {
                valueList.remove(10);   //维持长度小于10
            }
            accValues[i] = filterByBiasedNormalDistribution(valueList, i);
        }
        return accValues;
    }

    //主要用来获取加速计读数的零漂偏差值，在读数中去除
    private static double filterByBiasedNormalDistribution(ArrayList<Double> valueList, int i){
        Double avg = getAvg(valueList);
        Double staDev = getStaDev(valueList, avg, "normal");
        if (staDev < 0.3) {           //加速计读数比较稳定时，一般来说应该处于比较静止的状态,此时更新偏差值;读数在发生变化时，偏差值应该与静止时差不多，所以偏差值不做处理
            accBias[i] = avg;
            return 0.0;
        }
        accStaDev[i] = staDev;
        return  valueList.get(0) - accBias[i];    //测量值减去偏差值即为真实值
    }

    //过滤陀螺仪数据
    public static double[] filterGyroValue(float[] gyroValuesCopy) {
        double[] gyroValues = new double[gyroValuesCopy.length];
        for (int i = 0; i < gyroValuesCopy.length; i++) {
            gyroValues[i] = (Math.abs(gyroValuesCopy[i]) > 0.1) ? gyroValuesCopy[i] : 0;  //如果陀螺仪的值小于0.01则认为直接为0
        }
        return gyroValues;
    }



    //根据RSSI强度，对MAC地址排序
    public static ArrayList<String> sortNodeBasedOnRssi(Map<String, Double> mRssiFilterd, int BLE_CHOOSED_NUM) {
        List<Map.Entry<String, Double>> infoIds =
                new ArrayList<>(mRssiFilterd.entrySet());
        ArrayList<String> list = new ArrayList<>();
        int limit = BLE_CHOOSED_NUM < mRssiFilterd.size() ? BLE_CHOOSED_NUM : mRssiFilterd.size();

        Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {        //排序
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        for (int i = 0; i < limit; i++) {        //排序完,取前limit个
            String id = infoIds.get(i).toString();
            list.add(id.split("=")[0]);   //string.split后变为字符串数组。
        }
        return list;     //排序好的MAC地址的列表
    }

    //求ArrayLIst均值
    public static double getAvg(List list) {
        double sum = 0.0, avg = 0.0;
        if (list.size() != 0) {
            for (int i = 0; i < list.size(); i++) {
                sum += Double.valueOf(list.get(i).toString());
            }
            avg = sum / list.size();
        }
        return avg;
    }

    //求ArrayList标准差
    public static double getStaDev(ArrayList list, Double avg, String distribution) {
        double stadardDev = 0.0;
        if (list.size() > 1) {
            for (int i = 0; i < list.size(); i++) {
                stadardDev += Math.pow((Double.valueOf(list.get(i).toString()) - avg), 2);
            }
            if (distribution.equals("logarNormal"))
                stadardDev = Math.sqrt(stadardDev / list.size());
            else
                stadardDev = Math.sqrt(stadardDev / (list.size() - 1));
//            Log.d("staDev",stadardDev.toString());
        }
        return stadardDev;
    }

    //对数正态滤波
    public static List<Double> LogarNormalDistribution(List<Double> mAllRssilist, int RSSI_LIMIT) {
        ArrayList<Double> value = cutList(mAllRssilist, RSSI_LIMIT);

        Double avg, staDev, proLowLim, proHighLim, pdfAltered;  //rssiValue作为一个中间变量在多个计算过程中间使用

        ArrayList<Double> logarNormalList = GetLogarNormalList(value);   //转换成对数形式
        avg = getAvg(logarNormalList);   //求均值
        staDev = getStaDev(logarNormalList, avg, "logarNormal");  //求标准差

        if (staDev != 0) {
            proHighLim = Math.exp(0.5 * Math.pow(staDev, 2) - avg) / (staDev * Math.sqrt(2 * Math.PI));
            proLowLim = proHighLim * 0.6;
            for (int i = 0; i < logarNormalList.size(); i++) {          //去掉value中的低概率RSSI
                Double exponent = -Math.pow(logarNormalList.get(i) - avg, 2) / (2 * Math.pow(staDev, 2));
                pdfAltered = Math.exp(exponent) / ((0 - value.get(i)) * staDev * Math.sqrt(2 * Math.PI));
                if (pdfAltered < proLowLim || pdfAltered > proHighLim) {
                    logarNormalList.remove(i);                              //删除不在高概率区域内的数据
                    value.remove(i);            //未进行对数运算的原始数据中也进行对应的删除操作
                    i -= 1;
                }
            }
        }

        return value;
    }

    //对ArrayList每个值取对数，以应用于对数正态运算的函数
    private static ArrayList<Double> GetLogarNormalList(ArrayList<Double> list) {
        ArrayList<Double> list1 = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            list1.add(Math.log(0 - list.get(i)));
        }
        return list1;
    }

    //切割子list
    private static <T> ArrayList<T> cutList(List<T> list, int limit) {
        int trueLimit = limit < list.size() ? limit : list.size();
        ArrayList<T> returnList = new ArrayList<>();
        for (int i = 0; i < trueLimit; i++) {
            returnList.add(list.get(i));
        }
        return returnList;
    }

    //根据RSSI强度，得到最近的蓝牙节点
    public static Double[] getNearestNode(List<String> listSortedNode, Map<String, Double[]> bleNodeLoc) {
        Double[] location = new Double[2];
        String A = listSortedNode.get(0);
        location[0] = bleNodeLoc.get(A)[0];
        location[1] = bleNodeLoc.get(A)[1];
        return location;
    }

    //使用质心定位得到坐标
    public static Double[] getMassCenterLocation(ArrayList<String> listSortedNode, Map<String, Double[]> bleNodeLoc) {
        Double[] location = new Double[2];
        String A = listSortedNode.get(0), B = listSortedNode.get(1), C = listSortedNode.get(2);
        Double Ax = bleNodeLoc.get(A)[0], Ay = bleNodeLoc.get(A)[1], Bx = bleNodeLoc.get(B)[0], By = bleNodeLoc.get(B)[1], Cx = bleNodeLoc.get(C)[0], Cy = bleNodeLoc.get(C)[1];
        location[0] = 1.0 / 3 * ((Ax) + 0.5 * (Ax + Bx) + 0.5 * (Bx + Cx));
        location[1] = 1.0 / 3 * ((Ay) + 0.5 * (Ay + By) + 0.5 * (By + Cy));
        Log.d("listSortedNode", listSortedNode.toString());
        Log.d("location", Arrays.toString(location));
        return location;
    }


}


//    Double Sx = 0.0, Sy = 0.0, V0x = 0.0, V0y = 0.0, ax = 0.0, ay = 0.0;
//    //将加速度矢量转化到地理坐标系，计算行进路程，并且储存变化后的坐标。
//    public void tranformAndStoreAccValue(){
//        float[] p = new float[4];
//        SensorManager.getQuaternionFromVector(p, rotVecValues);  //由旋转矢量获得四元数
//        Quaternion pQuaternion = new Quaternion(p);
//        Double[] accConverted = getConvertAcc(getAccCompleted(accValues), pQuaternion);  //将加速度矢量转换到地理坐标系
//        accConverted = filterAccValues(accConverted, accValueList);
//        ax = -accConverted[0];
//        ay = accConverted[1];
//
//        Long currentMillisecond = Calendar.getInstance().getTimeInMillis();
//        double timeDiff = (currentMillisecond - listOfTime.get(0)) / 1000.0;
//
//        if((Math.abs(ax) == 0.0) && (Math.abs(ay) == 0.0)){
//            V0x = 0.0;
//            V0y = 0.0;
//        }else{
//            V0x = V0x + ax * timeDiff;
//            V0y = V0y + ay * timeDiff;
//        }
//
//        Sx += V0x * timeDiff + 0.5 * ax * timeDiff * timeDiff;   //计算时时间单位应该用秒
//        Sy += V0y * timeDiff + 0.5 * ay * timeDiff * timeDiff;
//
//        mTextAcc.setText(ax + "," + ay + "\n" + V0x + "," + V0y);
//        mTextGyro.setText(Sx + "\n" + Sy + "");
//
//        webView.loadUrl(setInsetJS(Sx + "", Sy + "","circle_point"));
//
//        double[] location = {Sx,Sy};
//        locationBasedOnSensor.put(currentMillisecond, location);
//
//        //以时间差不能超过6秒钟的条件维持map大小。
//        listOfTime.add(0,currentMillisecond);
//        long oldestTime = listOfTime.get(listOfTime.size()-1);
//        if((currentMillisecond - oldestTime) > 6000){
//            listOfTime.remove(oldestTime);
//            locationBasedOnSensor.delete(oldestTime);
//        }
//    }


//    //初始化需要加同步锁的变量
//    private void initSynchronize() {
//        mAllRssi = Collections.synchronizedMap(mAllRssi);
//        mRssiFilterd = Collections.synchronizedMap(mRssiFilterd);
//    }

//从MAP中选出 list中元素作为键，对应的键值对
//    public synchronized Map<String, List<Double>> getMapForStore(List<String> listSortedNode, Map<String, List<Double>> map) {
//        Map<String, List<Double>> mapReturn = new HashMap<>();
//        for (int i = 0; i < listSortedNode.size(); i++) {
//            String mac = listSortedNode.get(i);
//            List<Double> listRssi = map.get(mac);
//            mapReturn.put(mac, listRssi);
//        }
//        return mapReturn;
//    }


//加速计去除零漂,认为匀加速运动比较少，将匀加速和静止不动合并起来，都去掉
//    public float[] filterAccValues(float[] accValues) {
//        float[] accValueForReturn = new float[3];
//        for (int i = 0; i < accValues.length; i++) {
//            //将得到的加速度值存储起来
//            ArrayList<Float> valueList = accValueList.get(i);
//            valueList.add(0, accValues[i]);
//            if (valueList.size() > 15) {
//                valueList.remove(10);   //维持长度小于10
//            }
//            ArrayList<Float> percentileList = accValueList.get(i + 3);
//            valueList = cutList(valueList, ACC_LIMIT);
////            float percentile = Math.abs((valueList.get(1) - valueList.get(0)) / valueList.get(0));
//            float percentile = Math.abs(valueList.get(1) - valueList.get(0));
//            if (percentile > PERCENTILE_LIMIT) {   //当变化比例超过PERCENTILE_LIMIT时，认为是真实的变化。
//                percentileList.add(0, percentile);
//                accValueForReturn[i] = accValues[i];
//            }
//            else {
//                percentileList.add(0, 0.0f);
//                accValueForReturn[i] = 0.0f;
//            }
//        }
//        return accValueForReturn;
//    }

//                            String locationFilterdinMacTemp = filterLocation();
//                            if(!locationFilterdinMacTemp.equals(locationFilterdinMac)) {
//                                final Double[] locationFilterdInCoordinate = bleNodeLoc.get(locationFilterdinMac);
//                                webView.loadUrl(setInsetJS(locationFilterdInCoordinate[0] + "", locationFilterdInCoordinate[1] + ""));
//                                locationFilterdinMac = locationFilterdinMacTemp;
//                                final ArrayList testList = (ArrayList) locationListOfNearest.clone();
//                                try {
//                                    Calendar now = Calendar.getInstance();
//                                    Long timeInMillis = now.getTimeInMillis();
//                                    String need1 = (timeInMillis - anchorTime) + "  " + locationFilterdInCoordinate[0] + "  " + locationFilterdInCoordinate[1] + "\n" + changelocationListOfNearestToCoordinate(testList) + "\n\n";
//                                    FileCache.saveFile(need1);
//                                } catch (Exception e) {
//                                    e.printStackTrace();
//                                }


//                            Thread thread = new Thread(
//                                    new Runnable() {
//                                @Override
//                                public void run() {
//                                    try {
//                                        Calendar now = Calendar.getInstance();
//                                        Integer minute = now.get(Calendar.MINUTE);
//                                        Integer second = now.get(Calendar.SECOND);
//                                        Map<String, List<Double>> mapForStore = getMapForStore(listSortedNode, mAllRssi);
//                                        Map<String, List<Double>> mapForStore1 = getMapForStore(listSortedNode, mTest);
//                                        String need1 = "{" + location[0].toString() + "   " + location[1].toString() + "     "
//                                                + minute.toString() + ":" + second.toString() + "\n"
//                                                + mapForStore + "\n"
//                                                + mapForStore1 + "\n" + "}";
//                                        FileCache.saveFile(need1);
//                                    } catch (Exception e) {
//                                        e.printStackTrace();
//
//                                    }
//                                }
//                            });
//                            thread.start();
//                            }

//    Thread thread = new Thread(
//            new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        String need1 = time.toString() + "  " + bleNodeLoc.get(toReturn)[0].toString() + ","
//                                + bleNodeLoc.get(toReturn)[0].toString() + "\n" + locationListOfNearestClone + "\n";
//                        FileCache.saveFile(need1);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }
//            });
//        thread.start();


//    public static void changeForNewNode(ArrayList<Long>listOfTime, double[]locationNew, long currentTime){
//        listOfTime.add(0, currentTime);
//        Sx = locationNew[0];
//        Sy = locationNew[1];
//        locationBasedOnSensor.put(currentTime,locationNew);
//    }

//    public static ArrayList<String> changelocationListOfNearestToCoordinate(ArrayList<String> locationListOfNearest, Map<String, Double[]> bleNodeLoc){
//        ArrayList<String> toReturn = new ArrayList<>();
//        for (int i = 0; i < locationListOfNearest.size(); i++){
//            Double[] test = bleNodeLoc.get(locationListOfNearest.get(i));
//            toReturn.add(test[0].toString());
//            toReturn.add(test[1].toString());
//            toReturn.add(" ");
//        }
//        return toReturn;
//    }