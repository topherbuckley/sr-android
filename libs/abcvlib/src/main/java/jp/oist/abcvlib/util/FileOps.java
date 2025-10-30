package jp.oist.abcvlib.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Debug;
import android.os.Environment;
import jp.oist.abcvlib.util.Logger;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Not used anywhere else in the library at this time. Leaving for possible future use.
 */
public class FileOps {
    private static final String TAG = "FileOps";

    public static void savedata(String content, String filename){
        try{
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                File sdCardDir=Environment.getExternalStorageDirectory();
                if(sdCardDir.exists() && sdCardDir.canWrite()){
                    File file=new File(sdCardDir.getAbsolutePath() + "/DataDir" + filename);
                    boolean madeDir = file.mkdir();
                    if (madeDir){
//                        String filepath = file.getAbsolutePath() + filename;
                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file.getAbsolutePath(),false), StandardCharsets.UTF_8));
                        bw.write(content);
                        bw.close();
                    } else{
                        Logger.d(TAG, "Unable to create DataDir directory");
                    }
                }
            }
        }catch(IOException e){
            ErrorHandler.eLog(TAG, "Error when saving data to file", e, true);
        }
    }

    public static void savedata(Context context, byte[] content, String pathName, String filename){
        try{
            boolean deleted = false;
            boolean created = false;
            File file=new File(context.getFilesDir() + File.separator + pathName + File.separator + filename);
            File path = new File(context.getFilesDir() + File.separator + pathName);
            if (file.exists()){
                deleted = file.delete();
            }
            if (!file.exists() || deleted){
                path.mkdirs();
                created = file.createNewFile();
                Logger.v(TAG, "Writing " + file.getAbsolutePath());
                FileOutputStream fileOutputStream = new FileOutputStream(file.getAbsolutePath(),false);
                fileOutputStream.write(content);
                fileOutputStream.close();
            }
        }catch(Exception e){
            ErrorHandler.eLog(TAG, "Error when saving data to file", e, true);

        }
    }

    public static void savedata(Context context, InputStream content, String pathName, String filename){
        try{
            boolean deleted = false;
            boolean created = false;
            File file=new File(context.getFilesDir() + File.separator + pathName + File.separator + filename);
            File path = new File(context.getFilesDir() + File.separator + pathName);
            if (file.exists()){
                deleted = file.delete();
            }
            if (!file.exists() || deleted){
                path.mkdirs();
                created = file.createNewFile();
                Logger.v(TAG, "Writing " + file.getAbsolutePath());
                FileOutputStream fileOutputStream = new FileOutputStream(file.getAbsolutePath(),false);

                byte[] buffer = new byte[1024];
                int read;
                while((read = content.read(buffer)) != -1){
                    fileOutputStream.write(buffer, 0, read);
                }

                fileOutputStream.close();
            }
        }catch(Exception e){
            ErrorHandler.eLog(TAG, "Error when saving data to file", e, true);
        }
    }

    public double[] readData(String fileName){

        String line = "";
        String[] lineArray;
        double[] output = new double[4];
        File file = getFile(fileName);
        String filePath = file.getPath();

        if (isExternalStorageReadable()){
            try {
                BufferedReader bufferedReader = new BufferedReader(new FileReader(filePath));
                line = bufferedReader.readLine();
                lineArray = line.split(",");
                for (int i = 0; i < lineArray.length; i++){
                    output[i] = Double.parseDouble(lineArray[i]);
                }
            } catch (IOException | NullPointerException e) {
                ErrorHandler.eLog(TAG, "Error when reading data from file", e, true);
            }
        }

        return output;
    }

    public void writeToFile(Context context, String fileName, double[] data) {

        String androidDataString = "";
        File file = getFile(fileName);

        for (int i = 0; i < data.length; i++){
            androidDataString = androidDataString.concat(data[i] + ",");
        }

        if (isExternalStorageWritable()){
            FileOutputStream stream = null;
            try {
                stream = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                ErrorHandler.eLog(TAG, "Error file not found", e, true);
            }

            try {
                stream.write(androidDataString.getBytes());
            }
            catch (IOException e) {
                ErrorHandler.eLog(TAG, "Error writing file", e, true);
            }finally {
                try {
                    stream.close();
                } catch (IOException e) {
                    ErrorHandler.eLog(TAG, "Error", e, true);
                }
            }
        }
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public File getFile(String fileName) {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), fileName);
        try {
            file.createNewFile();
        } catch (IOException e) {
            ErrorHandler.eLog(TAG, "Error when getting file", e, true);
        }
        return file;
    }

    public static void copyAssets(Context context, String path) {
        AssetManager assetManager = context.getAssets();
        String[] files = null;
        try {
            files = assetManager.list(path);
        } catch (IOException e) {
            ErrorHandler.eLog(TAG, "Failed to get asset file list", e, true);
        }
        if (files != null) for (String filename : files) {
            try (InputStream in = assetManager.open(path + filename)) {
                savedata(context, in, path, filename);
            } catch (IOException e) {
                ErrorHandler.eLog(TAG, "Failed to copy asset file = " + filename, e, true);
            }
            // NOOP
        }
    }

    /**
     *     This is helpful code when you have an OutOfMemoryError. Keeping as comment for easy
     *     access until we're sure we won't have these any longer.
     */
    public static void heapDump(Context context){
        try {
            boolean deleted = false;
            boolean created = false;
            Logger.d(TAG, "Within HeapDump");
            File file=new File( context.getFilesDir() + File.separator + "dump.hprof");
            if (file.exists()){
                deleted = file.delete();
            }
            if (!file.exists() || deleted){
                created = file.createNewFile();
            }
            Debug.dumpHprofData(file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
