package EZShare.message;

/**
 * Encapsulation of unsubscribe message.
 * Created by jason on 17/5/17.
 */
public class UnsubscribeMessage extends Message {

    private String id;

    public UnsubscribeMessage(String id) {
        super("UNSUBSCRIBE");
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean isValid() {
        return id != null;
    }
}
