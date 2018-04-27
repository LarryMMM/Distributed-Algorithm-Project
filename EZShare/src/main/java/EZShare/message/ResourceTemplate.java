package EZShare.message;

import com.google.gson.Gson;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

/**
 * Encapsulation of Query Message.
 * Created by jason on 10/4/17.
 */
public class ResourceTemplate extends Validatable {
    private String name = "";
    private String[] tags = {};
    private String description = "";
    private String uri = "";
    private String channel = "";
    private String owner = "";
    private String ezserver = "";

    /**
     * Only for Gson.
     */
    public ResourceTemplate() {
    }

    public ResourceTemplate(String channel, String name, String[] tags, String description, String uri, String owner, String ezserver) {
        this.channel = channel;
        this.name = name;
        this.tags = tags;
        this.description = description;
        this.uri = uri;
        this.owner = owner;
        this.ezserver = ezserver;
    }


    public String getChannel() {
        return channel;
    }

    public String getDescription() {
        return description;
    }

    public String getEzserver() {
        return ezserver;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public String getUri() {
        return uri;
    }

    public String[] getTag() {
        return tags;
    }

    public void setEzserver(String ezserver) {
        this.ezserver = ezserver;
    }

    public void encryptOwner(String owner) {
        this.owner = "*";
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * Check whether the uri is valid for publish or query.
     *
     * @return Validation.
     */
    public boolean isValidUri() {
        try {
            URI u = new URI(this.uri);
            return (u.getScheme() != null && !u.getScheme().equals("file") && u.getAuthority() != null && u.isAbsolute());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Check whether the uri is valid for share.
     *
     * @return Validation.
     */
    public boolean isValidFile() {
        try {
            URI u = new URI(this.uri);
            return (u.getScheme() != null && u.getScheme().equals("file") && u.getPath() != null && u.isAbsolute());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @Override
    public boolean isValid() {
        return owner != null && !owner.equals("*");
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }


    /**
     * Check if two resourceTemplate objects match in the query.
     *
     * @param candidate Resource in candidate.
     * @return Match or not
     */

    public boolean match(ResourceTemplate candidate) {
        ResourceTemplate query = this;
        return channel_match(query.channel, candidate.getChannel())
                && owner_match(query.owner, candidate.getOwner())
                && tag_match(query.tags, candidate.getTag())
                && string_match(query.uri, candidate.getUri())
                && (candidate.getName().contains(query.name)
                || candidate.getDescription().contains(query.description)
                || (query.description.equals("")
                && query.name.equals("")));
    }

    private static boolean string_match(String s1, String s2) {
        return (s1 == null || s2 == null || s1.equals("") || s2.equals("") || s1.equals(s2));
    }

    private static boolean channel_match(String channel1, String channel2) {
        return channel1.equals(channel2);
    }

    private static boolean owner_match(String owner1, String owner2) {
        return (owner1.equals("") || owner1.equals(owner2));
    }

    private static boolean tag_match(String[] tags1, String[] tags2) {
        for (String t : tags1) {
            if (!Arrays.asList(tags2).contains(t)) {
                return false;
            }
        }
        return true;
    }


}
