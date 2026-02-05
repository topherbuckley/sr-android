package jp.oist.abcvlib.basicassembler

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import jp.oist.abcvlib.basicassembler.databinding.ActivityMainBinding
import jp.oist.abcvlib.core.AbcvlibActivity
import jp.oist.abcvlib.core.inputs.PublisherManager
import jp.oist.abcvlib.core.inputs.TimeStepDataBuffer
import jp.oist.abcvlib.core.inputs.microcontroller.BatteryData
import jp.oist.abcvlib.core.inputs.microcontroller.WheelData
import jp.oist.abcvlib.core.inputs.phone.ImageDataRaw
import jp.oist.abcvlib.core.inputs.phone.MicrophoneData
import jp.oist.abcvlib.core.inputs.phone.OrientationData
import jp.oist.abcvlib.core.learning.ActionSpace
import jp.oist.abcvlib.core.learning.CommActionSpace
import jp.oist.abcvlib.core.learning.MetaParameters
import jp.oist.abcvlib.core.learning.MotionActionSpace
import jp.oist.abcvlib.core.learning.StateSpace
import jp.oist.abcvlib.util.Logger
import jp.oist.abcvlib.util.SerialCommManager
import jp.oist.abcvlib.util.SerialReadyListener
import jp.oist.abcvlib.util.UsbSerial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Most basic Android application showing connection to IOIOBoard and Android Sensors
 * Shows basics of setting up any standard Android Application framework. This MainActivity class
 * does not implement the various listener interfaces in order to subscribe to updates from various
 * sensor data, but instead sets up a custom
 * [jp.oist.abcvlib.core.learning.Trial] object that handles setting up the
 * subscribers and assembles the data into a [TimeStepDataBuffer]
 * comprising multiple [TimeStepDataBuffer.TimeStepData] objects
 * that each represent all the data gathered from one or more sensors over the course of one timestep
 * 
 * Optional commented out lines in each listener method show how to write the data to the Android
 * logcat log. As these occur VERY frequently (tens of microseconds) this spams the logcat and such
 * I have reserved them only for when necessary. The updates to the GUI via the GuiUpdate object
 * are intentionally delayed or sampled every 100 ms so as not to spam the GUI thread and make it
 * unresponsive.
 * @author Christopher Buckley https://github.com/topherbuckley
 */
class MainActivity : AbcvlibActivity(), SerialReadyListener {
    private var guiUpdater: GuiUpdater? = null
    private val maxEpisodeCount = 3
    private val maxTimeStepCount = 40
    private var stateSpace: StateSpace? = null
    private var actionSpace: ActionSpace? = null
    private var timeStepDataBuffer: TimeStepDataBuffer? = null
    lateinit var binding: ActivityMainBinding

    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Setup Android GUI object references such that we can write data to them later.
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initUiUpdater()

        timeStepDataBuffer = TimeStepDataBuffer(10)

        super.onCreate(savedInstanceState)
    }

    /**
     * Refreshes UI values every 100ms.
     */
    private fun initUiUpdater() {
        guiUpdater = GuiUpdater(binding, maxTimeStepCount, maxEpisodeCount)
        lifecycleScope.launch {
            while (isActive) {
                guiUpdater!!.displayUiValues()
                delay(100.milliseconds)
            }
        }
    }

    override fun onSerialReady(usbSerial: UsbSerial) {
        Logger.d(TAG, "onSerialReady")

        /*------------------------------------------------------------------------------
        ------------------------------ Define Action Space -----------------------------
        --------------------------------------------------------------------------------
         */

        // Defining custom actions
        val commActionSpace = CommActionSpace(3).apply {
            // I'm just overwriting an existing to show how
            addCommAction("action1", 0.toByte())
            addCommAction("action2", 1.toByte())
            addCommAction("action3", 2.toByte())
        }

        val motionActionSpace = MotionActionSpace(5).apply {
            // I'm just overwriting an existing to show how
            addMotionAction("stop", 0.toByte(), 0f, 0f, false, false)
            addMotionAction("forward", 1.toByte(), 1f, 1f, false, false)
            addMotionAction("backward", 2.toByte(), -1f, -1f, false, false)
            addMotionAction("left", 3.toByte(), -1f, 1f, false, false)
            addMotionAction("right", 4.toByte(), 1f, -1f, false, false)
        }

        actionSpace = ActionSpace(commActionSpace, motionActionSpace)

        /*------------------------------------------------------------------------------
        ------------------------------ Define State Space ------------------------------
        --------------------------------------------------------------------------------
        */

        val publisherManager = PublisherManager()
        val context = this
        lifecycleScope.launch(Dispatchers.Default) {
            val wheelData = WheelData.Builder(context, publisherManager)
                .setBufferLength(50)
                .setExpWeight(0.01)
                .build()
            wheelData.addSubscriber(timeStepDataBuffer)

            val batteryData = BatteryData.Builder(context, publisherManager).build()
            batteryData.addSubscriber(timeStepDataBuffer)

            val orientationData = OrientationData.Builder(context, publisherManager).build()
            orientationData.addSubscriber(timeStepDataBuffer)

            val microphoneData = MicrophoneData.Builder(context, publisherManager).build()
            microphoneData.addSubscriber(timeStepDataBuffer)

            val imageDataRaw = ImageDataRaw
                .Builder(context, publisherManager, context)
                .setPreviewView(binding.cameraXPreview).build()
            imageDataRaw.addSubscriber(timeStepDataBuffer)

            stateSpace = StateSpace(publisherManager)
            setSerialCommManager(SerialCommManager(usbSerial, batteryData, wheelData))
            super.onSerialReady(usbSerial)
        }
    }

    override fun onOutputsReady() {
        Logger.d(TAG, "onOutputsReady")
        /*------------------------------------------------------------------------------
        ------------------------------ Set MetaParameters ------------------------------
        --------------------------------------------------------------------------------
         */
        // Note this whole block is not in onCreate because `outputs` is not initialized until onSerialReady is called
        val metaParameters = MetaParameters(
            this, 100, maxTimeStepCount,
            100, maxEpisodeCount, null,
            timeStepDataBuffer, getOutputs(), 1
        )
        /*------------------------------------------------------------------------------
        ------------------------------ Initialize and Start Trial ----------------------
        --------------------------------------------------------------------------------
         */
        val myTrial = MyTrial(this, guiUpdater!!, metaParameters, actionSpace!!, stateSpace!!)
        myTrial.startTrail()
    }
}

