package EZShare.message;

/**
 * Encapsulation of Share Command.
 * Created by jason on 11/4/17.
 */
public class ShareMessage extends Message{

    private final String secret;
    private final ResourceTemplate resource;


    public ShareMessage(ResourceTemplate resource,String secret){
        super("SHARE");
        this.resource = resource;
        this.secret = secret;
    }

    public ResourceTemplate getResource() {
        return resource;
    }

    public String getSecret() {
        return secret;
    }

    /**
     * URI in share request can only be in file schema.
     * @return  Whether the request is valid to query.
     */
    @Override
    public boolean isValid() {
        return resource.isValid()&&resource.isValidFile();
    }
}
