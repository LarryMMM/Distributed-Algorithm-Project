package EZShare.message;

import java.net.URISyntaxException;

/**
 *
 * @author Wenhao Zhao
 */
public class FileTemplate extends ResourceTemplate {
    /*
        
        TO-DO!
        
     */
    private long resourceSize = 0;

    /**
     * Only for gson.
     */
    public FileTemplate(){
        super();
    }

    public FileTemplate(ResourceTemplate r,long resourceSize){
        super(r.getChannel(),r.getName(),r.getTag(),r.getDescription(),r.getUri(),r.getOwner(),r.getEzserver());
        this.resourceSize = resourceSize;
    }

    public FileTemplate(String channel, String name, String[] tags, String description, String uri, String owner, String ezserver,int resourceSize){
        super(channel, name, tags, description, uri, owner, ezserver);
        this.resourceSize = resourceSize;
    }

    public long getResourceSize() {
        return resourceSize;
    }

}
