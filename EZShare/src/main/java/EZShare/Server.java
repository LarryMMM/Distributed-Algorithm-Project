package EZShare;

import EZShare.log.LogCustomFormatter;
import EZShare.message.Host;
import EZShare.message.ResourceTemplate;
import EZShare.message.SubscribeMessage;
import EZShare.message.UnsubscribeMessage;
import EZShare.server.FileList;
import EZShare.server.ServerList;
import EZShare.server.Subscription;
import EZShare.server.WorkerThread;
import com.google.gson.Gson;
import org.apache.commons.cli.*;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SocketHandler;
import javax.net.ServerSocketFactory;
import javax.net.ssl.*;

/**
 * @author Wenhao Zhao, Ying Li
 */
public class Server {

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
    public static final Logger logger = LogCustomFormatter.getLogger(Server.class.getName());
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
            Server.context = SSLContext.getInstance("SSL");

            KeyStore keystore = KeyStore.getInstance("pkcs12");
            InputStream keystoreFis = Server.class.getResourceAsStream(keystorePath);
            keystore.load(keystoreFis, keystorePassword.toCharArray());

            KeyStore trustKeystore = KeyStore.getInstance("jks");
            InputStream trustKeystoreFis = Server.class.getResourceAsStream(trustKeystorePath);
            trustKeystore.load(trustKeystoreFis, keystorePassword.toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("sunx509");
            kmf.init(keystore, keystorePassword.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("sunx509");
            tmf.init(trustKeystore);

            Server.context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);


            logger.info("Bound to port " + PORT);
            logger.info("Bound to sport " + SPORT);
            
            /* Create ServerSocket */
            ServerSocketFactory factory = ServerSocketFactory.getDefault();
            ServerSocket serverSocket = factory.createServerSocket(PORT);
            logger.info("ServerSocket initialized.");

            /* Create SSLServerSocket */
            SSLServerSocket sslServerSocket = (SSLServerSocket) Server.context.getServerSocketFactory().createServerSocket(SPORT);
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
                                Server.threadPool.submit(new WorkerThread(client, fileList, serverList, false));
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
                                Server.threadPool.submit(new WorkerThread(client, fileList, secure_severList, true));
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
                    for (Map.Entry<Host,Socket> entry: Server.unsecure_relay.entrySet()) {
                        forward(entry.getValue());
                    }
                    for (Map.Entry<Host,Socket> entry: Server.secure_relay.entrySet()) {
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
    }
}