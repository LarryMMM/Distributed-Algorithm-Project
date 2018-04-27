package EZShare.server;

import EZShare.Server;
import EZShare.message.ResourceTemplate;
import EZShare.message.SubscribeMessage;
import com.google.gson.Gson;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

/**
 * @author Ying Li
 */
public class FileList {

    private List<ResourceTemplate> resourceTemplateList = new ArrayList<>();
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private Gson gson = new Gson();


    /**
     * Send notification to socket which linked to the client that subscribed the relevant resources.
     *
     * @param candidate The published or shared resource.
     */
    public void sendNotification(ResourceTemplate candidate) {

        //Travers all unrelayed subscriptions.
        for (Map.Entry<Socket, Subscription> s : Server.subscriptions.entrySet()) {

            //get socket
            Socket socket = s.getKey();

            //note down whether the message has been sent to this socket
            boolean sent = false;

            //get query conditions
            for (Map.Entry<SubscribeMessage, Integer> entry : s.getValue().getSubscribeMessage().entrySet()) {

                ResourceTemplate query = entry.getKey().getResourceTemplate();

                //if the resource matches the subscription.
                if (query.match(candidate)) {
                    try {
                        if (!sent) {
                            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                            //send resource to that particular socket.

                            String c = gson.toJson(candidate);
                            ResourceTemplate encrypted_candidate = gson.fromJson(c, ResourceTemplate.class);

                            output.writeUTF(gson.toJson(encrypted_candidate, ResourceTemplate.class));
                            output.flush();
                            Server.logger.log(Level.FINE, "Matched resource sent:" + candidate, socket.getRemoteSocketAddress().toString());
                            //set sent to true to prevent send a same resource twice
                            sent = true;
                        }
                        //increase result size
                        Server.subscriptions.get(socket).addResult(entry.getKey().getId());
                        //break to avoid send one message several times


                    } catch (IOException e) {
                        Server.logger.log(Level.WARNING, "{0} IOException when sending subscribed resource! ", e.getMessage());
                    }
                }
            }
        }

    }


    /**
     * add a new file to filelist
     *
     * @param resourceTemplate Resource to be added.
     * @return boolean  Whether the resource is successfully added.
     */
    public boolean add(ResourceTemplate resourceTemplate) {
        lock.writeLock().lock();
        try {
            if (resourceTemplateList.isEmpty()) {
                resourceTemplateList.add(resourceTemplate);
                sendNotification(resourceTemplate);
                return true;
            } else {
                for (int i = 0; i < resourceTemplateList.size(); i++) {
                    ResourceTemplate f = resourceTemplateList.get(i);
                    if (f.getChannel().equals(resourceTemplate.getChannel()) && f.getUri().equals(resourceTemplate.getUri())) {
                        if (f.getOwner().equals(resourceTemplate.getOwner())) {
                            resourceTemplateList.set(i, resourceTemplate);
                            sendNotification(resourceTemplate);
                            return true;
                        }
                        return false;
                    }

                }
                resourceTemplateList.add(resourceTemplate);
                sendNotification(resourceTemplate);
                return true;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * delete a file from filelist
     *
     * @param resourceTemplate Resource to be removed.
     * @return boolean  Whether the resource is successfully removed.
     */
    public boolean remove(ResourceTemplate resourceTemplate) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < resourceTemplateList.size(); i++) {
                ResourceTemplate f = resourceTemplateList.get(i);
                if (f.getChannel().equals(resourceTemplate.getChannel()) && f.getUri().equals(resourceTemplate.getUri()) && f.getOwner().equals(resourceTemplate.getOwner())) {
                    resourceTemplateList.remove(i);
                    return true;
                }
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }


    /**
     * search a certain list of file by owner, uri and channel in filelist
     *
     * @param query Resource in query.
     * @return querylist    List of resources that match the query.
     */
    public List<ResourceTemplate> query(ResourceTemplate query) {
        lock.readLock().lock();
        List<ResourceTemplate> queryList = new ArrayList<>();
        try {
            for (ResourceTemplate candidate : resourceTemplateList) {
                if (query.match(candidate)) {
                    queryList.add(candidate);
                }
            }
            return queryList;
        } finally {
            lock.readLock().unlock();
        }
    }

    /*
        I guess the query rule of "fetch" is different from that of "query"?
    */
    public List<ResourceTemplate> fetch(ResourceTemplate query) {
        lock.readLock().lock();
        List<ResourceTemplate> fetch = new ArrayList<>();
        try {
            for (ResourceTemplate candidate : resourceTemplateList) {
                if (query.getChannel().equals(candidate.getChannel()) &&
                        query.getUri().equals(candidate.getUri())) {
                    fetch.add(candidate);
                    return fetch;
                }
            }
            return fetch;
        } finally {
            lock.readLock().unlock();
        }
    }
}
