package tests;

import ftpserver.FTPServer;
import ftpserver.access.StandardAuthenticator;
import ftpserver.file.FileHandler;

import java.io.File;
import java.io.IOException;

public class BasicFTPServer {

    public static void main(String[] args) throws IOException {

        File rootDir = new File(System.getProperty("user.home"));

        FileHandler fileHandler = new FileHandler(rootDir);

        StandardAuthenticator auth = new StandardAuthenticator();
        auth.addCredential("Alessio", "Baroni");

        FTPServer server = new FTPServer(auth, fileHandler);
        server.setPort(6000);
        server.listen();
    }

}
