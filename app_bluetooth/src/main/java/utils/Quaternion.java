package utils;
/*
 * Created by fanwe on 2017/5/4.
 */

public class Quaternion {
    public double value[] = new double[4];

    //加速度矢量补全为四元数
    public Quaternion(float[] values){
        value[0] = values[0];
        value[1] = values[1];
        value[2] = values[2];
        value[3] = values[3];
    }
    public Quaternion(){
    }

    //四元数乘法
    public static Quaternion getQuaternionMulit(Quaternion Q1, Quaternion Q2) {
        Quaternion Q3 = new Quaternion();
        Q3.value[0] = Q1.value[0] * Q2.value[0] - Q1.value[1] * Q2.value[1] - Q1.value[2] * Q2.value[2] - Q1.value[3] * Q2.value[3];
        Q3.value[1] = Q1.value[0] * Q2.value[1] + Q1.value[1] * Q2.value[0] + Q1.value[2] * Q2.value[3] - Q1.value[3] * Q2.value[2];
        Q3.value[2] = Q1.value[0] * Q2.value[2] + Q1.value[2] * Q2.value[0] + Q1.value[3] * Q2.value[1] - Q1.value[1] * Q2.value[3];
        Q3.value[3] = Q1.value[0] * Q2.value[3] + Q1.value[3] * Q2.value[0] + Q1.value[1] * Q2.value[2] - Q1.value[2] * Q2.value[1];
        return Q3;
    }

    //四元数取逆
    public static Quaternion getQuaternionInverse(Quaternion q) {
        Quaternion q_1 = new Quaternion();
        q_1.value[0] = q.value[0];
        q_1.value[1] = -q.value[1];
        q_1.value[2] = -q.value[2];
        q_1.value[3] = -q.value[3];
        return q_1;
    }
}
