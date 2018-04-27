package EZShare.server;

import EZShare.Server;
import EZShare.message.*;
import com.google.gson.Gson;

import javax.net.ssl.SSLSocket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Wenhao Zhao
 */
public class ServerList {
    private Gson gson = new Gson();

    private boolean secure;

    public static final int SERVER_TIMEOUT = (int) Server.EXCHANGE_PERIOD / 2;

    private final List<Host> serverList = new ArrayList<>();


    public ServerList(boolean seucre) {
        this.secure = seucre;
    }
    /*
    public ServerList() {
        // Add a default dummy host (which is of course not available)
        Host host = new Host("localhorse", 9527);
        this.serverList.add(host);
    }
    */

    public synchronized List<Host> getServerList() {
        return serverList;
    }

    public synchronized int updateServerList(List<Host> inputServerList) {

        int addCount = 0;
        for (Host inputHost : inputServerList) {
            /* 
                Discard host if (1) already in the list (2) is a local address
            */
            if (!containsHost(inputHost) &&
                    !(isMyIpAddress(inputHost.getHostname()) && (inputHost.getPort() == Server.PORT || inputHost.getPort() == Server.SPORT))) {

                openSubscribeRelay(inputHost);

                serverList.add(inputHost);

                ++addCount;
            }
        }
        return addCount;
    }

    public synchronized void regularExchange() {

        if (serverList.size() > 0) {
            int randomIndex = ThreadLocalRandom.current().nextInt(0, serverList.size());
            Host randomHost = serverList.get(randomIndex);

            Socket socket = null;

            try {
                // Need SSL!!!
                if (secure) {
                    socket = Server.context.getSocketFactory().createSocket();
                } else {
                    socket = new Socket();
                }
                /* Set timeout for connection establishment, throwing ConnectException */
                socket.connect(new InetSocketAddress(randomHost.getHostname(), randomHost.getPort()), SERVER_TIMEOUT);


                /* Set timeout for read() (also readUTF()!), throwing SocketTimeoutException */
                socket.setSoTimeout(SERVER_TIMEOUT);

                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());

                Server.logger.fine("Regular EXCHANGE to " + socket.getRemoteSocketAddress());

                ExchangeMessage exchangeMessage = new ExchangeMessage(serverList);

                String JSON = new Gson().toJson(exchangeMessage);
                output.writeUTF(JSON);
                output.flush();

                String response = input.readUTF();

                if (response.contains("error"))
                    Server.logger.warning("RECEIVED : " + response);
                if (response.contains("success"))
                    Server.logger.fine("RECEIVED : " + response);
            } catch (ConnectException ex) {
                Server.logger.warning(randomHost.toString() + " connection timeout");
                removeServer(randomHost, secure);
            } catch (SocketTimeoutException ex) {
                Server.logger.warning(randomHost.toString() + " readUTF() timeout");
                removeServer(randomHost, secure);
            } catch (IOException ex) {
                /* Unclassified exception */
                Server.logger.warning(randomHost.toString() + " IOException");
                removeServer(randomHost, secure);
            } finally {
                try {
                    if (socket != null)
                        socket.close();
                } catch (IOException e) {
                    Server.logger.warning("IOException! Disconnect!");
                }
            }
        }
    }

    public synchronized void removeServer(Host inputHost, boolean secure) {

        closeSubscribeRelay(inputHost);

        serverList.remove(inputHost);

    }

    private synchronized boolean containsHost(Host inputHost) {

        for (Host host : serverList) {
            if (host.getHostname().equals(inputHost.getHostname()) && host.getPort().equals(inputHost.getPort())) {
                return true;
            }
        }
        return false;
    }

    private boolean isMyIpAddress(String ipAddress) {
        InetAddress addr;
        try {
            addr = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException ex) {
            /* False-positive!!! If error occurred, the address should not be added to the server list. */
            return true;
        }
        /* Check if the address is a valid special local or loop back */
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
            return true;
        }

        /* Check if the address is defined on any interface */
        try {
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (SocketException e) {
            return false;
        }
    }


    public void openSubscribeRelay(Host target) {
        Socket socket = null;
        ConcurrentHashMap<Host, Socket> relay;

        try {
            if (secure) {
                socket = Server.context.getSocketFactory().createSocket();
                relay = Server.secure_relay;
            } else {
                socket = new Socket();
                relay = Server.unsecure_relay;
            }
                /* Set timeout for connection establishment, throwing ConnectException */
            socket.connect(new InetSocketAddress(target.getHostname(), target.getPort()), SERVER_TIMEOUT);

            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            //DataInputStream inputStream = new DataInputStream(socket.getInputStream());


            //traverse all subscribers
            for (Map.Entry<Socket, Subscription> subscriber : Server.subscriptions.entrySet()) {
                //get all subscribe message of this subscriber
                ConcurrentHashMap<SubscribeMessage, Integer> messages = subscriber.getValue().getSubscribeMessage();
                for (Map.Entry<SubscribeMessage, Integer> subscription : messages.entrySet()) {
                    //if this message have relay=true
                    if (subscription.getKey().isRelay()) {

                        SubscribeMessage forwarded = new SubscribeMessage(false, subscription.getKey().getId(), subscription.getKey().getResourceTemplate());

                        String JSON = gson.toJson(forwarded, SubscribeMessage.class);
                        outputStream.writeUTF(JSON);
                        outputStream.flush();

                        /*
                        String response = inputStream.readUTF();

                        if(response.contains("success")){
                            Server.logger.warning("From: " +subscriber.getKey().getRemoteSocketAddress() +
                                                        " relayed to: "+target.toString()+
                                                        " for "+subscription.getKey().getId());
                        }
                        */

                    }

                }


            }
            relay.put(target, socket);
            Server.logger.info("relay connection opened " + target.toString());

        } catch (IOException e) {
            Server.logger.warning("IOException when subscribe relay to " + target.toString());
        }


    }

    public synchronized void closeSubscribeRelay(Host target) {
        Socket socket = null;
        ConcurrentHashMap<Host, Socket> relay;

        try {
            if (secure) {
                relay = Server.secure_relay;
            } else {
                relay = Server.unsecure_relay;
            }

            socket = relay.get(target);

            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());


            //traverse all subscribers
            for (Map.Entry<Socket, Subscription> subscriber : Server.subscriptions.entrySet()) {
                //get all subscribe message of this subscriber
                ConcurrentHashMap<SubscribeMessage, Integer> messages = subscriber.getValue().getSubscribeMessage();
                for (Map.Entry<SubscribeMessage, Integer> subscription : messages.entrySet()) {
                    //if this message have relay=true
                    if (subscription.getKey().isRelay()) {
                        String JSON = gson.toJson(new UnsubscribeMessage(subscription.getKey().getId()), UnsubscribeMessage.class);
                        outputStream.writeUTF(JSON);
                        outputStream.flush();

                        /*
                        String response = inputStream.readUTF();

                        if (response.contains("resultSize")) {
                            Server.logger.warning("Terminate from: " + subscriber.getKey().getRemoteSocketAddress() +
                                    " relayed to: " + target.toString() +
                                    " for " + subscription.getKey().getId());
                        }
                        */
                    }

                }


            }
            relay.remove(target);


        } catch (IOException e) {
            Server.logger.warning("IOException when subscribe relay to " + target.toString());
        }
    }

    public synchronized void doMessageRelay(String JSON) {

        ConcurrentHashMap<Host, Socket> relay;

        if (secure) {
            relay = Server.secure_relay;
        } else {
            relay = Server.unsecure_relay;
        }


        try {
            for (Map.Entry<Host, Socket> entry : relay.entrySet()) {
                DataOutputStream outputStream = new DataOutputStream(entry.getValue().getOutputStream());

                entry.getValue().setSoTimeout(3000);

                outputStream.writeUTF(JSON);
                outputStream.flush();
                Server.logger.fine("message relayed");

            }

        } catch (IOException e) {
            Server.logger.warning("IOException when subscribe relay: " + e.getMessage());
            e.printStackTrace();
            System.out.println("JSON : " + JSON);
        }

    }

    public synchronized void refreshAllRelay() {
        ConcurrentHashMap<Host, Socket> relay;

        if (secure) {
            relay = Server.secure_relay;
        } else {
            relay = Server.unsecure_relay;
        }

        relay = new ConcurrentHashMap<>();

        for (Host h : this.serverList) {
            openSubscribeRelay(h);
        }

    }


}
