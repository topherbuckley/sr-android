package jp.oist.abcvlib.core;

import jp.oist.abcvlib.util.Logger;

import java.util.ArrayList;
import java.util.List;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.Closeable;
import ioio.lib.api.DigitalInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOConnectionManager;
import jp.oist.abcvlib.core.inputs.microcontroller.BatteryData;
import jp.oist.abcvlib.core.inputs.microcontroller.WheelData;

/**
 * AbcvlibLooper provides the connection with the IOIOBoard by allowing access to the loop
 * function being called by the software on the IOIOBoard itself. All class variables and
 * contents of the setup() and loop() methods are passed upward to the respective parent classes
 * related to the core IOIOBoard operation by extending BaseIOIOLooper.
 *
 * AbcvlibLooper represents the "control" thread mentioned in the git wiki. It sets up the IOIO
 * Board pin connections, reads the encoder values, and writes out the total encoder counts for
 * wheel speed calculations elsewhere.
 *
 * @author Jiexin Wang https://github.com/ha5ha6
 * @author Christopher Buckley https://github.com/topherbuckley
 */
public class AbcvlibLooper extends BaseIOIOLooper {

    private final String TAG = this.getClass().getName();

    //      --------------Quadrature Encoders----------------
    /**
     Creates IOIO Board object that read the quadrature encoders of the Hubee Wheels.
     Using the encoderARightWheel.read() returns a boolean of either high or low telling whether the
     quadrature encoder is on a black or white mark at the rime of read.<br><br>

     The encoderARightWheel.waitForValue(true) can also be used to wait for the value read by the quadrature
     encoder to change to a given value (true in this case).<br><br>

     encoderARightWheelStatePrevious is just the previous reading of encoderARightWheel<br><br>

     For more on quadrature encoders see
     <a href="http://www.creative-robotics.com/bmdsresources">here</a> OR
     <a href="https://en.wikipedia.org/wiki/Rotary_encoder#Incremental_rotary_encoder">here</a><br><br>

     For more on IOIO Board DigitalInput objects see:
     <a href="https://github.com/ytai/ioio/wiki/Digital-IO">here</a><br><br>
    */
    private DigitalInput encoderARightWheel;
    /**
     * @see #encoderARightWheel
     */
    private DigitalInput encoderBRightWheel;
    /**
     * @see #encoderARightWheel
     */
    private DigitalInput encoderALeftWheel;
    /**
     * @see #encoderARightWheel
     */
    private DigitalInput encoderBLeftWheel;

    //     --------------Wheel Direction Controllers----------------
    /**
     The values set by input1RightWheelController.write() control the direction of the Hubee wheels.
     See <a hred="http://www.creative-robotics.com/bmdsresources">here</a> for the source of the
     control value table copied below:<br><br>

     Table below refers to a single wheel
     (e.g. setting input1RightWheelController to H and input2RightWheelController to L
     with PWM H and Standby H would result in the right wheel turning backwards)<br><br>

     Setting input1RightWheelController to H is done via input1RightWheelController.write(true)<br><br>

     <table style="width:100%">
        <tr>
            <th>IN1</th>
            <th>IN2</th>
            <th>PWM</th>
            <th>Standby</th>
            <th>Result</th>
        </tr>
        <tr>
            <th>H</th>
            <th>H</th>
            <th>H/L</th>
            <th>H</th>
            <th>Stop-Brake</th>
        </tr>
        <tr>
            <th>L</th>
            <th>H</th>
            <th>H</th>
            <th>H</th>
            <th>Turn Forwards</th>
        </tr>
        <tr>
            <th>L</th>
            <th>H</th>
            <th>L</th>
            <th>H</th>
            <th>Stop-Brake</th>
        </tr>
        <tr>
            <th>H</th>
            <th>L</th>
            <th>H</th>
            <th>H</th>
            <th>Turn Backwards</th>
        </tr>
        <tr>
            <th>H</th>
            <th>L</th>
            <th>L</th>
            <th>H</th>
            <th>Stop-Brake</th>
        </tr>
        <tr>
            <th>L</th>
            <th>L</th>
            <th>H/L</th>
            <th>H</th>
            <th>Stop-NoBrake</th>
        </tr>
        <tr>
            <th>H/L</th>
            <th>H/L</th>
            <th>H/L</th>
            <th>L</th>
            <th>Standby</th>
        </tr>
     </table>
    */
    private DigitalOutput input1RightWheelController;
    /**
     * @see #input1RightWheelController
     */
    private DigitalOutput input2RightWheelController;
    /**
     * @see #input1RightWheelController
     */
    private DigitalOutput input1LeftWheelController;
    /**
     * @see #input1RightWheelController
     */
    private DigitalOutput input2LeftWheelController;
    /**
     * Monitors onboard battery voltage (note this is not the smartphone battery voltage. The
     * smartphone battery should always be fully charged as it will draw current from the onboard
     * battery until the onboard battery dies)
     */
    private AnalogInput batteryVoltageMonitor;
    /**
     * Monitors external charger (usb or wirelss coil) voltage. Use this to detect on charge puck or not. (H/L)
     */
    private AnalogInput chargerVoltageMonitor;
    /**
     * Monitors wireless receiver coil voltage at coil (not after Qi regulator). Use this to detect on charge puck or not. (H/L)
     */
    private AnalogInput coilVoltageMonitor;

    //     --------------Pulse Width Modulation (PWM)----------------
    /**
     PwmOutput objects like pwmControllerRightWheel have methods like
    <ul>
     <li>openPwmOutput(pinNum, freq) to start the PWM on pinNum at freq</li>

     <li>pwm.setDutyCycle(dutycycle) to change the freq directly by modifying the pulse width</li>
     </ul>

     More info <a href="https://github.com/ytai/ioio/wiki/PWM-Output">here</a>
    */
    private PwmOutput pwmControllerRightWheel;
    /**
     * @see #pwmControllerRightWheel
     */
    private PwmOutput pwmControllerLeftWheel;

    /**
     * The IN1 and IN2 IO determining Hubee Wheel direction. See input1RightWheelController doc for
     * control table
     *
     * @see #input1RightWheelController
     */
    private boolean input1RightWheelState;
    /**
     * @see #input1RightWheelState
     */
    private boolean input2RightWheelState;
    /**
     * @see #input1RightWheelState
     */
    private boolean input1LeftWheelState;
    /**
     * @see #input1RightWheelState
     */
    private boolean input2LeftWheelState;

    /**
     * Duty cycle of PWM pulse width tracking variable. Values range from 0 to 100
     */
    private float dutyCycleRightWheel;
    /**
     * @see #dutyCycleRightWheel
     */
    private float dutyCycleLeftWheel;

    private volatile BatteryData batteryData = null;
    private volatile WheelData wheelData = null;
    private final IOReadyListener ioReadyListener;

    private List<Closeable> ioioPins = new ArrayList<>();

    public AbcvlibLooper(IOReadyListener ioReadyListener){
        this.ioReadyListener = ioReadyListener;
    }

    /**
     * Called every time a connection with IOIO has been established.
     * Typically used to open pins.
     *
     * @throws ConnectionLostException
     *             When IOIO connection is lost.
     *
     * @see ioio.lib.util.IOIOLooper#setup(IOIO)
     */
    @Override
    public void setup() throws ConnectionLostException {

        /*
         --------------IOIO Board PIN References----------------
         Although several other pins would work, there are restrictions on which pins can be used to
         PWM and which pins can be used for analog/digital purposes. See back of IOIO Board for pin
         mapping.

         Note the INPUTX_XXXX pins were all placed on 5V tolerant pins, but Hubee wheel inputs
         operate from 3.3 to 5V, so any other pins would work just as well.

         Although the encoder pins were chosen to be on the IOIO board analog in pins, this is not
         necessary as the encoder objects only read digital high and low values.

         PWM pins are currently on pins with P (peripheral) and 5V tolerant pins. The P capability is
         necessary in order to properly use the PWM based methods (though not sure if these are even
         used). The 5V tolerant pins are not necessary as the IOIO Board PWM is a 3.3V peak signal.
         */

        Logger.d("abcvlib", "AbcvlibLooper setup() started");

        final int INPUT1_RIGHT_WHEEL_PIN = 2;
        final int INPUT2_RIGHT_WHEEL_PIN = 3;
        final int PWM_RIGHT_WHEEL_PIN = 4;
        final int ENCODER_A_RIGHT_WHEEL_PIN = 6;
        final int ENCODER_B_RIGHT_WHEEL_PIN=7;

        final int INPUT1_LEFT_WHEEL_PIN = 11;
        final int INPUT2_LEFT_WHEEL_PIN = 12;
        final int PWM_LEFT_WHEEL_PIN = 13;
        final int ENCODER_A_LEFT_WHEEL_PIN=15;
        final int ENCODER_B_LEFT_WHEEL_PIN=16;
        
        final int CHARGER_VOLTAGE = 33;
        final int BATTERY_VOLTAGE = 34;
        final int COIL_VOLTAGE = 35;

        Logger.v(TAG, "ioio_ state = " + ioio_.getState().toString());

        /*
        Initializing all wheel controller values to low would result in both wheels being in
        the "Stop-NoBrake" mode according to the Hubee control table. Not sure if this state
        is required for some reason or just what was defaulted to.
        */
        input1RightWheelController = ioio_.openDigitalOutput(INPUT1_RIGHT_WHEEL_PIN,false);
        ioioPins.add(input1RightWheelController);
        input2RightWheelController = ioio_.openDigitalOutput(INPUT2_RIGHT_WHEEL_PIN,false);
        ioioPins.add(input2RightWheelController);
        input1LeftWheelController = ioio_.openDigitalOutput(INPUT1_LEFT_WHEEL_PIN,false);
        ioioPins.add(input1LeftWheelController);
        input2LeftWheelController = ioio_.openDigitalOutput(INPUT2_LEFT_WHEEL_PIN,false);
        ioioPins.add(input2LeftWheelController);

        batteryVoltageMonitor = ioio_.openAnalogInput(BATTERY_VOLTAGE);
        ioioPins.add(batteryVoltageMonitor);
        chargerVoltageMonitor = ioio_.openAnalogInput(CHARGER_VOLTAGE);
        ioioPins.add(chargerVoltageMonitor);
        coilVoltageMonitor = ioio_.openAnalogInput(COIL_VOLTAGE);
        ioioPins.add(coilVoltageMonitor);

        // This try-catch statement should likely be refined to handle common errors/exceptions
        try{
            /*
             * PWM frequency. Do not modify locally. Modify at AbcvlibActivity level if necessary
             *  Not sure why initial PWM_FREQ is 1000, but assume this can be modified as necessary.
             *  This may depend on the motor or microcontroller requirements/specs. <br><br>
             *
             *  If motor is just a DC motor, I guess this does not matter much, but for servos, this would
             *  be the control function, so would have to match the baud rate of the microcontroller. Note
             *  this library is not set up to control servos at this time. <br><br>
             *
             *  The microcontroller likely has a maximum frequency which it can turn ON/OFF the IO, so
             *  setting PWM_FREQ too high may cause issues for certain microcontrollers.
             */
            int PWM_FREQ = 1000;
            pwmControllerRightWheel = ioio_.openPwmOutput(PWM_RIGHT_WHEEL_PIN, PWM_FREQ);
            ioioPins.add(pwmControllerRightWheel);
            pwmControllerLeftWheel = ioio_.openPwmOutput(PWM_LEFT_WHEEL_PIN, PWM_FREQ);
            ioioPins.add(pwmControllerLeftWheel);

            /*
            Note openDigitalInput() can also accept DigitalInput.Spec.Mode.OPEN_DRAIN if motor
            circuit requires
            */
            encoderARightWheel = ioio_.openDigitalInput(ENCODER_A_RIGHT_WHEEL_PIN,
                    DigitalInput.Spec.Mode.PULL_UP);
            ioioPins.add(encoderARightWheel);
            encoderBRightWheel = ioio_.openDigitalInput(ENCODER_B_RIGHT_WHEEL_PIN,
                    DigitalInput.Spec.Mode.PULL_UP);
            ioioPins.add(encoderBRightWheel);
            encoderALeftWheel = ioio_.openDigitalInput(ENCODER_A_LEFT_WHEEL_PIN,
                    DigitalInput.Spec.Mode.PULL_UP);
            ioioPins.add(encoderALeftWheel);
            encoderBLeftWheel = ioio_.openDigitalInput(ENCODER_B_LEFT_WHEEL_PIN,
                    DigitalInput.Spec.Mode.PULL_UP);
            ioioPins.add(encoderBLeftWheel);

        }catch (ConnectionLostException e){
            Logger.e("abcvlib", "ConnectionLostException at AbcvlibLooper.setup()");
            throw e;
        }
        Logger.d("abcvlib", "AbcvlibLooper setup() finished");
        ioReadyListener.onIOReady();
    }

    /**
     * Called repetitively while the IOIO is connected.
     *
     * @see ioio.lib.util.IOIOLooper#loop()
     */
    @Override
    public void loop() {

        try {
            long timeStamp = System.nanoTime();

            // Read IOIO Input pins
            float chargerVoltage = chargerVoltageMonitor.getVoltage();
            float coilVoltage = coilVoltageMonitor.getVoltage();
            float batteryVoltage = batteryVoltageMonitor.getVoltage();
            boolean encoderARightWheelState = encoderARightWheel.read();
            boolean encoderBRightWheelState = encoderBRightWheel.read();
            boolean encoderALeftWheelState = encoderALeftWheel.read();
            boolean encoderBLeftWheelState = encoderBLeftWheel.read();

//            // Update any subscribers/listeners
//            if (wheelData != null){
//                wheelData.onWheelDataUpdate(timeStamp, encoderARightWheelState, encoderBRightWheelState,
//                        encoderALeftWheelState, encoderBLeftWheelState);
//            }
//            if (batteryData != null){
//                batteryData.onChargerVoltageUpdate(chargerVoltage, coilVoltage, timeStamp);
//                batteryData.onBatteryVoltageUpdate(batteryVoltage, timeStamp);
//            }

            // Write all calculated values to the IOIO Board pins
            input1RightWheelController.write(input1RightWheelState);
            input2RightWheelController.write(input2RightWheelState);
            pwmControllerRightWheel.setDutyCycle(dutyCycleRightWheel); //converting from duty cycle to pulse width
            input1LeftWheelController.write(input1LeftWheelState);
            input2LeftWheelController.write(input2LeftWheelState);
            pwmControllerLeftWheel.setDutyCycle(dutyCycleLeftWheel);//converting from duty cycle to pulse width
        }
        catch (ConnectionLostException | InterruptedException e){
            Logger.e("abcvlib", "connection lost in AbcvlibLooper.loop");
        }
        IOIOConnectionManager.Thread.yield();
    }

    /**
     * Called when the IOIO is disconnected.
     *
     * @see ioio.lib.util.IOIOLooper#disconnected()
     */
    @Override
    public void disconnected() {
        Logger.d("abcvlib", "AbcvlibLooper disconnected");
    }

    /**
     * Called when the IOIO is connected, but has an incompatible firmware version.
     *
     * @see ioio.lib.util.IOIOLooper#incompatible(IOIO)
     */
    @Override
    public void incompatible() {
        Logger.e("abcvlib", "Incompatible IOIO firmware version!");
    }

    /**
     * Returns a hard limited value for the dutyCycle to be within the inclusive range of [0,1].
     * @param dutyCycleOld un-limited duty cycle
     * @return limited duty cycle
     */
    private float dutyCycleLimiter(float dutyCycleOld){
        final int MAX_DUTY_CYCLE = 1;
        float dutyCycleNew;

        if(Math.abs(dutyCycleOld) < MAX_DUTY_CYCLE){
            dutyCycleNew = Math.abs(dutyCycleOld);
        }else{
            dutyCycleNew = MAX_DUTY_CYCLE;
        }

        return dutyCycleNew;
    }

    public void setDutyCycle(float left, float right) {
        // Determine how to set the ioio input pins which in turn set the direction of the wheel
        if(right >= 0){
            input1RightWheelState = false;
            input2RightWheelState = true;
        }else{
            input1RightWheelState = true;
            input2RightWheelState = false;
        }

        if(left <= 0){
            input1LeftWheelState = false;
            input2LeftWheelState = true;
        }else{
            input1LeftWheelState = true;
            input2LeftWheelState = false;
        }

        // Limit the duty cycle to be from 0 to 1
        dutyCycleRightWheel = dutyCycleLimiter(right);
        dutyCycleLeftWheel = dutyCycleLimiter(left);
    }

    public void setBatteryData(BatteryData batteryData) {
        this.batteryData = batteryData;
    }

    public void setWheelData(WheelData wheelData) {
        this.wheelData = wheelData;
    }

    public void turnOffWheels() throws ConnectionLostException {
        pwmControllerRightWheel.setDutyCycle(0);
        pwmControllerLeftWheel.setDutyCycle(0);
        Logger.d("abcvlib", "Turning off wheels");
    }

    void shutDown(){
        for (Closeable pin:ioioPins){
            pin.close();
        }
    }
}
