package utils;
/*
 * Created by fanwe on 2017/5/4.
 */

import android.util.Log;
import android.util.SparseArray;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static utils.Quaternion.getQuaternionInverse;
import static utils.Quaternion.getQuaternionMulit;

public class MyUtils {
    static Double[] accBias = {0.0,0.0,0.0}, accStaDev =  {0.0,0.0,0.0};

    public static Quaternion getAccCompleted(float[] acc){
        Quaternion Q = new Quaternion();
        Q.value[0] = new BigDecimal(0.0);
        Q.value[1] = new BigDecimal(acc[0]);
        Q.value[2] = new BigDecimal(acc[1]);
        Q.value[3] = new BigDecimal(acc[2]);
        return Q;
    }

    //根据四元数转换方式，将加速度矢量转换到地理坐标系
    public static Double[] getConvertAcc(Quaternion p_1, Quaternion q) {
        Quaternion q_1p_1 = getQuaternionMulit(getQuaternionInverse(q), p_1);
        Quaternion p = getQuaternionMulit(q_1p_1, q);
        Double[] pDouble = new Double[3];
        pDouble[0] = Double.valueOf(String.valueOf(p.value[1]));
        pDouble[1] = Double.valueOf(String.valueOf(p.value[2]));
        pDouble[2] = Double.valueOf(String.valueOf(p.value[3]));
        return pDouble;
    }

    public static Double[] filterAccValues(Double[] accValues, SparseArray<ArrayList<Double>> accValueList) {
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
    public static double filterByBiasedNormalDistribution(ArrayList<Double> valueList, int i){
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
    public static float[] filterGyroValue(float[] gyroValues) {
        for (int i = 0; i < gyroValues.length; i++) {
            gyroValues[i] = (Math.abs(gyroValues[i]) > 0.1) ? gyroValues[i] : 0;  //如果陀螺仪的值小于0.01则认为直接为0
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
    public static Double getAvg(List list) {
        Double sum = 0.0, avg = 0.0;
        if (list.size() != 0) {
            for (int i = 0; i < list.size(); i++) {
                sum += Double.valueOf(list.get(i).toString());
            }
            avg = sum / list.size();
        }
        return avg;
    }

    //求ArrayList标准差
    public static Double getStaDev(ArrayList list, Double avg, String distribution) {
        Double stadardDev = 0.0;
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
    public static List<Double> LogarNormalDistribution(List<Double> mAllRssilist) {
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
    public static ArrayList<Double> GetLogarNormalList(ArrayList<Double> list) {
        ArrayList<Double> list1 = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            list1.add(Math.log(0 - list.get(i)));
        }
        return list1;
    }

    //切割子list
    public static ArrayList cutList(List list, int limit) {
        int trueLimit = limit < list.size() ? limit : list.size();
        ArrayList returnList = new ArrayList();
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
