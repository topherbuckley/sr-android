package jp.oist.abcvlib.basicassembler;

import android.content.Context;
import android.os.Handler;
import jp.oist.abcvlib.util.Logger;

import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ExecutionException;

import jp.oist.abcvlib.core.inputs.TimeStepDataBuffer;
import jp.oist.abcvlib.core.learning.ActionSpace;
import jp.oist.abcvlib.core.learning.CommAction;
import jp.oist.abcvlib.core.learning.MetaParameters;
import jp.oist.abcvlib.core.learning.MotionAction;
import jp.oist.abcvlib.core.learning.StateSpace;
import jp.oist.abcvlib.core.learning.Trial;
import jp.oist.abcvlib.core.outputs.ActionSelector;
import jp.oist.abcvlib.util.RecordingWithoutTimeStepBufferException;

public class MyTrial extends Trial implements ActionSelector{
    private int reward = 0;
    private final Handler mainHandler;
    private final GuiUpdater guiUpdater;

    public MyTrial(Context context, GuiUpdater guiUpdater, MetaParameters metaParameters,
                   ActionSpace actionSpace, StateSpace stateSpace) {
        super(metaParameters, actionSpace, stateSpace);
        mainHandler = new Handler(context.getMainLooper());
        this.guiUpdater = guiUpdater;
    }

    @Override
    public void forward(TimeStepDataBuffer.TimeStepData data) {
        MotionAction motionAction;
        CommAction commAction;

        // Use data as input to your policy and select action here
        // Just using default actions of each set as an example but this
        // should be replaced by your policy's decision process
        motionAction = getMotionActionSet().getMotionActions()[1];
        commAction = getCommActionSet().getCommActions()[0];

        // Add your selected actions to the TimeStepDataBuffer for record
        data.getActions().add(motionAction, commAction);

        Logger.i("myTrail", "Current motionAction: " + motionAction.getActionName());
        outputs.setWheelOutput(motionAction.getLeftWheelPWM(),
                motionAction.getRightWheelPWM(),
                motionAction.getLeftWheelBrake(),
                motionAction.getRightWheelBrake());

        // Note this will never be called when the myStepHandler.getTimeStep() >= myStepHandler.getMaxTimeStep() as the forward method will no longer be called
        mainHandler.post(() -> guiUpdater.updateGUIValues(data, getTimeStep(), getEpisodeCount()));
    }

    // If you want to do things at the start/end of the episode/trail you can override these methods from Trail

    @Override
    protected void startTrail() {
        // Do stuff here
        super.startTrail();
    }

    @Override
    public void startEpisode() {
        // Do stuff here
        super.startEpisode();
    }

    @Override
    protected void endEpisode() throws BrokenBarrierException, InterruptedException, IOException, RecordingWithoutTimeStepBufferException, ExecutionException {
        // Do stuff here
        super.endEpisode();
    }

    @Override
    protected void endTrial() throws RecordingWithoutTimeStepBufferException, InterruptedException {
        // Do stuff here
        super.endTrial();
    }

    @Override
    public boolean isLastTimestep() {
        boolean result = false;
        result = timeStep >= maxTimeStepCount;
        // Do some logic here to modify result based on how you want to determine if this is the last timestep (e.g. currentTimeStep > maxTimeStep, reward > maxReward, etc.).
        return result;
    }

    @Override
    public boolean isLastEpisode() {
        boolean result = false;
        result = episodeCount >= maxEpisodeCount;
        // Do some logic here to modify result based on how you want to determine if this is the last episode (e.g. currentEpisode > maxEpisode, etc.).
        return result;
    }
}
