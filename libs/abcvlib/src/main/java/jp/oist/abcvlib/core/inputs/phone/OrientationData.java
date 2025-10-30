package jp.oist.abcvlib.core.inputs.phone;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import jp.oist.abcvlib.util.Logger;

import java.util.ArrayList;

import jp.oist.abcvlib.core.inputs.PublisherManager;
import jp.oist.abcvlib.core.inputs.Publisher;

/**
 * MotionSensors reads and processes the data from the Android phone gryoscope and
 * accelerometer. The three main goals of this class are:
 *
 * 1.) To estimate the tilt angle of the phone by combining the input from the
 * gyroscope and accelerometer
 * 2.) To calculate the speed of each wheel (speedRightWheel and speedLeftWheel) by using
 * existing and past quadrature encoder states
 * 3.) Provides the get() methods for all relevant sensory variables
 *
 * This thread typically updates every 5 ms, but this depends on the
 * SensorManager.SENSOR_DELAY_FASTEST value. This represents the fastest possible sampling rate for
 * the sensor. Note this value can change depending on the Android phone used and corresponding
 * internal sensor hardware.
 *
 * A counter is keep track of the number of sensor change events via sensorChangeCount.
 *
 * @author Jiexin Wang https://github.com/ha5ha6
 * @author Christopher Buckley https://github.com/topherbuckley
 */
public class OrientationData extends Publisher<OrientationDataSubscriber> implements SensorEventListener {

    /*
     * Keeps track of current history index.
     * indexCurrent calculates the correct index within the time history arrays in order to
     * continuously loop through and rewrite the encoderCountHistoryLength indexes.
     * E.g. if sensorChangeCount is 15 and encoderCountHistoryLength is 15, then indexCurrent
     * will resolve to 0 and the values for each history array will be updated from index 0 until
     * sensorChangeCount exceeds 30 at which point it will loop to 0 again, so on and so forth.
     */
    private int indexCurrentRotation = 1;
    /**
     * Length of past timestamps and encoder values you keep in memory. 15 is not significant,
     * just what was deemed appropriate previously.
     */
    private int windowLength = 3;

    //Total number of times the sensors have changed data
    private int sensorChangeCountRotation = 1;

    private final SensorManager sensorManager;
    private final Sensor gyroscope;
    private final Sensor rotation_sensor;
    private final Sensor accelerometer;
    private Sensor accelerometer_uncalibrated;

    /**
     * orientation vector See link below for android doc
     * https://developer.android.com/reference/android/hardware/SensorManager.html#getOrientation(float%5B%5D,%2520float%5B%5D)
     */
    private final float[] orientation = new float[3];
    /**
     * thetaRad calculated from rotation vector
     */
    private final double[] thetaRad = new double[windowLength];
    /**
     * rotation matrix
     */
    private final float[] rotationMatrix = new float[16];
    /**
     * rotation matrix remapped
     */
    private final float[] rotationMatrixRemap = new float[16];
    /**
     * angularVelocity calculated from RotationMatrix.
     */
    private final double[] angularVelocityRad = new double[windowLength];

    int timerCount = 1;

    //----------------------------------------------------------------------------------------------

    //----------------------------------------- Timestamps -----------------------------------------
    /**
     * Keeps track of both gyro and accelerometer sensor timestamps
     */
    private final long[] timeStamps = new long[windowLength];
    /**
    indexHistoryOldest calculates the index for the oldest encoder count still within
    the history. Using the most recent historical point could lead to speed calculations of zero
    in the event the quadrature encoders slip/skip and read the same wheel count two times in a
    row even if the wheel is moving with a non-zero speed.
     */
    int indexHistoryOldest = 0; // Keeps track of oldest history index.
    double dt = 0;

    /**
     * Constructor that sets up Android Sensor Service and creates Sensor objects for both
     * accelerometer and gyroscope. Then registers both sensors such that their onSensorChanged
     * events will call the onSensorChanged method within this class.
     */
    public OrientationData(Context context, PublisherManager publisherManager){
        super(context, publisherManager);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        rotation_sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            accelerometer_uncalibrated = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED);
        }
    }

    public static class Builder{
        private final Context context;
        private final PublisherManager publisherManager;

        public Builder(Context context, PublisherManager publisherManager){
            this.context = context;
            this.publisherManager = publisherManager;
        }

        public OrientationData build(){
            return new OrientationData(context, publisherManager);
        }
    }

    /**
     * Assume this is only used for sensors that have the ability to change accuracy (e.g. GPS)
     * @param sensor Sensor object that has changed its accuracy
     * @param accuracy Accuracy. See SensorEvent on Android Dev documentation for details
     */
    @Override
    public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy){
        // Not sure if we need to worry about this. I think this is more for more variable sensors like GPS but could be wrong.
    }

    /**
     * This is called every time a registered sensor provides data. Sensor must be registered before
     * it will fire the event which calls this method. If statements handle differentiating between
     * accelerometer and gyrscope events.
     * @param event SensorEvent object that has updated its output
     */
    @Override
    public void onSensorChanged(SensorEvent event){
        Sensor sensor = event.sensor;

//        if(sensor.getType()==Sensor.TYPE_GYROSCOPE){
//            indexCurrentGyro = sensorChangeCountGyro % windowLength;
//            indexPreviousGyro = (sensorChangeCountGyro - 1) % windowLength;
//            // Rotation around x-axis
//            // See https://developer.android.com/reference/android/hardware/SensorEvent.html
//            thetaDotGyro = event.values[0];
//            thetaDotGyroDeg = (thetaDotGyro * (180 / Math.PI));
//            timeStampsGyro[indexCurrentGyro] = event.timestamp;
//            dtGyro = (timeStampsGyro[indexCurrentGyro] - timeStampsGyro[indexPreviousGyro]) / 1000000000f;
//            sensorChangeCountGyro++;
//            if (loggerOn){
//                sendToLog();
//            }
//        }
        if(sensor.getType()==Sensor.TYPE_ROTATION_VECTOR){
            // Timer for only TYPE_ROTATION_VECTOR sensor change
            indexCurrentRotation = sensorChangeCountRotation % windowLength;
            int indexPreviousRotation = (sensorChangeCountRotation - 1) % windowLength;
            indexHistoryOldest = (sensorChangeCountRotation + 1) % windowLength;
            timeStamps[indexCurrentRotation] = event.timestamp;
            dt = (timeStamps[indexCurrentRotation] - timeStamps[indexPreviousRotation]) / 1000000000f;

            SensorManager.getRotationMatrixFromVector(rotationMatrix , event.values);
            SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_Z, rotationMatrixRemap);
            SensorManager.getOrientation(rotationMatrixRemap, orientation);
            thetaRad[indexCurrentRotation] = orientation[1]; //Pitch
            angularVelocityRad[indexCurrentRotation] = (thetaRad[indexCurrentRotation] - thetaRad[indexPreviousRotation]) / dt;

            // Update all previous variables with current ones
            sensorChangeCountRotation++;
        }

        timerCount ++;

        if(!paused){
            for (OrientationDataSubscriber subscriber:subscribers){
                subscriber.onOrientationUpdate(timeStamps[indexCurrentRotation],
                        thetaRad[indexCurrentRotation],
                        angularVelocityRad[indexCurrentRotation]);
            }
        }
    }

    /**
     Registering sensorEventListeners for accelerometer and gyroscope only.
     */
    public void register(Handler handler){
        // Check if rotation_sensor exists before trying to turn on the listener
        if (rotation_sensor != null){
            sensorManager.registerListener(this, rotation_sensor, SensorManager.SENSOR_DELAY_FASTEST, handler);
        } else {
            Logger.e("SensorTesting", "No Default rotation_sensor Available.");
        }
        // Check if gyro exists before trying to turn on the listener
        if (gyroscope != null){
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST, handler);
        } else {
            Logger.e("SensorTesting", "No Default gyroscope Available.");
        }
        // Check if rotation_sensor exists before trying to turn on the listener
        if (accelerometer != null){
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST, handler);
        } else {
            Logger.e("SensorTesting", "No Default accelerometer Available.");
        }
        // Check if rotation_sensor exists before trying to turn on the listener
        if (accelerometer_uncalibrated != null){
            sensorManager.registerListener(this, accelerometer_uncalibrated, SensorManager.SENSOR_DELAY_FASTEST, handler);
        } else {
            Logger.e("SensorTesting", "No Default accelerometer_uncalibrated Available.");
        }
    }

//    /**
//     * Check if accelerometer and gyroscope objects still exist before trying to unregister them.
//     * This prevents null pointer exceptions.
//     */
    public void unregister(){
        // Check if rotation_sensor exists before trying to turn off the listener
        if (rotation_sensor != null){
            sensorManager.unregisterListener(this, rotation_sensor);
        }
        // Check if gyro exists before trying to turn off the listener
        if (gyroscope != null){
            sensorManager.unregisterListener(this, gyroscope);
        }
        // Check if rotation_sensor exists before trying to turn off the listener
        if (accelerometer != null){
            sensorManager.unregisterListener(this, accelerometer);
        }
        // Check if gyro exists before trying to turn off the listener
        if (accelerometer_uncalibrated != null){
            sensorManager.unregisterListener(this, accelerometer_uncalibrated);
        }
    }

    /**
     * Sets the history length for which to base the derivative functions off of (angular velocity,
     * linear velocity).
     * @param len length of array for keeping history
     */
    public void setWindowLength(int len) {
        windowLength = len; }

    /**
     * @return utility function converting radians to degrees
     */
    public static double getThetaDeg(double radians){
        return (radians * (180 / Math.PI));
    }

    /**
     * @return utility function converting rad/s to deg/s
     */
    public static double getAngularVelocityDeg(double radPerSec){
        return getThetaDeg(radPerSec);
    }

    @Override
    public void start() {
        mHandlerThread = new HandlerThread("sensorThread");
        mHandlerThread.start();
        handler = new Handler(mHandlerThread.getLooper());
        register(handler);
        publisherManager.onPublisherInitialized();
        super.start();
    }

    @Override
    public void stop() {
        mHandlerThread.quitSafely();
        unregister();
        handler = null;
        super.stop();
    }

    @Override
    public ArrayList<String> getRequiredPermissions() {
        return new ArrayList<>();
    }

    /**
     * This seems to be a very convoluted way to do this, but it seems to work just fine
     * @param angle Tilt angle in radians
     * @return Wrapped angle in radians from -Pi to Pi
     */
    private static float wrapAngle(float angle){
        while(angle<-Math.PI)
            angle+=2*Math.PI;
        while(angle>Math.PI)
            angle-=2*Math.PI;
        return angle;
    }
}
