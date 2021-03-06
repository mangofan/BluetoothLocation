package utils;
/*
 * Created by fanwe on 2017/5/4.
 */

import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.util.SparseArray;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.math.BigDecimal.ROUND_HALF_UP;

public class MyUtils {
    private static long lastTimeOfSensorFlag = 0;
    public static int MOVING = 0, STANDING = 1;
    private static double Sx = 14.0, Sy = 0.0;


//    private static Double[] accBias = {0.0,0.0,0.0};
    private static LongSparseArray<double[]> locationBasedOnSensor = new LongSparseArray<>();
    private static ArrayList<String> locationListForStandx = new ArrayList<>();
    private static ArrayList<String> locationListForStandy = new ArrayList<>();
    private static ArrayList<Long> timeStepList = new ArrayList<>();

    // 计步发生3秒以上时，对维护的位置列表求平均
    public static Map<String,ArrayList<String>> getStandLocation(){
        cutListAndDelete(locationListForStandx, 50);
        cutListAndDelete(locationListForStandy, 50);
        double locationx = getAvgBigDecimal(locationListForStandx);
        double locationy = getAvgBigDecimal(locationListForStandy);
        Map<String, ArrayList<String>> toReturn = new HashMap<>();
        ArrayList<String> massCenter = new ArrayList<>();
        massCenter.add(String.valueOf(locationx));
        massCenter.add(String.valueOf(locationy));
        toReturn.put("massCenter", massCenter);
        toReturn.put("x", locationListForStandx);
        toReturn.put("y", locationListForStandy);
        return toReturn;
    }

    // 根据计步器，区分现在是在移动还是已经停止不动；维护一个列表，存储计步发生后所有的位置，当出现新的计步之后，表清空，重新维护，为了静止时可以对发生的位置求平均。
    public static long getSensorState(long nowTime, List<Long> listTimeOfSensor, String newLocation){
        String[] location = newLocation.split(",");
        Log.w("newLocation", newLocation);
        locationListForStandx.add(0,location[0]);
        locationListForStandy.add(0,location[1]);

        long nearestSensorTime = searchTimeList(nowTime, listTimeOfSensor);
        Log.w("time differ", (nearestSensorTime-nowTime) + "");
        long toReturn;
        if((nowTime - nearestSensorTime) > 1000){   //如果当前时刻与计步器最后更新的步数的时刻相差3秒以上，认为这段时间没有移动
            toReturn = nowTime - nearestSensorTime;
        }else{
            toReturn =  MOVING;
        }
        return toReturn;
    }

    private static long searchTimeList(long time, List<Long> list){
        long toReturn = list.get(list.size()-1);   //这个初始化是为了用于time大于列表中任意时间时
        for(int i=0; i<list.size(); i++){
            if(list.get(i) > time){
                toReturn = list.get(i-1);    //因为最初初始化时，初始化了传感器最初时间，后来的蓝牙信号时间绝对不会早于应用初始化时间，也就是list.get(0)<time必然成立
                break;
            }
        }
        return toReturn;
    }

    public static String findTheLoc(int i, int end, LongSparseArray<String>map){
        //对这一秒内出现的节点进行计数，取最大。
        int flag = 0;
        String locationStringFinal = map.valueAt(i);
        Map<String, Integer> map1 = new HashMap<>();
        for (int j = i; j < end; j++) {
            String location = map.valueAt(j);
            if (map1.containsKey(location)) {
                Integer count = map1.get(location);
                count = count + 1;
                map1.put(location, count);
            } else {
                map1.put(location, 0);
            }
        }
        for (String loc : map1.keySet()) {
            int locNum = map1.get(loc);
            if (locNum > flag) {
                flag = locNum;
                locationStringFinal = loc;
            }
        }
        return locationStringFinal;
    }

    //使用质心定位得到坐标,使用BigDecimal来减少将6.7表示为6.6999999999的情况
    public static String getMassCenter(SparseArray<ArrayList<String>> SortedNodeMacAndRssi, Map<String, String> bleNodeLoc) {
        double varianceLimit = 27;   //对于方差的限制
        int flag;
        ArrayList<String> SortedNodeMacList = SortedNodeMacAndRssi.get(1);   //获取排好序的节点的MAC地址的列表
        ArrayList<String> SortedNodeRssiList = SortedNodeMacAndRssi.get(2);  //获取排好序的节点的RSSI地址的列表
        int size = SortedNodeRssiList.size();
        double[] massCenter = new double[2];
        massCenter[0] = 0.0;
        massCenter[1] = 0.0;

        ArrayList<double[]> nearestMacLoc = new ArrayList<>();  //将排好序的节点的坐标存起来
        for(int i = 0; i < size; i++){
            String[] location = bleNodeLoc.get(SortedNodeMacList.get(i)).split(",");
            double[] loc = new double[2];
            loc[0] = Double.valueOf(location[0]);
            loc[1] = Double.valueOf(location[1]);
            nearestMacLoc.add(loc);
        }

        if(size > 2){
            flag = judgeIfLine(nearestMacLoc);
        }else
            return nearestMacLoc.get(0)[0] + "," + nearestMacLoc.get(0)[1] + ":" +0;

        if (flag == 1){   //flag为1时，说明共线，为0时说明不共线
            massCenter[0] = (nearestMacLoc.get(0)[0] + nearestMacLoc.get(1)[0]) / 2;
            massCenter[1] = (nearestMacLoc.get(0)[1] + nearestMacLoc.get(1)[1]) / 2;
            return massCenter[0] + "," + massCenter[1] + ":" + "line";
        }else{
            double variance = getVariance(SortedNodeRssiList,"not sure");
            if (variance < varianceLimit) {
                for (int j = 0; j < size; j++) {
                    double[] loc = nearestMacLoc.get(j);
                    massCenter[0] = massCenter[0] + loc[0];
                    massCenter[1] = massCenter[1] + loc[1];
                }
                BigDecimal massCenter0 = new BigDecimal(massCenter[0]).divide(new BigDecimal(size), 1, ROUND_HALF_UP);
                BigDecimal massCenter1 = new BigDecimal(massCenter[1]).divide(new BigDecimal(size), 1, ROUND_HALF_UP);
                return massCenter0 + "," + massCenter1 + ":" + variance;
            }else{
                return bleNodeLoc.get(SortedNodeMacList.get(0)) + ":" + variance;
            }
        }
    }

    private static int judgeIfLine(ArrayList<double[]> nearestMacLoc){
        double[] vec1 = new double[2];
        double[] vec2 = new double[2];
        vec1[0] = nearestMacLoc.get(0)[0] - nearestMacLoc.get(1)[0];
        vec1[1] = nearestMacLoc.get(0)[1] - nearestMacLoc.get(1)[1];
        vec2[0] = nearestMacLoc.get(0)[0] - nearestMacLoc.get(2)[0];
        vec2[1] = nearestMacLoc.get(0)[1] - nearestMacLoc.get(2)[1];
        double cos = (vec1[0] * vec2[0] + vec1[1] * vec2[1]) / (Math.sqrt(vec1[0]*vec1[0] + vec1[1]*vec1[1]) * Math.sqrt(vec2[0]*vec2[0] + vec2[1]*vec2[1]));
        if((0.9 < cos) & (cos< 1.1))    //共线
            return 1;
        else
            return 0;   //不共线
    }


    public static int searchTimeMap(LongSparseArray map, long time){
        int j = 0;
        for(int i = 0; i < map.size(); i++){
            if(map.keyAt(i) < time){
                j = i;
            }else{
                break;
            }
        }
        return j;
    }

    //根据RSSI强度，对MAC地址排序
    public static SparseArray<ArrayList<String>> sortNodeBasedOnRssi(Map<String, Double> rssiAvg, int BLE_CHOOSED_NUM) {
        List<Map.Entry<String, Double>> infoIds = new ArrayList<>(rssiAvg.entrySet());
        ArrayList<String> listOfMac = new ArrayList<>();
        ArrayList<String> listOfRssi = new ArrayList<>();
        ArrayList<String> listOfMacAndRssi = new ArrayList<>();
        String MacAndRssi = "";
        int limit = BLE_CHOOSED_NUM < rssiAvg.size() ? BLE_CHOOSED_NUM : rssiAvg.size();

        Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {        //排序
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        for (int i = 0; i < limit; i++) {        //排序完,取前limit个
            String[] id = infoIds.get(i).toString().split("=");   //string.split后变为字符串数组。
            listOfMac.add(id[0]);
            listOfRssi.add(id[1]);
            MacAndRssi += id[0].split(":")[5] + "," + id[1] + "\n";
        }
        SparseArray<ArrayList<String>> toReturn = new SparseArray<>();
        toReturn.put(1, listOfMac);
        toReturn.put(2, listOfRssi);
        listOfMacAndRssi.add(MacAndRssi);
        toReturn.put(3, listOfMacAndRssi);
        return toReturn;     //排序好的MAC地址的列表
    }

    //求ArrayLIst均值
    private static double getAvg(List list) {
        double sum = 0.0;
        double avg = 0.0;
        if (list.size() != 0) {
            for (int i = 0; i < list.size(); i++) {
                sum += Double.valueOf(list.get(i).toString());
            }
            avg = sum / list.size();
        }
        return avg;
    }


    //求ArrayLIst均值
    private static double getAvgBigDecimal(List list) {
        double sum = 0.0, avg = 0.0;
        if (list.size() != 0) {
            for (int i = 0; i < list.size(); i++) {
                sum = sum + Double.valueOf(list.get(i).toString());
            }
            BigDecimal bigDecimalavg = new BigDecimal(sum).divide(new BigDecimal(list.size()),1, ROUND_HALF_UP);
            avg = bigDecimalavg.doubleValue();
        }

        return avg;
    }

    //求ArrayList标准差
    private static double getStaDev(ArrayList list, Double avg) {
        double stadardDev = 0.0;
        if (list.size() > 1) {
            for (int i = 0; i < list.size(); i++) {
                stadardDev += Math.pow((Double.valueOf(list.get(i).toString()) - avg), 2);
            }
            stadardDev = Math.sqrt(stadardDev / list.size());
        }
        return stadardDev;
    }

    //求ArrayList方差
    private static double getVariance(ArrayList list, String distribution) {
        double avg = getAvg(list);
        double variance = 0.0;
        if (list.size() > 1) {
            for (int i = 0; i < list.size(); i++) {
                variance += Math.pow((Double.valueOf(list.get(i).toString()) - avg), 2);
            }
            if (distribution.equals("logarNormal"))
                variance = variance / list.size();
            else
                variance = new BigDecimal(variance).divide(new BigDecimal(list.size()-1),1,ROUND_HALF_UP).doubleValue();
        }
        return variance;
    }


    //对数正态滤波
    public static double LogarNormalDistribution(ArrayList<Double> mAllRssilist, int RSSI_LIMIT) {
        cutListAndDelete(mAllRssilist, RSSI_LIMIT);  //按照limit切割子list，并删除切割后剩余的值

        Double avg, staDev, proLowLim, proHighLim, pdfAltered;  //rssiValue作为一个中间变量在多个计算过程中间使用

        ArrayList<Double> logarNormalList = GetLogarNormalList(mAllRssilist);   //转换成对数形式
        avg = getAvg(logarNormalList);   //求均值
        staDev = getStaDev(logarNormalList, avg);  //求标准差
        ArrayList<Double> dataToGetAvg = new ArrayList<>();
        if (staDev != 0) {    //取值的标准差不为零时，去除低概率值，再计算平均值
            proHighLim = Math.exp(0.5 * Math.pow(staDev, 2) - avg) / (staDev * Math.sqrt(2 * Math.PI));
            proLowLim = proHighLim * 0.6;
            double denominatorOfExponent = 2 * Math.pow(staDev, 2);  //提前计算exponent的分母，因为历次分母相同，避免重复计算
            double partDenominatorOfPdfAltered = staDev * Math.sqrt(2 * Math.PI);   //提前计算pdfAltered的一部分分母，也是为了避免重复计算

            for (int i = 0; i < logarNormalList.size(); i++) {          //去掉value中的低概率RSSI，并且计算剩余的平均值。
                double exponent = -Math.pow(logarNormalList.get(i) - avg, 2) / denominatorOfExponent;
                pdfAltered = Math.exp(exponent) / ((0 - mAllRssilist.get(i)) * partDenominatorOfPdfAltered);
                if (pdfAltered > proLowLim && pdfAltered < proHighLim) {   //筛选在高概率区域内的数据
                    dataToGetAvg.add(mAllRssilist.get(i));
                }
            }
            return getAvgBigDecimal(dataToGetAvg);
        }else             //取值的标准差为零时，直接返回平均值
            return mAllRssilist.get(0);
    }

    //对ArrayList每个值取对数，以应用于对数正态运算的函数
    private static ArrayList<Double> GetLogarNormalList(ArrayList<Double> list) {
        ArrayList<Double> list1 = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            list1.add(Math.log(0 - list.get(i)));
        }
        return list1;
    }

    //切割子list，并且去掉之后需要的值之后的值
    private static <T> void cutListAndDelete(List<T> list, int limit){
        if(list.size() > limit){
            for(int i = list.size()-1; i > limit-1; i--){
                list.remove(i);
            }
        }
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

//    //使用质心定位得到坐标,使用BigDecimal来减少将6.7表示为6.6999999999的情况
//    public static String getMassCenter(SparseArray<ArrayList<String>> SortedNodeMacAndRssi, Map<String, String> bleNodeLoc) {
//        double varianceLimit = 1;   //对于方差的限制
//        ArrayList<String> SortedNodeMacList = SortedNodeMacAndRssi.get(1);   //获取排好序的节点的MAC地址的列表
//        ArrayList<String> SortedNodeRssiList = SortedNodeMacAndRssi.get(2);  //获取排好序的节点的RSSI地址的列表
//
//        BigDecimal[] massCenter = new BigDecimal[2];
//        massCenter[0] = new BigDecimal("0.0");
//        massCenter[1] = new BigDecimal("0.0");
//        for (int i = SortedNodeMacList.size(); i > 0; i--) {    //从多到少，分别计算方差，方差小于某个值时，认为这几个值相近，求这几个值的质心
//            ArrayList<String> rssiList = cutList(SortedNodeRssiList, i);
//            ArrayList<String> macList = cutList(SortedNodeMacList, i);
//            double variance = getVariance(rssiList, getAvg(rssiList), "not sure");
//            if (variance < varianceLimit) {
//                for (int j = 0; j < i; j++) {
//                    BigDecimal[] node = new BigDecimal[2];
//                    String[] location = bleNodeLoc.get(macList.get(j)).split(",");
//                    node[0] = new BigDecimal(location[0]);
//                    node[1] = new BigDecimal(location[1]);
//                    massCenter[0] = massCenter[0].add(node[0]);
//                    massCenter[1] = massCenter[1].add(node[1]);
//                }
//                massCenter[0] = massCenter[0].divide(new BigDecimal(String.valueOf(i)), 1, ROUND_HALF_UP);
//                massCenter[1] = massCenter[1].divide(new BigDecimal(String.valueOf(i)), 1, ROUND_HALF_UP);
//                break;
//            }
//        }
//        return massCenter[0] + "," + massCenter[1];
//    }


    //    public static double[] searchTimeListForRecentConfirmMap(LongSparseArray<String> recentLocationMapRaw) {
//        int TARGET_TIME = 1500, TIME_INTERVAL = 1000;
//        int indexOfOldTimeEnd = 0;
//        double[] toReturn = new double[2];
//        long lastTime = recentLocationMapRaw.keyAt(recentLocationMapRaw.size() - 1);   //得到本次插入的时间
//        long targetTime = lastTime - TARGET_TIME;  //求出目标时间，常数值标志现在时间与目标时间的差
//        double oldTimeEnd = targetTime + 0.5 * TIME_INTERVAL;  //所要搜寻的时间范围的下界，常数值标志在targetTIme两侧总的搜寻时间的范围。
//        double oldTimeStart = targetTime - 0.5 * TIME_INTERVAL;    //搜寻的时间范围的上界，常数值标志在targetTIme两侧总的搜寻时间的范围。
//        Map<String, Integer> locationMap = new HashMap<>();
//        int lengthOfMap = recentLocationMapRaw.size();
//
//        for (int i = 0; i < lengthOfMap; i++) {
//            if (recentLocationMapRaw.keyAt(i) < oldTimeStart) {  //查找到搜索的时间段的上界的index,并把上界之前的值都删除，这样上界肯定是0
//                recentLocationMapRaw.remove(i);
//                i -= 1;
//                lengthOfMap -= 1;
//            } else if (recentLocationMapRaw.keyAt(i) < oldTimeEnd) {
//                String locationThisTime = recentLocationMapRaw.valueAt(i);
//                if(locationMap.containsKey(locationThisTime)){
//                    int lastTimeOfThisLocation = locationMap.get(locationThisTime);
//                    if(lastTimeOfThisLocation != (i-1)){     //当两个相同位置（A）在时间序列上出现的位置不相邻时
//                        for(int j = lastTimeOfThisLocation + 1; j < i; j++){
//                            recentLocationMapRaw.setValueAt(j, locationThisTime);   //将两次A之间的所有元素修改为A
//                        }
//                    }else{
//                        locationMap.put(locationThisTime, i);   //两次A相邻时，更新locationMap
//                    }
//                }else{            //当locationMap没出现过这个元素时，在locationMap中添加。
//                    locationMap.put(recentLocationMapRaw.valueAt(i), i);
//                }
//                indexOfOldTimeEnd = i;   //得知所求时间段的index为从0到indexOfOldTimeEnd
//            }else{
//                break;
//            }
//        }
//
//
//        return toReturn;
//    }

    //    //当获得角度大于零时，传入的新坐标点是对的；小于零也包括传感器提示没有行动时，返回传入的旧坐标点
//    //超过2秒没有发生步伐，认为是静止，传感器数据不再使用，直接采用蓝牙的结果；多于4秒没有发生变化，采用最后一次蓝牙的结果，以避免无尽的跳来跳去;少于2秒时，仍使用传感器结果和蓝牙进行比对确定位置。
//    public static String getSensorConfirm(String locationOnBluetoothOldString, String locationOnBluetoothNewString, long timeLast,long currentMillisecond, ArrayList<Long> listOfTimeSensor){
//        long sensorLatestUpdateTime = listOfTimeSensor.get(0);    //时间列表最后更新的时间
//        long timeDiff = currentMillisecond - sensorLatestUpdateTime;
//        double[] locationOnBluetoothOld = new double[2];
//        locationOnBluetoothOld[0] = Double.valueOf(locationOnBluetoothOldString.split(",")[0]);
//        locationOnBluetoothOld[1] = Double.valueOf(locationOnBluetoothOldString.split(",")[1]);
//        double[] locationOnBluetoothNew = new double[2];
//        locationOnBluetoothNew[0] = Double.valueOf(locationOnBluetoothNewString.split(",")[0]);
//        locationOnBluetoothNew[1] = Double.valueOf(locationOnBluetoothNewString.split(",")[1]);
//        if(timeDiff < 2000) {
//            double[] vectorBasedOnBluetooth = {locationOnBluetoothNew[0] - locationOnBluetoothOld[0], locationOnBluetoothNew[1] - locationOnBluetoothOld[1]};
//            double[] locationOldSensor = searchTimeListForSensor(timeLast, listOfTimeSensor, "cutFromOldTime");
//            double[] locationNewSensor = searchTimeListForSensor(currentMillisecond, listOfTimeSensor, "dontCut");
//            double[] vectorBasedOnSensor = {locationNewSensor[0] - locationOldSensor[0], locationNewSensor[1] - locationOldSensor[1]};
//
//            double angle = MyUtils.getVectorAngle(vectorBasedOnBluetooth, vectorBasedOnSensor);
//            double[] toReturn;
//            if (angle > 0) {
//                toReturn = locationOnBluetoothNew;
//            } else {
//                toReturn = locationOnBluetoothOld;
//            }
//            return toReturn[0] + "," + toReturn[1];
//
//        }else if(timeDiff > 4000){
//            return locationOnBluetoothOld[0] + "," + locationOnBluetoothOld[1];
//        }else{
//            return locationOnBluetoothNew[0] + "," + locationOnBluetoothNew[1];
//        }
//    }

//    public static double getAngleFixed(float angle, double angleBiased){
//        double angleBiasedInR = angleBiased * Math.PI / 180;
//        double angleFixed = angle - angleBiasedInR;
//        if(angleFixed < - Math.PI){
//            return angleFixed + 2 * Math.PI;
//        }else if(angleFixed > Math.PI){
//            return angleFixed - 2 * Math.PI;
//        }else
//            return angleFixed;
//    }

//    private static void positiveOrientationList(ArrayList<Double> listOfOrientation){
//        for(int i = 0; i < listOfOrientation.size(); i++){
//            double value = listOfOrientation.get(i);
//            if(value < 0) {
//                value += 2 * Math.PI;
//                listOfOrientation.set(i,value);
//            }
//        }
//    }

//    //每步产生时记录位置，记录时间，为了以后的查询中使用
//    public static double[] makeOneStepProcess(ArrayList<Double> listOfOrientation, ArrayList<Long>listOfTimeSensor){
//        if(listOfOrientation.size() == 0)
//            return new double[]{Sx,Sy};
//        else {
//            long currentTime = Calendar.getInstance().getTimeInMillis();
//            double staDevOOrientation = getStaDev(listOfOrientation, getAvg(listOfOrientation), "not sure");
//            if (staDevOOrientation > 1.5) {       //方差过大时，认为方向应该为朝向南，处于正负180度之间造成的平均值出现误差，处理方法是将所有负数值加2π。
//                positiveOrientationList(listOfOrientation);
//            }
//            double avgOrientation = getAvg(listOfOrientation);
//            double staDev = getStaDev(listOfOrientation, avgOrientation, "not sure");
//            double stepLength = 0.6;  //假定步长是0.6
//            Sx += -Math.sin(avgOrientation) * stepLength;   //原坐标轴以东为正，现在坐标轴以西为正
//            Sy += Math.cos(avgOrientation) * stepLength;
//            int length = listOfOrientation.size();
//            double[] location = {Sx, Sy, avgOrientation, staDev, length};
//            locationBasedOnSensor.put(currentTime, location);
//            listOfTimeSensor.add(0, currentTime);
//            listOfOrientation.clear();
//            return location;
//        }
//    }

//    //求向量夹角的cos
//    private static double getVectorAngle(double[] v1, double[] v2){
//        double up = v1[0]*v2[0] + v1[1]*v2[1];
//        double down1 = Math.sqrt(v1[0]*v1[0] + v1[1]*v1[1]);
//        double down2 = Math.sqrt(v2[0]*v2[0] + v2[1]*v2[1]);
//        double angle;
//        try {
//            angle = up / (down1*down2);
//        }catch (ArithmeticException e){
//            angle = -2.0;   //当某个向量为0向量时，特殊处理
//        }
//        return angle;
//    }
//
//    //寻找列表中与传入时间最接近的时间，返回这个时间对应的坐标。维持时间和位置列表的长度不至于过长
//    private static double[] searchTimeListForSensor(long time, ArrayList<Long> listOfTimeSensor, String howToDealWithTimeList){
//        long theDiff = 1000000;
//        int j = 0;
//        for(int i = 0; i<listOfTimeSensor.size(); i++){
//            long diff = Math.abs(listOfTimeSensor.get(i) - time);
//            if(diff <= theDiff){
//                theDiff = diff;
//                j = i;
//            }else{
//                j = i - 1;
//                break;
//            }
//        }
//        long timeQuery = listOfTimeSensor.get(j);   //获得查找到的时间
//
//        double[] hahh = {14.0,0.0};     //初始进入时，有可能传感器定位还没有值，此时给定值为14.0,0.0
//        double[] testReturn = locationBasedOnSensor.get(timeQuery, hahh);   //如果查找的时间不存在值，则返回（14.0,0.0），认为是初始进入时没有值时的情况
//        String mark = "cutFromOldTime";                  //正常情况下，测试是否为对旧时间的query，如果是的话对list进行维持长度的操作。
//        if (howToDealWithTimeList.equals(mark) && (listOfTimeSensor.size() - 1 != j)) {
//            for (int k = listOfTimeSensor.size() - 1; k > j; k--) {
//                locationBasedOnSensor.delete(listOfTimeSensor.get(k));
//                listOfTimeSensor.remove(k);
//            }
//        }
//        return testReturn;
//    }

//    //在locationListOfNearest中选择多数作为当前的位置。
//    public static String filterLocation(ArrayList<String> locationListOfNearest, int LOCATION_NUM_LIMIT){
//        String toReturn;
//        if (locationListOfNearest.size() > LOCATION_NUM_LIMIT) {
//            Map<String, Integer> locationCountMap = new HashMap<>();
//            locationCountMap.put(locationListOfNearest.get(0), 1);
//            for (int i = 1; i < locationListOfNearest.size(); i++) {
//                String location = locationListOfNearest.get(i);
//                if(locationCountMap.containsKey(location)){
//                    locationCountMap.put(location, locationCountMap.get(location) + 1);
//                }else {
//                    locationCountMap.put(location,1);
//                }
//            }
//            toReturn = sortLocationBasedOnCount(locationCountMap);
//            locationListOfNearest.remove(locationListOfNearest.size() - 1);
//        }else{
//            toReturn = locationListOfNearest.get(0);   //在列表长度小于限制时，以列表头元素作为频率最高的值，
//        }
//        return  toReturn;
//    }

//    //返回map中对应最大值的键
//    private static String sortLocationBasedOnCount(Map<String,Integer> locationCountMap){
//        List<Map.Entry<String, Integer>> infoIds =
//                new ArrayList<>(locationCountMap.entrySet());
//        Collections.sort(infoIds, new Comparator<Map.Entry<String, Integer>>() {        //排序
//            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
//                return o2.getValue().compareTo(o1.getValue());
//            }
//        });
//        return infoIds.get(0).getKey();
//    }


//    public static Quaternion getAccCompleted(float[] acc){
//        Quaternion Q = new Quaternion();
//        Q.value[0] = 0.0;
//        Q.value[1] = acc[0];
//        Q.value[2] = acc[1];
//        Q.value[3] = acc[2];
//        return Q;
//    }

//    //根据四元数转换方式，将加速度矢量转换到地理坐标系
//    public static Double[] getConvertAcc(Quaternion p_1, Quaternion q) {
//        Quaternion q_1p_1 = getQuaternionMulit(getQuaternionInverse(q), p_1);
//        Quaternion p = getQuaternionMulit(q_1p_1, q);
//        Double[] pDouble = new Double[3];
//        pDouble[0] = p.value[1];
//        pDouble[1] = p.value[2];
//        pDouble[2] = p.value[3];
//        return pDouble;
//    }

//    public static double[] filterAccValues(double[] accValues, SparseArray<ArrayList<Double>> accValueList) {
//        for(int i = 0; i< accValues.length; i++) {
//            //将得到的加速度值存储起来
//            ArrayList<Double> valueList = accValueList.get(i);   //valueList 加速计一个轴的值的连续计数
//            valueList.add(0, accValues[i]);
//            if (valueList.size() > 10) {
//                valueList.remove(10);   //维持长度小于10
//            }
//            accValues[i] = filterByBiasedNormalDistribution(valueList, i);
//        }
//        return accValues;
//    }

//    //主要用来获取加速计读数的零漂偏差值，在读数中去除
//    private static double filterByBiasedNormalDistribution(ArrayList<Double> valueList, int i){
//        Double avg = getAvg(valueList);
//        Double staDev = getStaDev(valueList, avg, "normal");
//        if (staDev < 0.3) {           //加速计读数比较稳定时，一般来说应该处于比较静止的状态,此时更新偏差值;读数在发生变化时，偏差值应该与静止时差不多，所以偏差值不做处理
//            accBias[i] = avg;
//            return 0.0;
//        }
//        accStaDev[i] = staDev;
//        return  valueList.get(0) - accBias[i];    //测量值减去偏差值即为真实值
//    }

//    //过滤陀螺仪数据
//    public static double[] filterGyroValue(float[] gyroValuesCopy) {
//        double[] gyroValues = new double[gyroValuesCopy.length];
//        for (int i = 0; i < gyroValuesCopy.length; i++) {
//            gyroValues[i] = (Math.abs(gyroValuesCopy[i]) > 0.1) ? gyroValuesCopy[i] : 0;  //如果陀螺仪的值小于0.01则认为直接为0
//        }
//        return gyroValues;
//    }


//    //根据RSSI强度，得到最近的蓝牙节点
//    public static Double[] getNearestNode(List<String> listSortedNode, Map<String, Double[]> bleNodeLoc) {
//        Double[] location = new Double[2];
//        String A = listSortedNode.get(0);
//        location[0] = bleNodeLoc.get(A)[0];
//        location[1] = bleNodeLoc.get(A)[1];
//        return location;
//    }




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
//        double timeDiff = (currentMillisecond - listOfTimeSensor.get(0)) / 1000.0;
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
//        listOfTimeSensor.add(0,currentMillisecond);
//        long oldestTime = listOfTimeSensor.get(listOfTimeSensor.size()-1);
//        if((currentMillisecond - oldestTime) > 6000){
//            listOfTimeSensor.remove(oldestTime);
//            locationBasedOnSensor.delete(oldestTime);
//        }
//    }


//    //初始化需要加同步锁的变量
//    private void initSynchronize() {
//        mAllRssi = Collections.synchronizedMap(mAllRssi);
//        rssiAvg = Collections.synchronizedMap(rssiAvg);
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


//    public static void changeForNewNode(ArrayList<Long>listOfTimeSensor, double[]locationNew, long currentTime){
//        listOfTimeSensor.add(0, currentTime);
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

//三个点存在强度排序，然后计算三角形质心
//    String A = listSortedNode.get(0), B = listSortedNode.get(1), C = listSortedNode.get(2);
//    Double Ax = bleNodeLoc.get(A)[0], Ay = bleNodeLoc.get(A)[1], Bx = bleNodeLoc.get(B)[0], By = bleNodeLoc.get(B)[1], Cx = bleNodeLoc.get(C)[0], Cy = bleNodeLoc.get(C)[1];
//        location[0] = 1.0 / 3 * ((Ax) + 0.5 * (Ax + Bx) + 0.5 * (Bx + Cx));
//                location[1] = 1.0 / 3 * ((Ay) + 0.5 * (Ay + By) + 0.5 * (By + Cy));


//设置每隔TIME0更新UI
//        Timer updateTimer = new Timer("Update");
//        updateTimer.scheduleAtFixedRate(new TimerTask() {
//            @Override
//            public void run() {
//                updateGUI();
//            }
//        }, 0, TIME0);

//    //UI 更新方法
//    private void updateGUI() {
//        getActivity().runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//            }
//        });
//    }

//    //每步产生时记录位置，记录时间，为了以后的查询中使用
//    public static double[] makeOneStepProcess(ArrayList<Double> listOfOrientation, ArrayList<Long>listOfTimeSensor){
//        if(listOfOrientation.size() == 0)
//            return new double[]{Sx,Sy};
//        else {
//            long currentTime = Calendar.getInstance().getTimeInMillis();
//            double staDevOOrientation = getStaDev(listOfOrientation, getAvg(listOfOrientation), "not sure");
//            if (staDevOOrientation > 1.5) {       //方差过大时，认为方向应该为朝向南，处于正负180度之间造成的平均值出现误差，处理方法是将所有负数值加2π。
//                positiveOrientationList(listOfOrientation);
//            }
//            double avgOrientation = getAvg(listOfOrientation);
//            double staDev = getStaDev(listOfOrientation, avgOrientation, "not sure");
//            double stepLength = 0.6;  //假定步长是0.6
//            Sx += -Math.sin(avgOrientation) * stepLength;   //原坐标轴以东为正，现在坐标轴以西为正
//            Sy += Math.cos(avgOrientation) * stepLength;
//            int length = listOfOrientation.size();
//            double[] location = {Sx, Sy, avgOrientation, staDev, length};
//            locationBasedOnSensor.put(currentTime, location);
//            listOfTimeSensor.add(0, currentTime);
//            listOfOrientation.clear();
//            return location;
//        }
//    }