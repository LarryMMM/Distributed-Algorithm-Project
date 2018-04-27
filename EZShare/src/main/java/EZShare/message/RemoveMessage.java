package EZShare.message;

/**
 * Encapsulation of Remove Command.
 * Created by jason on 11/4/17.
 */
public class RemoveMessage extends Message{

    private final ResourceTemplate resource;

    public RemoveMessage(ResourceTemplate resource){
        super("REMOVE");
        this.resource = resource;
    }

    public ResourceTemplate getResource() {
        return resource;
    }

    /**
     * URI in remove request can be either file schema or not.
     * @return  Whether the request is valid to remove.
     */
    @Override
    public boolean isValid() {
        return resource.isValid()&&(resource.isValidUri()||resource.isValidFile());
    }

}
