package EZShare.message;

import com.google.gson.Gson;

/**
 * Base Class of All Messages
 * Created by jason on 10/4/17.
 */
public class Message extends Validatable {
    private static final String[] valid_commands = {"QUERY","SHARE","PUBLISH","REMOVE","EXCHANGE","FETCH"};

    private final String command;

    public Message(String command){
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    /**
     * Validate command name. IMPORTANT:CASE SENSITIVE!
     * @return  Whether the command name is valid.
     */
    @Override
    public boolean isValid() {
        for (String c : valid_commands) {
            if(c.equals(this.command))
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
