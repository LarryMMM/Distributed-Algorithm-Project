package EZShare.message;


/**
 * Encapsulation of Query Command.
 * Created by jason on 9/4/17.
 */
public class QueryMessage extends Message {

    private boolean relay;
    private final ResourceTemplate resourceTemplate;

    public void setRelay(boolean relay) {
        this.relay = relay;
    }

    public QueryMessage(ResourceTemplate resourceTemplate, boolean relay){
        super("QUERY");
        this.resourceTemplate = resourceTemplate;
        this.relay = relay;
    }

    public ResourceTemplate getResourceTemplate() {
        return resourceTemplate;
    }

    public boolean isRelay() {
        return relay;
    }

    /**
     * URI in query request can be either file schema or not.
     * @return  Whether the request is valid to query.
     */
    @Override
    public boolean isValid() {
        return resourceTemplate.isValid();
    }



}
