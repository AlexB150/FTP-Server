package ftpserver.access;

import ftpserver.ControlConnection;

import java.util.HashMap;
import java.util.Map;

public class StandardAuthenticator implements Authenticator {

    private final Map<String, String> credentials = new HashMap<>();

    public void addCredential(String username, String password) {
        credentials.put(username, password);
    }

    @Override
    public boolean needUsername(ControlConnection conn) {
        return true;
    }

    @Override
    public boolean needPassword(ControlConnection conn, String username) {
        return true;
    }

    @Override
    public boolean authenticate(ControlConnection conn, String username, String password) {

        if (password == null || password.isEmpty())
            return credentials.get(username) != null;

        return password.equals(credentials.get(username));
    }
}
