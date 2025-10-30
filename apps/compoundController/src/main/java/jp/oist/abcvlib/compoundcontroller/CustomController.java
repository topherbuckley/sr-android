package jp.oist.abcvlib.compoundcontroller;

import jp.oist.abcvlib.util.Logger;

import jp.oist.abcvlib.core.inputs.Publisher;
import jp.oist.abcvlib.core.inputs.microcontroller.WheelDataSubscriber;
import jp.oist.abcvlib.core.outputs.AbcvlibController;

/**
 * Simple proportional controller trying to achieve some setSpeed set by python server GUI.
 */
public class CustomController extends AbcvlibController implements WheelDataSubscriber {

    double actualSpeed = 0;
    double errorSpeed = 0;
    double maxSpeed = 350; // Just spot measurede this by setOutput(1.0, 1.0) and read the log. This will surely change with battery level and wheel wear/tear.
    double setSpeed = 50; // mm/s.
    double d_s = 0.0001; // derivative controller for speed of wheels

    public CustomController(){}

    public void run(){
        errorSpeed = setSpeed - actualSpeed;

        // Note the use of the same output for controlling both wheels. Due to various errors
        // that build up over time, controling individual wheels has so far led to chaos
        // and unstable controllers.
        setOutput((float) ((setSpeed / maxSpeed) + ((errorSpeed * d_s) / maxSpeed)), (float) ((setSpeed / maxSpeed) + ((errorSpeed * d_s) / maxSpeed)));
    }

    @Override
    public void onWheelDataUpdate(long timestamp, int wheelCountL, int wheelCountR, double wheelDistanceL, double wheelDistanceR, double wheelSpeedInstantL, double wheelSpeedInstantR, double wheelSpeedBufferedL, double wheelSpeedBufferedR, double wheelSpeedExpAvgL, double wheelSpeedExpAvgR) {
        actualSpeed = wheelSpeedBufferedL;
        Logger.d("WheelUpdate", "wheelSpeedBufferedL: " + wheelSpeedBufferedL + ", wheelSpeedBufferedR: " + wheelSpeedBufferedR);
    }
}
