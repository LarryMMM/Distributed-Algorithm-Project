package EZShare.server;

import java.util.ArrayList;
import java.util.List;




public class ServerStruct {
    public String guid;
    public String parentId;
    public String[] files;
    public boolean isOnline;
    
    public ServerStruct(String guid)
    {
        this.guid = guid;
        isOnline = true;
    }
    public ServerStruct(String guid, String parentId)
    {
        this.guid = guid;
        this.parentId = parentId;
        isOnline = true;
    }
    public ServerStruct(String guid, String parentId, String files)
    {
        this.guid = guid;
        this.parentId = parentId;
        isOnline = true;
        this.files = files.split(",");
    }
    
    public boolean checkIfHasFile(String filename) {
        for (int i = 0; i < files.length; i++) {
            if (files[i].equals(filename)) {
                return true;
            }
        }
        return false;
    }
}




