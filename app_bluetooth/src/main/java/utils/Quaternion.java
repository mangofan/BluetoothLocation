package utils;
/*
.multiply(Created by fanwe on 2017/5/4.
 */

import java.math.BigDecimal;

public class Quaternion {
    public BigDecimal value[] = new BigDecimal[4];

    public Quaternion(float[] values){
        value[0] = new BigDecimal(values[0]);
        value[1] = new BigDecimal(values[1]);
        value[2] = new BigDecimal(values[2]);
        value[3] = new BigDecimal(values[3]);
    }
    public Quaternion(){
    }



    //四元数乘法
    public static Quaternion getQuaternionMulit(Quaternion Q1, Quaternion Q2) {
        Quaternion Q3 = new Quaternion();
        Q3.value[0] = Q1.value[0].multiply(Q2.value[0]).subtract(Q1.value[1].multiply(Q2.value[1])).subtract(Q1.value[2].multiply(Q2.value[2])).subtract(Q1.value[3].multiply(Q2.value[3]));
        Q3.value[1] = Q1.value[0].multiply(Q2.value[1]).add(Q1.value[1].multiply(Q2.value[0])).add(Q1.value[2].multiply(Q2.value[3])).subtract(Q1.value[3].multiply(Q2.value[2]));
        Q3.value[2] = Q1.value[0].multiply(Q2.value[2]).add(Q1.value[2].multiply(Q2.value[0])).add(Q1.value[3].multiply(Q2.value[1])).subtract(Q1.value[1].multiply(Q2.value[3]));
        Q3.value[3] = Q1.value[0].multiply(Q2.value[3]).add(Q1.value[3].multiply(Q2.value[0])).add(Q1.value[1].multiply(Q2.value[2])).subtract(Q1.value[2].multiply(Q2.value[1]));
        return Q3;
    }

    //四元数取逆
    public static Quaternion getQuaternionInverse(Quaternion q) {
        Quaternion q_1 = new Quaternion();
        q_1.value[0] = q.value[0];
        q_1.value[1] = q.value[1].negate();
        q_1.value[2] = q.value[2].negate();
        q_1.value[3] = q.value[3].negate();
        return q_1;
    }
}
