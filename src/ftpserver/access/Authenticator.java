package ftpserver.access;

import ftpserver.ControlConnection;

public interface Authenticator {

    /** Check whether the username is needed to login
     * @param conn The control connection of the server */
    boolean needUsername(ControlConnection conn);

    /** Check whether the password is needed to login
     *
     * @param conn The control connection of the server
     * @param username The username */
    boolean needPassword(ControlConnection conn, String username);

    /** Used to authenticate if a user can login
     *
     * @param conn The control connection of the server
     * @param username The username that needs to be checked
     * @param password The password of a user */
    boolean authenticate(ControlConnection conn, String username, String password);
}
