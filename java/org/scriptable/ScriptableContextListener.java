package org.scriptable;

/**
 * Scriptable servlet lifecycle listener
 */

import javax.servlet.annotation.WebListener;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;

@WebListener // make it automatically discovered
public final class ScriptableContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        synchronized(ScriptableContextListener.class) {

            for (ExecutorService es: executors) {
                es.shutdownNow();
            }
            executors.clear();

            for (ScriptableHttpRequest.JobQueue jq: jobQueues) {
                jq.shutdown();
            }
            jobQueues.clear();

            // give threads a chance to react
            try { Thread.sleep(400); } catch (InterruptedException e) {}
        }
    }

    private static HashSet<ExecutorService> executors = new HashSet<ExecutorService>();

    public static synchronized void registerThreadPool(ExecutorService es) {
        executors.add(es);
    }

    public static synchronized void unregisterThreadPool(ExecutorService es) {
        executors.remove(es);
    }

    private static HashSet<ScriptableHttpRequest.JobQueue> jobQueues = new HashSet<ScriptableHttpRequest.JobQueue>();

    public static synchronized void registerJobQueue(ScriptableHttpRequest.JobQueue jq) {
        jobQueues.add(jq);
    }

//    public final static class JobQueue { <- move to ScriptableHttpRequest, also initialize originating request object in the job thread so _r.r is available
}

