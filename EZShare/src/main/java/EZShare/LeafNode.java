package EZShare;

import EZShare.log.LogCustomFormatter;
import EZShare.message.Host;
import EZShare.message.ResourceTemplate;
import EZShare.server.FileList;
import EZShare.server.ServerList;
import EZShare.server.Subscription;
import EZShare.server.WorkerThread;
import com.google.gson.Gson;
import org.apache.commons.cli.*;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import EZShare.log.LogCustomFormatter;
import EZShare.message.*;
import com.google.gson.Gson;
import org.apache.commons.cli.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
/**
 * @author Wenhao Zhao, Ying Li
 */
public class LeafNode {

    /* Default configuration */
    public static String HOST = "localhost";
    public static int PORT = 3780;
    public static int SPORT = 3781;
    public static final int MAX_THREAD_COUNT = 50;
    public static long EXCHANGE_PERIOD = 600000;
    public static long INTERVAL = 1000;
    public static String SECRET = random(26);

    /* Data structures and utilities */
    public static SSLContext context = null;
    public static final Logger logger = LogCustomFormatter.getLogger(LeafNode.class.getName());
    private static final FileList fileList = new FileList();
    private static final ServerList serverList = new ServerList(false);
    private static final ServerList secure_severList = new ServerList(true);
    private static final Gson gson = new Gson();

    /*
        Currently it is a simple fixed-volume thread pool.
        If no thread resource is available at the moment, it would be blocked until it could get one.
    */
    private static ExecutorService threadPool = Executors.newFixedThreadPool(MAX_THREAD_COUNT);

    /*
        A HashMap to record the mapping from a specified client to the starting time of its last connection
    */
    private static ConcurrentHashMap<String, Long> intervalLimit = new ConcurrentHashMap<>();

    /*
        Data structures for subscriptions and relayed subscriptions
    */
    public static ConcurrentHashMap<Socket, Subscription> subscriptions = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Host, Socket> secure_relay = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Host, Socket> unsecure_relay = new ConcurrentHashMap<>();

    /**
     * Construct command line options
     *
     * @return CommandLine options.
     */
    private static Options commandOptions() {
        //Build up command line options
        Options options = new Options();
        options.addOption("advertisedhostname", true, "advertised hostname");
        options.addOption("connectionintervallimit", true, "connection interval limit in seconds");
        options.addOption("exchangeinterval", true, "exchange interval in seconds");
        options.addOption("port", true, "server port, an integer");
        options.addOption("secret", true, "secret");
        options.addOption("debug", false, "print debug information");
        options.addOption("sport", true, "server secure port, an integer");
        options.addOption("id", true, "set the ID for subscribe request");
        options.addOption("subscribe", false, "subscribe resource from server");
        options.addOption("secure", false, "set true to initiate secure connection");
        options.addOption("debug", false, "print debug information");
        options.addOption("fetch", false, "fetch resource from server");
        options.addOption("channel", true, "channel");
        options.addOption("description", true, "resource description");
        options.addOption("exchange", false, "exchange server list with server");
        options.addOption("host", true, "server host, a domain name or IP address");
        options.addOption("name", true, "resource name");
        options.addOption("owner", true, "owner");
        options.addOption("port", true, "server port, an integer");
        options.addOption("publish", false, "publish resources from server");
        options.addOption("query", false, "query for resources from server");
        options.addOption("remove", false, "remove resource from server");
        options.addOption("secret", true, "secret");
        options.addOption("servers", true, "server list, host1:port1,host2:port2,...");
        options.addOption("share", false, "share resource on server");
        options.addOption("tags", true, "resource tags, tag1,tag2,tag3,...");
        options.addOption("uri", true, "resource URI");
        options.addOption("help", false, "help");

        //parse command line arguments
        return options;
    }

    private static String random(int length) {
        String str = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int num = random.nextInt(36);
            buf.append(str.charAt(num));
        }
        return buf.toString();
    }

    private synchronized static void forward(Socket socket){
        try{
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            socket.setSoTimeout(1);
            String resource = inputStream.readUTF();

            if (!resource.contains("resultSize")&&!resource.contains("error")&&!resource.contains("success")){
                ResourceTemplate  resourceTemplate = gson.fromJson(resource,ResourceTemplate.class);
                fileList.sendNotification(resourceTemplate);
            }
        }catch (IOException e){

        }
    }

    public static void main(String[] args) {
        logger.info("Starting the EZShare Server");

        /* Timer running as a daemon thread schedules the regular EXCHANGE command. */
        Timer timer = new Timer(true);
        TimerTask regularExchangeTask = new TimerTask() {
            @Override
            public void run() {
                serverList.regularExchange();
            }
        };
        TimerTask secure_regularExchangeTask = new TimerTask() {
            @Override
            public void run() {
                secure_severList.regularExchange();
            }
        };
        timer.schedule(regularExchangeTask, 0, EXCHANGE_PERIOD);
        timer.schedule(secure_regularExchangeTask, 1000, EXCHANGE_PERIOD);

        /* Command line processing */
        CommandLineParser parser = new DefaultParser();
        Options options = commandOptions();

        try {
            // parse command line arguments
            CommandLine line = parser.parse(options, args);

            if (line.hasOption("advertisedhostname")) {
                HOST = line.getOptionValue("advertisedhostname");
            }
            if (line.hasOption("connectionintervallimit")) {
                INTERVAL = Integer.parseInt(line.getOptionValue("connectionintervallimit"));
            }
            if (line.hasOption("exchangeinterval")) {
                EXCHANGE_PERIOD = Integer.parseInt(line.getOptionValue("exchangeinterval"));
            }
            if (line.hasOption("port")) {
                PORT = Integer.parseInt(line.getOptionValue("port"));
            }
            if (line.hasOption("sport")) {
                SPORT = Integer.parseInt(line.getOptionValue("sport"));
            }

            if (line.hasOption("secret")) {
                SECRET = line.getOptionValue("secret");
            }
            // if debug not toggle, cancel all logs.
            if (!line.hasOption("debug")) {
                logger.setFilter((LogRecord record) -> (false));
            } else {
                logger.info("setting debug on");
            }


            logger.info("Using advertised hostname: " + HOST);
            logger.info(String.valueOf("Using connection interval limit: " + INTERVAL));
            logger.info(String.valueOf("Using exchange interval period: " + EXCHANGE_PERIOD));
            logger.info("Using secret: " + SECRET);





            /* SSL Context! */
            String keystorePath = "/server.keystore";
            String trustKeystorePath = "/trust-ca.keystore";
            String keystorePassword = "123456";
            LeafNode.context = SSLContext.getInstance("SSL");

            KeyStore keystore = KeyStore.getInstance("pkcs12");
            InputStream keystoreFis = LeafNode.class.getResourceAsStream(keystorePath);
            keystore.load(keystoreFis, keystorePassword.toCharArray());

            KeyStore trustKeystore = KeyStore.getInstance("jks");
            InputStream trustKeystoreFis = LeafNode.class.getResourceAsStream(trustKeystorePath);
            trustKeystore.load(trustKeystoreFis, keystorePassword.toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("sunx509");
            kmf.init(keystore, keystorePassword.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("sunx509");
            tmf.init(trustKeystore);

            LeafNode.context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);


            logger.info("Bound to port " + PORT);
            logger.info("Bound to sport " + SPORT);
            
            /* Create ServerSocket */
            ServerSocketFactory factory = ServerSocketFactory.getDefault();
            ServerSocket serverSocket = factory.createServerSocket(PORT);
            logger.info("ServerSocket initialized.");

            /* Create SSLServerSocket */
            SSLServerSocket sslServerSocket = (SSLServerSocket) LeafNode.context.getServerSocketFactory().createServerSocket(SPORT);
            sslServerSocket.setNeedClientAuth(true);
            logger.info("SSLServerSocket initialized.");


            logger.info("Waiting for client connection..");


            /* Start listening */
            Thread plainSocket = new Thread(() -> {
                while (true) {
                    try {
                        Socket client = serverSocket.accept();

                        /* Upper bound of simultaneous connections */
                        String clientIP = client.getInetAddress().getHostAddress();
                        long currentTime = System.currentTimeMillis();
                        if (!intervalLimit.containsKey(clientIP) || (currentTime - intervalLimit.get(clientIP) > INTERVAL)) {
                            /* Update the time record */
                            intervalLimit.put(clientIP, currentTime);

                            /* Assign a worker thread for this socket. */
                            try {
                                LeafNode.threadPool.submit(new WorkerThread(client, fileList, serverList, false));
                            } catch (IOException e) {
                                logger.log(Level.WARNING, "{0} cannot create stream", client.getRemoteSocketAddress().toString());
                                client.close();
                            }

                        } else {
                    /* Violation */
                            client.close();
                        }
                    } catch (IOException ex) {
                        logger.warning(ex.getMessage());
                    }
                }
            });

            Thread sslSocket = new Thread(() -> {
                while (true) {
                    try {
                        Socket client = sslServerSocket.accept();

                        /* Upper bound of simultaneous connections */
                        String clientIP = client.getInetAddress().getHostAddress();
                        long currentTime = System.currentTimeMillis();
                        if (!intervalLimit.containsKey(clientIP) || (currentTime - intervalLimit.get(clientIP) > INTERVAL)) {
                            /* Update the time record */
                            intervalLimit.put(clientIP, currentTime);

                            /* Assign a worker thread for this socket. */
                            try {
                                LeafNode.threadPool.submit(new WorkerThread(client, fileList, secure_severList, true));
                            } catch (IOException e) {
                                logger.log(Level.WARNING, "{0} cannot create stream", client.getRemoteSocketAddress().toString());
                                client.close();
                            }

                        } else {
                    /* Violation */
                            client.close();
                        }
                    } catch (IOException ex) {
                        logger.warning(ex.getMessage());
                    }
                }
            });

            Thread listener = new Thread(() -> {
                while (true){
                    for (Map.Entry<Host,Socket> entry: LeafNode.unsecure_relay.entrySet()) {
                        forward(entry.getValue());
                    }
                    for (Map.Entry<Host,Socket> entry: LeafNode.secure_relay.entrySet()) {
                        forward(entry.getValue());
                    }
                }

            });

            plainSocket.start();
            sslSocket.start();
            listener.start();

        } catch (IOException ex) {
            logger.warning(ex.getMessage());
        } catch (ParseException ex) {
            /* If commandline args are invalid, show help info. */
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("EZShare.Server", options);
        }
        // SSL issues
        catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }

        //Initialize command line parser and options


        Socket socket = null;

        try {
            //parse command line arguments
            CommandLine line = parser.parse(options, args);

            //validate the command option is unique.
            if (!optionsValidator(line)) {
                throw new ParseException("Multiple command options!");
            }

            //set debug on if toggled
            if (!line.hasOption("debug")) {
                logger.setFilter((LogRecord record) -> (false));
            } else {
                logger.info("setting debug on");
            }

            //get destination host from commandline args
            Host host = getHost(line);

            //get resource template from command args
            ResourceTemplate resourceTemplate = getResourceTemplate(line);


            // SSLSocket? Or plain Socket?
            if (line.hasOption("secure")) {
                String keystorePath = "/client.keystore";
                String trustKeystorePath = "/trust-ca.keystore";
                String keystorePassword = "123456";
                SSLContext context = SSLContext.getInstance("SSL");

                KeyStore clientKeystore = KeyStore.getInstance("pkcs12");
                InputStream keystoreFis = LeafNode.class.getResourceAsStream(keystorePath);
                clientKeystore.load(keystoreFis, keystorePassword.toCharArray());

                KeyStore trustKeystore = KeyStore.getInstance("jks");
                InputStream trustKeystoreFis = LeafNode.class.getResourceAsStream(trustKeystorePath);
                trustKeystore.load(trustKeystoreFis, keystorePassword.toCharArray());

                KeyManagerFactory kmf = KeyManagerFactory.getInstance("sunx509");
                kmf.init(clientKeystore, keystorePassword.toCharArray());

                TrustManagerFactory tmf = TrustManagerFactory.getInstance("sunx509");
                tmf.init(trustKeystore);

                context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

                socket = context.getSocketFactory().createSocket();
            } else {
                socket = new Socket();
            }

            /* Connect! */
            socket.connect(new InetSocketAddress(host.getHostname(), host.getPort()), TIME_OUT);

            //proceed commands
            String error_message = null;    //error message when command line options missing

            if (line.hasOption("query")) {
                queryCommand(socket, resourceTemplate);
            }

            if (line.hasOption("publish")) {
                if (!line.hasOption("uri")) {
                    error_message = "URI is missing.";
                } else {
                    publishCommand(socket, resourceTemplate);
                }
            }
            if (line.hasOption("remove")) {
                if (!line.hasOption("uri")) {
                    error_message = "URI is missing.";
                } else {
                    removeCommand(socket, resourceTemplate);
                }
            }

            if (line.hasOption("share")) {
                if (!line.hasOption("uri") || !line.hasOption("secret")) {
                    error_message = "URI or secret missing.";
                } else {
                    shareCommand(socket, line.getOptionValue("secret"), resourceTemplate);
                }
            }

            if (line.hasOption("exchange")) {
                if (!line.hasOption("servers")) {
                    error_message = "servers missing.";
                } else {

                    //parse commandline args to host list
                    String[] s = line.getOptionValue("servers").split(",");
                    List<Host> serverList = new ArrayList<>();
                    for (String server : s) {
                        String[] address = server.split(":");
                        serverList.add(new Host(address[0], Integer.valueOf(address[1])));
                    }

                    exchangeCommand(socket, serverList);
                }
            }

            if (line.hasOption("fetch")) {
                if (!line.hasOption("uri")) {
                    error_message = "URI is missing.";
                } else {
                    fetchCommand(socket, resourceTemplate);
                }
            }

            if (line.hasOption("subscribe")) {
                // boolean relay = line.hasOption("relay");
                boolean relay = true;
                //set local IP address as default ID.
                String id = line.getOptionValue("id", socket.getLocalAddress().toString());
                subscribeCommand(socket, resourceTemplate, relay, id);
            }

            if (error_message != null) {
                logger.warning(error_message);
            }

        } catch (ParseException e) {
            //If commandline args invalid, show help info.
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("EZShare.Client", options);
        } catch (ConnectException e) {
            logger.warning("Socket connection timeout!");
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            //when value of -servers option invalid
            logger.warning("Server address invalid.");
        } catch (SocketTimeoutException e) {
            logger.warning("Socket timeout!");
        } catch (IOException e) {
            logger.warning("IOException!");
        }
        // SSL issues
        catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } finally {
            try {
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                logger.warning("IOException! Disconnect!");
            }
        }
    }



    private static final String download_path = "Downloads/";
    private static final int TIME_OUT = 30000;



    /**
     * Validator of Command Line options. Prevent user typing multiple commands.
     *
     * @param line command line args after parsing.
     * @return validation of the command line args.
     */
    private static boolean optionsValidator(CommandLine line) {
        //make sure only one command option appears in commandline args
        int count = 0;
        if (line.hasOption("query")) {
            count++;
        }
        if (line.hasOption("publish")) {
            count++;
        }
        if (line.hasOption("share")) {
            count++;
        }
        if (line.hasOption("exchange")) {
            count++;
        }
        if (line.hasOption("remove")) {
            count++;
        }
        if (line.hasOption("fetch")) {
            count++;
        }
        if (line.hasOption("subscribe")) {
            count++;
        }
        return count == 1;
    }

    /**
     * Construct Host object via command line args
     *
     * @param line command line args
     * @return Host object
     */

    private static Host getHost(CommandLine line) {
        //parse commandline args to Host object
        String hostname = line.getOptionValue("host", "localhost");
        Integer port = Integer.valueOf(line.getOptionValue("port", "3780"));
        return new Host(hostname, port);
    }

    /**
     * Construct ResourceTemplate object from command line args.
     *
     * @param line command line args.
     * @return ResourceTemplate object.
     */
    private static ResourceTemplate getResourceTemplate(CommandLine line) {
        //parse commandline args to ResourceTemplate object
        String channel = line.getOptionValue("channel", "");
        String name = line.getOptionValue("name", "");
        String[] tags = {};
        if (line.hasOption("tags")) {
            tags = line.getOptionValue("tags").split(",");
        }
        String description = line.getOptionValue("description", "");
        String uri = (line.hasOption("uri")) ? line.getOptionValue("uri") : "";
        String owner = line.getOptionValue("owner", "");
        String ezserver = line.getOptionValue("servers", "");

        return new ResourceTemplate(channel, name, tags, description, uri, owner, ezserver);

    }

    /**
     * Send Messages to the server.
     *
     * @param output the output stream of the socket.
     * @param JSON   the json string to be sent.
     */
    private static void sendMessage(DataOutputStream output, String JSON) throws IOException {
        //send message to server
        output.writeUTF(JSON);
        output.flush();

        //log
        logger.fine("SENT:" + JSON);
    }

    /**
     * Process query command. Can also be utilized in Server WorkerThread
     *
     * @param socket           The socket connected to target server.
     * @param resourceTemplate The encapsulation of the resource.
     */
    public static void queryCommand(Socket socket, ResourceTemplate resourceTemplate) throws IOException {

        socket.setSoTimeout(TIME_OUT);
        List<ResourceTemplate> result = new ArrayList<>();

        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        logger.fine("querying to " + socket.getRemoteSocketAddress());

        QueryMessage queryMessage = new QueryMessage(resourceTemplate, true);

        String JSON = gson.toJson(queryMessage);
        sendMessage(output, JSON);

        String response = input.readUTF();

        //receive response
        if (response.contains("success")) {
            //if success print resources
            logger.fine("RECEIVE:" + response);
            response = input.readUTF(); //discard success message
            while (!response.contains("resultSize")) {
                //print out resources
                ResourceTemplate r = gson.fromJson(response, ResourceTemplate.class);
                result.add(r);
                System.out.println(response);
                response = input.readUTF();
            }
            //receive result size for successful request
            logger.fine("RECEIVE_ALL:" + response);
        } else if (response.contains("error")) {
            //when error occur
            logger.warning("RECEIVED:" + response);
        }
    }

    /**
     * Process publish command.
     *
     * @param socket           The socket connected to target server.
     * @param resourceTemplate The encapsulation of the resource.
     */
    private static void publishCommand(Socket socket, ResourceTemplate resourceTemplate) throws IOException {

        socket.setSoTimeout(TIME_OUT);
        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        logger.fine("publishing to " + socket.getRemoteSocketAddress());

        PublishMessage publishMessage = new PublishMessage(resourceTemplate);

        String JSON = gson.toJson(publishMessage);
        sendMessage(output, JSON);

        String response = input.readUTF();

        if (response.contains("error")) {
            logger.warning("RECEIVED:" + response);
        }
        if (response.contains("success")) {
            logger.fine("RECEIVED:" + response);
        }

    }

    /**
     * Proceed share command
     *
     * @param secret           Secret of the server.
     * @param socket           The socket connected to target server.
     * @param resourceTemplate The encapsulation of the resource.
     */
    private static void shareCommand(Socket socket, String secret, ResourceTemplate resourceTemplate) throws IOException {

        socket.setSoTimeout(TIME_OUT);
        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        logger.fine("sharing to " + socket.getRemoteSocketAddress());

        ShareMessage shareMessage = new ShareMessage(resourceTemplate, secret);

        String JSON = gson.toJson(shareMessage);
        sendMessage(output, JSON);

        String response = input.readUTF();
        if (response.contains("error")) {
            logger.warning("RECEIVED:" + response);
        }
        if (response.contains("success")) {
            logger.fine("RECEIVED:" + response);
        }

    }

    /**
     * Process remove command.
     *
     * @param socket           The socket connected to target server.
     * @param resourceTemplate The encapsulation of the resource.
     */
    private static void removeCommand(Socket socket, ResourceTemplate resourceTemplate) throws IOException {

        socket.setSoTimeout(TIME_OUT);
        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        logger.fine("removing to " + socket.getRemoteSocketAddress());

        RemoveMessage removeMessage = new RemoveMessage(resourceTemplate);

        String JSON = gson.toJson(removeMessage);
        sendMessage(output, JSON);

        String response = input.readUTF();
        if (response.contains("error")) {
            logger.warning("RECEIVED:" + response);
        }
        if (response.contains("success")) {
            logger.fine("RECEIVED:" + response);
        }

    }

    /**
     * Process exchange command.
     *
     * @param socket     The socket connected to target server.
     * @param serverList The servers in exchange request.
     */
    private static void exchangeCommand(Socket socket, List<Host> serverList) throws IOException {

        socket.setSoTimeout(TIME_OUT);
        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        logger.fine("exchanging to " + socket.getRemoteSocketAddress());

        ExchangeMessage exchangeMessage = new ExchangeMessage(serverList);

        String JSON = gson.toJson(exchangeMessage);
        sendMessage(output, JSON);

        String response = input.readUTF();
        if (response.contains("error")) {
            logger.warning("RECEIVED:" + response);
        }
        if (response.contains("success")) {
            logger.fine("RECEIVED:" + response);
        }

    }

    /**
     * Process fetch command.
     *
     * @param socket           The socket connected to target server.
     * @param resourceTemplate The encapsulation of the resource.
     */
    private static void fetchCommand(Socket socket, ResourceTemplate resourceTemplate) throws IOException {

        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        logger.fine("fetching to " + socket.getRemoteSocketAddress());

        FetchMessage fetchMessage = new FetchMessage(resourceTemplate);

        String JSON = gson.toJson(fetchMessage);
        sendMessage(output, JSON);

        String response = input.readUTF();
        if (response.contains("success")) {

            logger.fine("RECEIVED:" + response);
            //try to read file template
            String file_template = input.readUTF();

            //if result size is 0
            if (file_template.contains("resultSize")) {
                logger.warning("RECEIVED_ALL:" + file_template);
            } else {
                //result exist! parse resource template
                logger.fine("RECEIVE:" + file_template);
                FileTemplate receivedFileTemplate = gson.fromJson(file_template, FileTemplate.class);

                int resource_size = (int) receivedFileTemplate.getResourceSize();

                String name = new File(receivedFileTemplate.getUri()).getName();
                //System.out.println(name);

                //check download directory
                File download_directory = new File(download_path);
                if (!download_directory.exists()) {
                    download_directory.mkdir();
                }

                //create file
                RandomAccessFile randomAccessFile = new RandomAccessFile(download_path + name, "rw");

                //set read buffer size
                int buffer_size = 1024;

                buffer_size = resource_size > buffer_size ? buffer_size : resource_size;

                byte[] buffer = new byte[buffer_size];

                // # of bytes to be received
                int to_receive = resource_size;

                // # of bytes received per time
                int received;

                //read byte from socket until the last chunk
                while (to_receive > buffer_size && (received = input.read(buffer)) != -1) {
                    //write file
                    randomAccessFile.write(Arrays.copyOf(buffer, received));
                    //note down how many bytes received
                    to_receive -= received;

                    //System.out.println(to_receive);
                    //if there is only one chunk to receive, break to prevent the lost of result_size information

                }

                if (to_receive > 0) {
                    //set the buffer to the length of the last chunk
                    buffer = new byte[to_receive];

                    //read last chunk and write to file
                    received = input.read(buffer);
                    randomAccessFile.write(Arrays.copyOf(buffer, received));
                }
                //close file
                randomAccessFile.close();

                //read resourceSize
                response = input.readUTF();
                logger.fine("RECEIVED:" + response);

            }

        } else if (response.contains("error")) {
            logger.warning("RECEIVED:" + response);
        }

    }

    /**
     * Process subscribe command.
     *
     * @param socket           The socket connected to target server.
     * @param resourceTemplate The query condition of subscribed resources.
     * @param relay            Whether the subscribe command will be relayed to other servers.
     * @param id               The id of the subscription.
     * @throws IOException Exception in data stream.
     */
    private static void subscribeCommand(Socket socket, ResourceTemplate resourceTemplate, boolean relay, String id) throws IOException {

        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        logger.fine("subscribing to " + socket.getRemoteSocketAddress());

        //construct subscribe message.
        SubscribeMessage subscribeMessage = new SubscribeMessage(relay, id, resourceTemplate);

        String JSON = gson.toJson(subscribeMessage);
        sendMessage(output, JSON);

        String response = input.readUTF();

        //if successfully subscribed
        if (response.contains("success")) {
            logger.fine("RECEIVED:" + response);

            //hold connection until press enter.
            socket.setSoTimeout(1);
            while (System.in.available() == 0) {

                //check available resource and print out.
                try {
                    String resource = input.readUTF();
                    System.out.println(resource);
                } catch (IOException e) {
                    //just to prevent blocking in SSLSocket.
                }

            }

            socket.setSoTimeout(3000);
            //Termination
            //construct unsubscribe message.
            UnsubscribeMessage unsubscribeMessage = new UnsubscribeMessage(id);

            JSON = gson.toJson(unsubscribeMessage);
            sendMessage(output, JSON);

            //read result size
            response = input.readUTF();

            logger.info("RECEIVED:" + response);


        } else if (response.contains("error")) {
            logger.warning("RECEIVED:" + response);
        }


    }




}