package ftpserver.access;

import ftpserver.ControlConnection;

public class NoAuthenticator implements Authenticator {

    @Override
    public boolean needUsername(ControlConnection conn) {
        return false;
    }

    @Override
    public boolean needPassword(ControlConnection conn, String username) {
        return false;
    }

    @Override
    public boolean authenticate(ControlConnection conn, String username, String password) {
        return true;
    }
}
