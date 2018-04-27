package EZShare.message;

/**
 * Highest abstraction of validatable objects.
 * Created by jason on 12/4/17.
 */
public class idExchangeMessage extends Message{


    private ArrayList id_List = new ArrayList();
    private Integer server_id;

    public List<Host> getServerList() {
        return serverList;
    }
    
    public ExchangeMessage(ArrayList id_List,int server_id){
        super("EXCHANGE");
        this.id_List = id_List;
        this.server_id = server_id;
        
    }
}
