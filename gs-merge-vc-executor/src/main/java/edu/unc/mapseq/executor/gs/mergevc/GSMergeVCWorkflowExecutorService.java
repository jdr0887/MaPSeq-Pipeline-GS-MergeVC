package edu.unc.mapseq.executor.gs.mergevc;

import java.util.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GSMergeVCWorkflowExecutorService {

    private final Logger logger = LoggerFactory.getLogger(GSMergeVCWorkflowExecutorService.class);

    private final Timer mainTimer = new Timer();

    private GSMergeVCWorkflowExecutorTask task;

    private Long period = 5L;

    public GSMergeVCWorkflowExecutorService() {
        super();
    }

    public void start() throws Exception {
        logger.info("ENTERING start()");
        long delay = 1 * 60 * 1000;
        mainTimer.scheduleAtFixedRate(task, delay, period * 60 * 1000);
    }

    public void stop() throws Exception {
        logger.info("ENTERING stop()");
        mainTimer.purge();
        mainTimer.cancel();
    }

    public GSMergeVCWorkflowExecutorTask getTask() {
        return task;
    }

    public void setTask(GSMergeVCWorkflowExecutorTask task) {
        this.task = task;
    }

    public Long getPeriod() {
        return period;
    }

    public void setPeriod(Long period) {
        this.period = period;
    }

}
