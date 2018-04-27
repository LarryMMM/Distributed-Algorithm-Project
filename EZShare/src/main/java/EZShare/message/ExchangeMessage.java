package EZShare.message;

import java.util.List;

/**
 * Encapsulation of Exchange Command.
 * Created by jason on 11/4/17.
 */
public class ExchangeMessage extends Message{

    private final List<Host> serverList;

    public ExchangeMessage(List<Host> serverList){
        super("EXCHANGE");
        this.serverList = serverList;
    }

    public List<Host> getServerList() {
        return serverList;
    }

    /**
     * Validate every host in list.
     * @return Whether serverList is valid.
     */
    @Override
    public boolean isValid() {
        for (Host h:serverList) {
            if(!h.isValid()){
                return false;
            }
        }
        return true;
    }
}
