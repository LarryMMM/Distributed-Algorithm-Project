package test;

import EZShare.server.FileList;
import EZShare.server.ServerList;
import EZShare.server.WorkerThread;
import org.junit.Assert;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by Wenhao Zhao on 2017/5/26.
 */
public class SecureWorkerThreadTest extends WorkerThreadTest {
    protected void refreshWorkerThread() {
        try {
            w = new WorkerThread(new Socket(), new FileList(), new ServerList(true), true);
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
    }
}
