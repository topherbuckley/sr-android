package jp.oist.abcvlib.core.outputs;

import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.util.ErrorHandler;
import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;
import jp.oist.abcvlib.util.ScheduledExecutorServiceWithException;

public abstract class AbcvlibController implements Runnable{

    private String name;
    private int threadCount = 1;
    private int threadPriority = Thread.MAX_PRIORITY;
    private int initDelay;
    private int timeStep;
    private TimeUnit timeUnit;
    private ScheduledExecutorServiceWithException executor;
    private final String TAG = getClass().getName();
    private boolean isRunning = false;
    private Outputs outputs;

    public AbcvlibController(Outputs outputs){
        if (outputs == null){
            ErrorHandler.eLog(TAG, "Outputs object cannot be null.", new Exception(), true);
        }
        this.outputs = outputs;
    }

    // Deprecated default constructor. You must provide an Outputs object
    @Deprecated
    public AbcvlibController(){};

    public AbcvlibController setName(String name) {
        this.name = name;
        return this;
    }

    public AbcvlibController setThreadCount(int threadCount) {
        this.threadCount = threadCount;
        return this;
    }

    public AbcvlibController setThreadPriority(int threadPriority) {
        this.threadPriority = threadPriority;
        return this;
    }

    public AbcvlibController setInitDelay(int initDelay) {
        this.initDelay = initDelay;
        return this;
    }

    public AbcvlibController setTimestep(int timeStep) {
        this.timeStep = timeStep;
        return this;
    }

    public AbcvlibController setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
        return this;
    }

    public void startController(){
        isRunning = true;
    }

    public void stopController(){
        setOutput(0,0);
        isRunning = false;
    }

    public Output output = new Output();

    synchronized Output getOutput(){
        return output;
    }

    synchronized float getLeftOutput(){
        return output.left;
    }

    synchronized float getRightOutput(){
        return output.right;
    }

    protected synchronized void setOutput(float left, float right){
        output.left = left;
        output.right = right;
        if (isRunning){
            outputs.setWheelOutput(left, right, false, false);
        }
    }

    @Override
    public void run() {
        ErrorHandler.eLog(TAG, "You must override the run method within your custom AbcvlibController.", new Exception(),false);
    }

    public static class Output{
        public float left;
        public float right;
    }

    public synchronized boolean isRunning(){
        return isRunning;
    }
}
