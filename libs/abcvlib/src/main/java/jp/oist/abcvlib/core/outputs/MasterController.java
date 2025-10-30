package jp.oist.abcvlib.core.outputs;

import jp.oist.abcvlib.util.Logger;

import java.util.concurrent.CopyOnWriteArrayList;

import ioio.lib.api.exception.ConnectionLostException;
import jp.oist.abcvlib.util.SerialCommManager;
import jp.oist.abcvlib.core.Switches;

/*
Currently deprecated. This class was intended to be used as a way to control multiple controllers
This needs to be updated to include a way to control the motors via the SerialCommManager class
instead of the deprecated SerialCommManager class
 */
public class MasterController extends AbcvlibController{

    private final String TAG = this.getClass().getName();

    private final Switches switches;
    private final CopyOnWriteArrayList<AbcvlibController> controllers = new CopyOnWriteArrayList<>();
    private final SerialCommManager serialCommManager;

    MasterController(Switches switches, SerialCommManager serialCommManager){
        this.switches = switches;
        this.serialCommManager = serialCommManager;
    }

    @Override
    public void run() {

        setOutput(0, 0);
        for (AbcvlibController controller : controllers){

            if (switches.loggerOn){
                Logger.v(TAG, controller.toString() + "output:" + controller.getOutput().left);
            }

            setOutput((output.left + controller.getOutput().left), (output.right + controller.getOutput().right));
        }

        if (switches.loggerOn){
            Logger.v("abcvlib", "grandController output:" + output.left);
        }

        serialCommManager.setMotorLevels(output.left, output.right, false, false);
    }

    public void addController(AbcvlibController controller){
        this.controllers.add(controller);
    }

    public void removeController(AbcvlibController controller){
        if (controllers.contains(controller)){
            this.controllers.remove(controller);
        }
    }

    public void stopAllControllers(){
        for (AbcvlibController controller : controllers){
            controller.setOutput(0, 0);
        }
    }
}
