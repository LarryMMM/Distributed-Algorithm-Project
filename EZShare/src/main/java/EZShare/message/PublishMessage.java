package EZShare.message;


/**
 * Encapsulation of Publish Command.
 * Created by jason on 10/4/17.
 */
public class PublishMessage extends Message{

    private final ResourceTemplate resource;

    public PublishMessage(ResourceTemplate resource){
        super("PUBLISH");
        this.resource = resource;
    }

    public ResourceTemplate getResource() {
        return resource;
    }

    /**
     * Require uri NOT in file schema to publish.
     * @return  Whether the request is valid to publish.
     */
    @Override
    public boolean isValid() {
        return resource.isValid()&&resource.isValidUri();
    }
}
