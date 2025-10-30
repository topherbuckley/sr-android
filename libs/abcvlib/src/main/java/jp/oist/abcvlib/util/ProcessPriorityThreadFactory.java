package jp.oist.abcvlib.util;

import jp.oist.abcvlib.util.Logger;

import java.util.concurrent.ThreadFactory;

public final class ProcessPriorityThreadFactory implements ThreadFactory {

    private final int threadPriority;
    private final String threadName;
    private int threadCount = 0;

    public ProcessPriorityThreadFactory(int threadPriority, String threadName) {
        this.threadPriority = threadPriority;
        this.threadName = threadName;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
//        Logger.v("Threading", "Current Thread Priority: " + thread.getPriority());
//        Logger.v("Threading", "Current ThreadGroup Max Priority: " + thread.getThreadGroup().getMaxPriority());
        thread.setPriority(threadPriority);
//        Logger.v("Threading", "Newly set Thread Priority: " + thread.getPriority());
        thread.setName(threadName + "_" + threadCount);
        threadCount++;
        return thread;
    }

}
