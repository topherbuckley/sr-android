package jp.oist.abcvlib.core.learning;

import android.content.Context;
import jp.oist.abcvlib.util.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.core.inputs.PublisherManager;
import jp.oist.abcvlib.core.inputs.TimeStepDataBuffer;
import jp.oist.abcvlib.core.outputs.ActionSelector;
import jp.oist.abcvlib.core.outputs.Outputs;
import jp.oist.abcvlib.util.ErrorHandler;
import jp.oist.abcvlib.util.FileOps;
import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;
import jp.oist.abcvlib.util.RecordingWithoutTimeStepBufferException;
import jp.oist.abcvlib.util.ScheduledExecutorServiceWithException;
import jp.oist.abcvlib.util.SerialCommManager;
import jp.oist.abcvlib.util.SocketListener;

public class Trial implements Runnable, ActionSelector, SocketListener {
    private final Context context;
    private int timeStepLength = 50;
    protected int maxTimeStepCount = 100;
    protected int timeStep = 0;
    private boolean lastEpisode = false; // Use to trigger MainActivity to stop generating episodes
    private boolean lastTimestep = false; // Use to trigger MainActivity to stop generating timesteps for a single episode
    private int reward = 0;
    private int maxReward = 100;
    protected int maxEpisodeCount = 3;
    protected int episodeCount = 0;
    private final CommActionSpace commActionSpace;
    private final MotionActionSpace motionActionSpace;
    private final TimeStepDataBuffer timeStepDataBuffer;
    private final String TAG = getClass().toString();
    private ScheduledFuture<?> timeStepDataAssemblerFuture;
    private final ScheduledExecutorServiceWithException executor;
    private final PublisherManager publisherManager;
    private FlatbufferAssembler flatbufferAssembler;
    protected final Outputs outputs;
    protected final int robotID;

    public Trial(MetaParameters metaParameters, ActionSpace actionSpace,
                 StateSpace stateSpace){
        this.context = metaParameters.context;
        this.outputs = metaParameters.outputs;
        this.robotID = metaParameters.robotID;
        this.timeStepDataBuffer = metaParameters.timeStepDataBuffer;
        this.timeStepLength = metaParameters.timeStepLength;
        this.maxTimeStepCount = metaParameters.maxTimeStepCount;
        this.maxReward = metaParameters.maxReward;
        this.maxEpisodeCount = metaParameters.maxEpisodeCount;
        this.flatbufferAssembler = new FlatbufferAssembler(this,
                metaParameters.inetSocketAddress, this, timeStepDataBuffer, robotID);
        this.motionActionSpace = actionSpace.motionActionSpace;
        this.commActionSpace = actionSpace.commActionSpace;
        this.publisherManager = stateSpace.publisherManager;

        int threads = 1;
        executor = new ScheduledExecutorServiceWithException(threads, new ProcessPriorityThreadFactory(1, "trail"));
    }

    public void setFlatbufferAssembler(FlatbufferAssembler flatbufferAssembler){
        this.flatbufferAssembler = flatbufferAssembler;
    }
    
    protected void startTrail(){
        publisherManager.initializePublishers();
        publisherManager.startPublishers();
        startEpisode();
        startPublishers();
    }

    protected void startPublishers() {
        timeStepDataAssemblerFuture = executor.scheduleAtFixedRate(this, getTimeStepLength(), getTimeStepLength(), TimeUnit.MILLISECONDS);
    }

    protected void startEpisode(){
        if (flatbufferAssembler != null){
            flatbufferAssembler.startEpisode();
        }
    }

    @Override
    public void run() {
        incrementTimeStep();
        // Moves timeStepDataBuffer.writeData to readData and nulls out the writeData for new data
        timeStepDataBuffer.nextTimeStep();

        // Choose action wte based on current timestep data
        forward(timeStepDataBuffer.getReadData());

        // Add timestep and return int representing offset in flatbuffer
        try {
            flatbufferAssembler.addTimeStep(timeStepDataBuffer.getReadIndex());
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }

        // If some criteria met, end episode.
        if (isLastTimestep()){
            try {
                endEpisode();
                if(isLastEpisode()){
                    endTrial();
                }else {
                    startEpisode();
                    resumePublishers();
                }
            } catch (BrokenBarrierException | InterruptedException | IOException | RecordingWithoutTimeStepBufferException | ExecutionException e) {
                ErrorHandler.eLog(TAG, "Error when trying to end episode or trail", e, true);
            }
        }
    }

    protected void pausePublishers() throws RecordingWithoutTimeStepBufferException, InterruptedException {
        publisherManager.pausePublishers();
        timeStepDataAssemblerFuture.cancel(false);
    }

    protected void resumePublishers(){
        publisherManager.resumePublishers();
        timeStepDataAssemblerFuture = executor.scheduleAtFixedRate(this, getTimeStepLength(), getTimeStepLength(), TimeUnit.MILLISECONDS);
    }

    // End episode after some reward has been acheived or maxtimesteps has been reached
    protected void endEpisode() throws BrokenBarrierException, InterruptedException, IOException, RecordingWithoutTimeStepBufferException, ExecutionException {
        Logger.d("Episode", "End of episode:" + getEpisodeCount());
        // Waits for all image compression to finish prior to finishing flatbuffer
        long start = System.nanoTime();
        synchronized (timeStepDataBuffer.imgCompFuturesEpisode){
            for (Collection<Future<?>> timestepFutures: timeStepDataBuffer.imgCompFuturesEpisode){
                synchronized (timestepFutures){
                    for (Future<?> future:timestepFutures){
                        future.get();
                    }
                }
            }
        }
        synchronized (flatbufferAssembler.flatbufferWriteFutures){
            // Waits for all timestep flatbuffer writes to finish prior to finishing flatbuffer
            for (Future<?> future:flatbufferAssembler.flatbufferWriteFutures){
                future.get();
            }
        }
        long stop = System.nanoTime();
        long timediff = stop - start;
        flatbufferAssembler.endEpisode();
        pausePublishers();
        setTimeStep(0);
        setLastTimestep(false);
        incrementEpisodeCount();
        timeStepDataBuffer.nextTimeStep();

        flatbufferAssembler.sendToServer();
    }

    protected void endTrial() throws RecordingWithoutTimeStepBufferException, InterruptedException {
        Logger.i(TAG, "Need to handle end of trail here");
        pausePublishers();
        publisherManager.stopPublishers();
        timeStepDataAssemblerFuture.cancel(false);
    }

    /**
     * This method is called at the end of each timestep within your class extending Trail and implementing ActionSelector
     * @param data All the data collected from the most recent timestep
     */
    public void forward(TimeStepDataBuffer.TimeStepData data){};

    @Override
    public void onServerReadSuccess(JSONObject jsonHeader, ByteBuffer msgFromServer) {
        // Parse whatever you sent from python here
        //loadMappedFile...
        try {
            if (jsonHeader.get("content-encoding").equals("utf-8")){
                Logger.d(TAG, "Received text message from server");
                msgFromServer.flip();
                byte[] bytes = new byte[(int) jsonHeader.get("content-length")];
                msgFromServer.get(bytes);
                String msg = new String(bytes, StandardCharsets.UTF_8);
                Logger.d(TAG, "Server says, \"" + msg + "\"");
            }
            else if (jsonHeader.get("content-encoding").equals("binary")){
                if (jsonHeader.get("content-type").equals("files")){
                    Logger.d(TAG, "Writing files to disk");
                    JSONArray fileNames = (JSONArray) jsonHeader.get("file-names");
                    JSONArray fileLengths = (JSONArray) jsonHeader.get("file-lengths");

                    msgFromServer.flip();

                    for (int i = 0; i < fileNames.length(); i++){
                        byte[] bytes = new byte[fileLengths.getInt(i)];
                        msgFromServer.get(bytes);
                        FileOps.savedata(context, bytes, "models", fileNames.getString(i));
                    }
                }
                else if (jsonHeader.get("content-type").equals("flatbuffer")){
                    //todo
                }
                else if (jsonHeader.get("content-type").equals("json")){
                    //todo
                }
            }else{
                Logger.d(TAG, "Data from server does not contain modelVector content. Be sure to set content-encoding to \"modelVector\" in the python jsonHeader");
            }
        } catch (JSONException e) {
            ErrorHandler.eLog(TAG, "Something wrong with parsing the JSONheader from python", e, true);
        }
    }

    public int getTimeStepLength() {
        return timeStepLength;
    }

    public TimeStepDataBuffer getTimeStepDataBuffer() {
        return timeStepDataBuffer;
    }

    public int getEpisodeCount() {
        return episodeCount;
    }

    public int getTimeStep() {
        return timeStep;
    }

    public boolean isLastEpisode() {
        return (episodeCount >= maxEpisodeCount) || lastEpisode;
    }

    public boolean isLastTimestep() {
        return (timeStep >= maxTimeStepCount) || lastTimestep;
    }

    public int getMaxEpisodecount() {
        return maxEpisodeCount;
    }

    public int getMaxTimeStepCount() {
        return maxTimeStepCount;
    }

    public int getReward() {
        return reward;
    }

    public int getMaxReward() {
        return maxReward;
    }

    public void incrementEpisodeCount() {
        episodeCount++;
    }

    public void incrementTimeStep(){timeStep++;}

    public void setTimeStep(int timeStep) {
        this.timeStep = timeStep;
    }

    public void setLastEpisode(boolean lastEpisode) {
        this.lastEpisode = lastEpisode;
    }

    public MotionActionSpace getMotionActionSet() {
        return motionActionSpace;
    }

    public CommActionSpace getCommActionSet() {
        return commActionSpace;
    }

    public void setLastTimestep(boolean lastTimestep) {
        this.lastTimestep = lastTimestep;
    }
}
