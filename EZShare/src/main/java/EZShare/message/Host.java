package EZShare.message;

/**
 * Encapsulation of Hostname and port #
 * Created by jason on 9/4/17.
 */
public class Host extends Validatable {
    private final String hostname;
    private final Integer port;

    public Host(String hostname, Integer port) {
        this.hostname = hostname;
        this.port = port;
    }

    public Integer getPort() {
        return port;
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * Only able to check port number at this stage.
     * @return  Whether the port number is valid.
     */
    @Override
    public boolean isValid() {
        return port<=65535&&port>=1;
    }

    @Override
    public String toString() {
        return hostname+":"+port.toString();
    }
}
