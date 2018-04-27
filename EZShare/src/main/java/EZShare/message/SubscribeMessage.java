package EZShare.message;

/**
 * Encapsulation of subscribe message
 *
 * @author jason
 */
public class SubscribeMessage extends Message {

    private boolean relay;
    private String id;
    private ResourceTemplate resourceTemplate;

    public SubscribeMessage(boolean relay, String id, ResourceTemplate resourceTemplate) {
        super("SUBSCRIBE");
        this.relay = relay;
        this.id = id;
        this.resourceTemplate = resourceTemplate;
    }

    public boolean isRelay() {
        return relay;
    }

    public ResourceTemplate getResourceTemplate() {
        return resourceTemplate;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean isValid() {
        return resourceTemplate.isValid() && id != null;
    }
}
