package EZShare.message;

/**
 * Encapsulation of Fetch Command.
 * Created by jason on 11/4/17.
 */
public class FetchMessage extends Message{
    private final ResourceTemplate resourceTemplate;

    public FetchMessage(ResourceTemplate resource){
        super("FETCH");
        this.resourceTemplate = resource;
    }

    public ResourceTemplate getResource() {
        return resourceTemplate;
    }

    /**
     * Require uri in correct file schema to fetch
     * @return  Whether the fetch request is in correct format.
     */
    @Override
    public boolean isValid() {
        return resourceTemplate.isValid()&&resourceTemplate.isValidFile();
    }
}
