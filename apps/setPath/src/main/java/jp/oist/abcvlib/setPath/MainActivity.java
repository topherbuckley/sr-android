package jp.oist.abcvlib.setPath;

import android.os.Bundle;
import jp.oist.abcvlib.util.Logger;

import jp.oist.abcvlib.core.AbcvlibActivity;
import jp.oist.abcvlib.core.AbcvlibLooper;
import jp.oist.abcvlib.core.IOReadyListener;

/**
 * Android application showing connection to IOIOBoard, Hubee Wheels, and Android Sensors
 * Also includes a simple controller making the robot move along a set path repeatedly
 * @author Christopher Buckley https://github.com/topherbuckley
 */
public class MainActivity extends AbcvlibActivity implements IOReadyListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setIoReadyListener(this);
        // Passes Android App information up to parent classes for various usages. Do not modify
        super.onCreate(savedInstanceState);

        // Setup Android GUI. Point this method to your main activity xml file or corresponding int
        // ID within the R class
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onIOReady(AbcvlibLooper abcvlibLooper) {
        // Linear Back and Forth every 10 mm
        setPath setPathThread = new setPath();
        new Thread(setPathThread).start();
    }

    public class setPath implements Runnable{

        float speed = 0.6f; // Duty Cycle from 0 to 1.

        public void run(){
            try {
                // Set Initial Speed
                getOutputs().setWheelOutput(speed, speed);
                Thread.sleep(2000);
                // Turn left
                getOutputs().setWheelOutput(speed / 3, speed);
                Thread.sleep(5000);
                // Go straight
                getOutputs().setWheelOutput(speed, speed);
                Thread.sleep(2000);
                // turn the other way
                getOutputs().setWheelOutput(speed, speed / 3);
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Logger.e("setPath","Error", e);
                throw new RuntimeException("This is a crash");
            }
        }
    }

}
