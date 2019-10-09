package org.scriptable;

/**
 * Rhino servlet lifecycle listener
 */

import javax.servlet.annotation.WebListener;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;

@WebListener // make it automatically discovered
public final class RhinoContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        synchronized(RhinoContextListener.class) {

            for (ExecutorService es: executors) {
                es.shutdownNow();
            }
            executors.clear();

            for (RhinoHttpRequest.JobQueue jq: jobQueues) {
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

    private static HashSet<RhinoHttpRequest.JobQueue> jobQueues = new HashSet<RhinoHttpRequest.JobQueue>();

    public static synchronized void registerJobQueue(RhinoHttpRequest.JobQueue jq) {
        jobQueues.add(jq);
    }

//    public final static class JobQueue { <- move to rhinohttprequest, also initialize originating request object in the job thread so _r.r is available
}

