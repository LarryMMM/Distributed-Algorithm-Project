package EZShare.log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

/**
 *  Custom Log Formatter
 *  Author @Wayan Saryada
 */
public class LogCustomFormatter {

    /* Create a logger for current class */
    public static Logger getLogger(String classname) {

        Logger logger = Logger.getLogger(classname);
        logger.setUseParentHandlers(false);

        MyFormatter formatter = new MyFormatter();
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(formatter);

        /* Set logger level to all to display all logs in console */
        logger.setLevel(Level.ALL);
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);

        return logger;
    }

    /*
    public static void main(String[] args) {
        Logger logger = Logger.getLogger(LogCustomFormatter.class.getName());
        logger.setUseParentHandlers(false);

        MyFormatter formatter = new MyFormatter();
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(formatter);

        logger.addHandler(handler);
        logger.info("Example of creating custom formatter.");
        logger.warning("A warning message.");
        logger.severe("A severe message.");
    }
     */
}

class MyFormatter extends Formatter {

    /* Create a DateFormat to format the logger timestamp. */
    private static final DateFormat df = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss.SSS");

    @Override
    public String format(LogRecord record) {
        StringBuilder builder = new StringBuilder(1000);
        builder.append(df.format(new Date(record.getMillis()))).append(" - ");
        builder.append("[").append(record.getSourceClassName()).append(".");
        builder.append(record.getSourceMethodName()).append("] - ");
        builder.append("[").append(record.getLevel()).append("] - ");
        builder.append(formatMessage(record));
        builder.append("\n");
        return builder.toString();
    }

    @Override
    public String getHead(Handler h) {
        return super.getHead(h);
    }

    @Override
    public String getTail(Handler h) {
        return super.getTail(h);
    }
}
