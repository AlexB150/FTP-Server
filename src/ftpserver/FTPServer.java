package ftpserver;

import ftpserver.access.Authenticator;
import ftpserver.file.FileHandler;
import ftpserver.log.Log;

import javax.net.ssl.SSLContext;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * FTP Server
 **/
public class FTPServer implements Closeable {

    public static final String TAG = "FTPServer";

    public static final String VERSION = "1.0.0 beta";

    /** Port where the server listen */
    private int port;

    private ServerSocket server;

    /** Receive and send commands */
    private ControlConnection control;
    private final Authenticator auth;
    private final FileHandler fh;

    private final int bufferSize;

    private ListeningThread listeningThread;

    private boolean isClosed = true;

    public FTPServer (Authenticator auth, FileHandler handler) {
        this(auth, handler, 21);
    }

    public FTPServer (Authenticator auth, FileHandler handler, int port) {
        this(auth, handler, port, 1024);
    }

    public FTPServer (Authenticator auth, FileHandler handler, int port, int bufferSize) {
        this.auth = auth;
        this.fh = handler;
        this.port = port;
        this.bufferSize = bufferSize;
        Log.i(TAG, "FTPServer created");
    }

    /** Listen for incoming connection requests. */
    public void listen() throws IOException {
        if (server == null) create();

        listeningThread = new ListeningThread();
        listeningThread.start();

        isClosed = false;
        Log.i(TAG, "Started listening");
    }

    /** Create the server */
    public void create() throws IOException {
        Log.i(TAG, "Creating server socket");

        if (port <= 0) throw new IllegalArgumentException("Invalid port number");
        if (server != null) throw new IOException("Server already started");

        server = new ServerSocket(port);
        Log.i(TAG, "Server address: " + InetAddress.getLocalHost().getHostAddress());
    }

    /** Create a new control connection for the server */
    public void createControlConnection(Socket socket) throws IOException {
        Log.i(TAG, "Create control connection");
        //5 minutes
        int timeout = 5 * 60 * 1000;
        control = new ControlConnection(this, socket, auth, timeout, bufferSize, fh);
    }

    @Override
    public void close() throws IOException {
        listeningThread.interrupt();
        server.close();
        server = null;
        isClosed = true;
        Log.i(TAG, "Server closed");
    }

    public void setPort(int port) {
        this.port = port;
    }

    /** Get the port where the server will listen */
    public int getPort() { return server != null ? server.getLocalPort() : null; }

    public InetAddress getAddress() {
        InetAddress address = server.getInetAddress();
        if (address.getHostAddress().equals("0.0.0.0")) {
            try {
                return InetAddress.getLocalHost();
            } catch (UnknownHostException ignored) {}
        }
        return server != null ? server.getInetAddress() : null;
    }

    public boolean isClosed() {
        return isClosed;
    }

    class ListeningThread extends Thread{
        @Override
        public void run() {
            while (server != null && !server.isClosed()) {
                try {
                    createControlConnection(server.accept());
                } catch (IOException ignored) {}
            }
        }
    }
}
