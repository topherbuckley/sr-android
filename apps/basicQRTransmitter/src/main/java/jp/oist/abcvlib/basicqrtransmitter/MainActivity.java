package jp.oist.abcvlib.basicqrtransmitter;

import android.os.Bundle;
import jp.oist.abcvlib.util.Logger;

import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.core.AbcvlibActivity;
import jp.oist.abcvlib.core.inputs.PublisherManager;
import jp.oist.abcvlib.core.inputs.phone.ImageDataRaw;
import jp.oist.abcvlib.core.inputs.phone.QRCodeData;
import jp.oist.abcvlib.core.inputs.phone.QRCodeDataSubscriber;
import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;
import jp.oist.abcvlib.util.QRCode;
import jp.oist.abcvlib.util.ScheduledExecutorServiceWithException;
import jp.oist.abcvlib.util.SerialCommManager;
import jp.oist.abcvlib.util.SerialReadyListener;
import jp.oist.abcvlib.util.UsbSerial;

/**
 * Android application showing connection to IOIOBoard, Hubee Wheels, and Android Sensors
 * Initializes socket connection with external python server
 * Runs PID controller locally on Android, but takes PID parameters from python GUI
 * @author Christopher Buckley https://github.com/topherbuckley
 */
public class MainActivity extends AbcvlibActivity implements SerialReadyListener,
        QRCodeDataSubscriber {

    private QRCode qrCode;
    private PublisherManager publisherManager;
    private float speedL = 0;
    private float speedR = 0;
    private float speed = 0.6f;
    private String qrcodeData = "";
    private enum ACTIONS {TURN_LEFT, TURN_RIGHT};
    private ACTIONS action = ACTIONS.TURN_RIGHT;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Passes Android App information up to parent classes for various usages. Do not modify
        super.onCreate(savedInstanceState);

        // Setup Android GUI. Point this method to your main activity xml file or corresponding int
        // ID within the R class
        setContentView(R.layout.activity_main);

        // create a new QRCode object with input args point to the FragmentManager and your layout fragment where you want to generate the qrcode image.
        qrCode = new QRCode(getSupportFragmentManager(), R.id.qrFragmentView);
    }

    private Runnable swapAction = new Runnable() {
        @Override
        public void run() {
            if (action == ACTIONS.TURN_RIGHT){
                action = ACTIONS.TURN_LEFT;
                turnRight();
            } else {
                action = ACTIONS.TURN_RIGHT;
                turnLeft();
            }
        }
    };

    @Override
    public void onSerialReady(UsbSerial usbSerial) {
        publisherManager = new PublisherManager();

        QRCodeData qrCodeData = new QRCodeData.Builder(this, publisherManager, this).build();
        qrCodeData.addSubscriber(this);

        publisherManager.initializePublishers();
        publisherManager.startPublishers();

        setSerialCommManager(new SerialCommManager(usbSerial));
        super.onSerialReady(usbSerial);
    }

    @Override
    public void onOutputsReady() {
        publisherManager.initializePublishers();
        publisherManager.startPublishers();
        ScheduledExecutorServiceWithException executor = new ScheduledExecutorServiceWithException(1, new ProcessPriorityThreadFactory(Thread.MIN_PRIORITY, "ActionSelector"));
        executor.scheduleAtFixedRate(swapAction, 0, 10, TimeUnit.SECONDS);
    }

    @Override
    public void onQRCodeDetected(String qrDataDecoded) {
        if (!qrDataDecoded.equals("")){
            Logger.i("qrcode", "QR Code Found and decoded: " + qrDataDecoded);
        }
    }

    // Main loop for any application extending AbcvlibActivity. This is where you will put your main code
    @Override
    protected void abcvlibMainLoop(){
        outputs.setWheelOutput(speedL, speedR, false, false);
    }

    private void turnRight(){
        speedL = -speed;
        speedR = speed;
        qrCodeRegenerate("R");
    }

    private void turnLeft(){
        speedL = speed;
        speedR = -speed;
        qrCodeRegenerate("L");
    }

    private void qrCodeRegenerate(String qrcodeData){
        qrCode.close();
        qrCode.generate(qrcodeData);
    }
}
