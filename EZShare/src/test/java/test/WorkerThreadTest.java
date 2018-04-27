package test;

import EZShare.Server;
import EZShare.server.FileList;
import EZShare.server.ServerList;
import EZShare.server.WorkerThread;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Wenhao Zhao
 */
public class WorkerThreadTest {

    /* Please configure an existing and accessible file path before commencing testing. */
    protected final String existingFilePath = "file:///Users/nek/.bash_profile";
    
    protected WorkerThread w = null;
    protected List<String> outputJsons = null;

    protected void refreshWorkerThread() {
        try {
            w = new WorkerThread(new Socket(), new FileList(), new ServerList(true), false);
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
    }
    
    protected List<String> receptionTest(String inputFileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(System.getProperty("user.dir") + "/src/test/java/test/jsons/" + inputFileName));
        String json = "";
        for (String line = br.readLine(); line != null; line = br.readLine()) {
            json += line;
            json += '\n';
        }
        System.out.println(json);
        return w.reception(json);
    }
    
    protected List<String> receptionShareOrFetchTest(String inputFileName, String filePath, String secret) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(System.getProperty("user.dir") + "/src/test/java/test/jsons/" + inputFileName));
        String json = "";
        for (String line = br.readLine(); line != null; line = br.readLine()) {
            json += line;
            json += '\n';
        }

        json = json.replace("abcdefghijklmnopqrstuvwxyz0123456789", secret);
        json = json.replace("file:///", filePath);
        
        System.out.println(json);
        return w.reception(json);
    }
    
    protected void checkIndexContentAndDisplay(int index, String subMessageContent) {
        System.out.println(outputJsons.get(index));
        Assert.assertEquals(true, outputJsons.get(index).contains(subMessageContent));
    }
    
    /*
        PUBLISH
    */
    @Test
    public void publishSuccess() {
        try {
            refreshWorkerThread();
            outputJsons = receptionTest("PublishSuccess");
            checkIndexContentAndDisplay(0, "success");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }

    @Test
    public void publishRulesBroken() {
        try {
            refreshWorkerThread();
            /* Execute a successful PUBLISH operation beforehand */ 
            receptionTest("PublishSuccess");
            /* Same channel and URI but different owner */
            outputJsons = receptionTest("PublishRulesBroken");
            checkIndexContentAndDisplay(0, "cannot publish resource");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void publishInvalid() {
        try {
            refreshWorkerThread();
            /* "owner" is "*" */
            outputJsons = receptionTest("PublishInvalid");
            checkIndexContentAndDisplay(0, "invalid resource");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void publishMissingResource() {
        try {
            refreshWorkerThread();
            /* Field "resource" is missing */
            outputJsons = receptionTest("PublishMissingResource");
            checkIndexContentAndDisplay(0, "missing resource");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    /*
        GENERIC
    */
    @Test
    public void genericInvalidCommand() {
        try {
            refreshWorkerThread();
            /* "command" == "hehe", which is unreasonable */
            outputJsons = receptionTest("GenericInvalidCommand");
            checkIndexContentAndDisplay(0, "invalid command");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void genericMissingCommand() {
        try {
            refreshWorkerThread();
            /* Field "command" is missing */
            outputJsons = receptionTest("GenericMissingCommand");
            checkIndexContentAndDisplay(0, "missing or incorrect type for command");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    /*
        EXCHANGE
    */
    /*
    @Test
    public void exchangeSuccess() {
        try {
            refreshWorkerThread();
            outputJsons = receptionTest("ExchangeSuccess");
            checkIndexContentAndDisplay(0, "success");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    */
    
    @Test
    public void exchangeInvalid() {
        try {
            refreshWorkerThread();
            /* One of the port is invalid (> 65535) */
            outputJsons = receptionTest("ExchangeInvalid");
            checkIndexContentAndDisplay(0, "invalid server record");     // TO-DO: But in spec the errorMessage shall be "missing resourceTemplate"
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void exchangeMissing() {
        try {
            refreshWorkerThread();
            /* Field "servers" is missing */
            outputJsons = receptionTest("ExchangeMissing");
            checkIndexContentAndDisplay(0, "missing or invalid server list");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    /*
        QUERY
    */
    @Test
    public void querySuccess() {
        try {
            refreshWorkerThread();
            /* Prepare a resource to be queried */
            receptionTest("PublishSuccess");
            /* Query it! */
            outputJsons = receptionTest("QuerySuccess");
            checkIndexContentAndDisplay(2, "\"resultSize\":1");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void queryInvalid() {
        try {
            refreshWorkerThread();
            /* "owner" is "*" */
            outputJsons = receptionTest("QueryInvalid");
            checkIndexContentAndDisplay(0, "invalid resourceTemplate");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }

    @Test
    public void queryMissingResource() {
        try {
            refreshWorkerThread();
            /* "owner" is "*" */
            outputJsons = receptionTest("QueryMissingResource");
            checkIndexContentAndDisplay(0, "missing resourceTemplate");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    /*
        REMOVE
    */
    @Test
    public void removeSuccess() {
        try {
            refreshWorkerThread();
            /* Prepare a resource to be removed */
            receptionTest("PublishSuccess");
            /* Remove it! */
            outputJsons = receptionTest("RemoveSuccess");
            checkIndexContentAndDisplay(0, "success");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void removeInvalid() {
        try {
            refreshWorkerThread();
            /* URI here is invalid */
            outputJsons = receptionTest("RemoveInvalid");
            checkIndexContentAndDisplay(0, "invalid resource");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }

    @Test
    public void removeNotExisted() {
        try {
            refreshWorkerThread();
            /* Prepare a similar resource, but not to be removed */
            receptionTest("PublishSuccess");
            /* URI here is invalid */
            outputJsons = receptionTest("RemoveNotExisted");
            checkIndexContentAndDisplay(0, "cannot remove resource");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void removeMissingResource() {
        try {
            refreshWorkerThread();
            /* URI here is invalid */
            outputJsons = receptionTest("RemoveMissingResource");
            checkIndexContentAndDisplay(0, "missing resource");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    /*
        SHARE
    */
    @Test
    public void shareSuccess() {
       try {
            refreshWorkerThread();
            outputJsons = receptionShareOrFetchTest("ShareSuccess", existingFilePath, Server.SECRET);
            checkIndexContentAndDisplay(0, "success");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void shareIncorrectSecret() {
       try {
            refreshWorkerThread();
            outputJsons = receptionShareOrFetchTest("ShareIncorrectSecret", existingFilePath, "9527");
            checkIndexContentAndDisplay(0, "incorrect secret");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void shareInvalid() {
       try {
            refreshWorkerThread();
            outputJsons = receptionShareOrFetchTest("ShareIncorrectSecret", "http://User", Server.SECRET);
            checkIndexContentAndDisplay(0, "invalid resource");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }

    @Test
    public void shareMissingResource() {
       try {
            refreshWorkerThread();
            outputJsons = receptionShareOrFetchTest("ShareMissingResource", "", Server.SECRET);
            checkIndexContentAndDisplay(0, "missing resource and/or secret");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void shareRulesBroken() {
       try {
            refreshWorkerThread();
            outputJsons = receptionShareOrFetchTest("ShareRulesBroken", existingFilePath + "f", Server.SECRET);
            checkIndexContentAndDisplay(0, "cannot share resource");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    /*
        FETCH
    */
    @Test
    public void fetchSuccess() {
       try {
            refreshWorkerThread();
            /* Prepare a file to be fetched */
            receptionShareOrFetchTest("ShareSuccess", existingFilePath, Server.SECRET);
            /* Fetch it! */
            outputJsons = receptionShareOrFetchTest("FetchSuccess", existingFilePath, "何と無く");
            checkIndexContentAndDisplay(2, existingFilePath);
            checkIndexContentAndDisplay(3, "\"resultSize\":1");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }  

    @Test
    public void fetchInvalidResource() {
       try {
            refreshWorkerThread();
            outputJsons = receptionTest("FetchInvalidResource");
            checkIndexContentAndDisplay(0, "invalid resourceTemplate");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }
    
    @Test
    public void fetchMissingResource() {
       try {
            refreshWorkerThread();
            outputJsons = receptionTest("FetchMissingResource");
            checkIndexContentAndDisplay(0, "missing resourceTemplate");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }

    @Test
    public void fetchNoMatchedFile() {
       try {
            refreshWorkerThread();
            /* Prepare a similar file, but not to be fetched */
            receptionShareOrFetchTest("ShareSuccess", existingFilePath, Server.SECRET);
            /* Fetch it! */
            outputJsons = receptionShareOrFetchTest("FetchNoMatchedFile", existingFilePath + "f", "何と無く");
            checkIndexContentAndDisplay(1, "\"resultSize\":0");
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } 
    }     
}
