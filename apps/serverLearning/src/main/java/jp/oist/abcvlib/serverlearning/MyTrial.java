package jp.oist.abcvlib.serverlearning;

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
import jp.oist.abcvlib.util.SocketListener;

public class MyTrial extends Trial implements ActionSelector{

    public MyTrial(MetaParameters metaParameters, ActionSpace actionSpace, StateSpace stateSpace) {
        super(metaParameters, actionSpace, stateSpace);
    }

    @Override
    public void forward(TimeStepDataBuffer.TimeStepData data) {
        MotionAction motionAction;
        CommAction commAction;

        // Use data as input to your policy and select action here
        // Just using first actions of each set as an example but this should be replaced by your policy's decision process
        motionAction = getMotionActionSet().getMotionActions()[0];
        commAction = getCommActionSet().getCommActions()[0];

        // Add your selected actions to the TimeStepDataBuffer for record
        data.getActions().add(motionAction, commAction);
        Logger.i("myTrail", "Current motionAction: " + motionAction.getActionName());
        outputs.setWheelOutput(motionAction.getLeftWheelPWM(),
                motionAction.getRightWheelPWM(),
                motionAction.getLeftWheelBrake(),
                motionAction.getRightWheelBrake());
    }

    // If you want to do things at the start/end of the episode/trail you can override these methods from Trail

    @Override
    protected void startTrail() {
        // Do stuff here
        Logger.v("MyTrail", "startTrail");
        super.startTrail();
    }

    @Override
    public void startEpisode() {
        // Do stuff here
        Logger.v("MyTrail", "startEpisode");
        super.startEpisode();
    }

    @Override
    protected void endEpisode() throws BrokenBarrierException, InterruptedException, IOException, RecordingWithoutTimeStepBufferException, ExecutionException {
        // Do stuff here
        Logger.v("MyTrail", "endEpisode");
        super.endEpisode();
    }

    @Override
    protected void endTrial() throws RecordingWithoutTimeStepBufferException, InterruptedException {
        // Do stuff here
        Logger.v("MyTrail", "endTrail");
        outputs.setWheelOutput(0,0,true,true);
        super.endTrial();
    }
}
