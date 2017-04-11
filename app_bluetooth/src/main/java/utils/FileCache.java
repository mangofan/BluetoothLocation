package utils;
/*
 * Created by fanwe on 2017/3/31.
 */

import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileCache {
    public static synchronized  void saveFile(String saveString){

        File f = new File(Environment.getExternalStorageDirectory(),"/download/db.txt");
        FileOutputStream out =null;
        try {
            out =new FileOutputStream(f,true);
            out.write(saveString.getBytes());
            out.write("\n".getBytes());
            out.flush();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if(out!=null)
                    out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}