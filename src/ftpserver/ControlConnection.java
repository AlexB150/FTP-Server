package ftpserver;

import ftpserver.command.Command;
import ftpserver.command.Command.CommandFunction;
import ftpserver.command.Command.CommandFunctionNoArgs;
import ftpserver.command.Command.CommandFunctionMultiArgs;
import ftpserver.command.CommandHandler;
import ftpserver.file.FileHandler;
import ftpserver.log.Log;

import java.io.*;
import ftpserver.access.Authenticator;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;

/**
 *  Handle the control connection for the FTP server
 *
 *  Used to send/receive commands
 * */
public class ControlConnection implements Closeable {

    public static final String TAG = "ControlConnection";

    /** Connection to the client */
    private Socket conn;

    private final BufferedReader reader;
    private final BufferedWriter writer;

    /** Used to check incoming commands */
    private final ControlThread ctrlThread;

    /** Check if the user can login */
    private final Authenticator auth;

    private final FTPServer server;
    private final CommandHandler cmdHandler;
    private final DataConnectionHandler dataConnHandler;
    private final FileHandler fh;

    private boolean isStopped = false;
    private final int timeout;
    private long lastUpdate;

    /** The list of all the commands supported*/
    private final Map<String, Command> commands = new HashMap<>();
    /** The list of all the features supported*/
    private final List<String> features = new ArrayList<>();
    /** The list of all the options supported*/
    private final Map<String, String> options = new HashMap<>();

    public ControlConnection(FTPServer server, Socket socket, Authenticator auth,
                             int timeout, int bufferSize, FileHandler handler) throws IOException {
        this.server = server;
        conn = socket;
        this.auth = auth;
        this.fh = handler;
        this.timeout = timeout;

        cmdHandler = new CommandHandler(this);
        dataConnHandler = new DataConnectionHandler(this, fh, bufferSize);

        InputStream in = socket.getInputStream();
        reader = new BufferedReader(new InputStreamReader(in));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        conn.setSoTimeout(timeout);

        ctrlThread = new ControlThread();
        ctrlThread.setDaemon(true);
        ctrlThread.start();

        registerCommand("FEAT", this::feat, "FEAT", false);
        registerCommand("OPTS", this::opts, "OPTS <option> [value]");

        registerFeature("feat");
        registerFeature("UTF-8");
        registerOption("UTF-8", "ON");

        registerFeature("base"); // Base Commands (RFC 5797)
        registerFeature("MLST Type*;Size*;Modify*;Perm*;"); // File Information (RFC 3659)
        registerFeature("TYPE A;AN;AT;AC;L;I"); // Supported Types (RFC 5797)

        registerOption("MLST", "Type;Size;Modify;Perm;");

        cmdHandler.registerCommand();
        dataConnHandler.registerCommands();

        sendResponse(220, "Service ready");
    }

    /** Listen for incoming commands */
    public void listen() {
        if (cmdHandler.shouldStop()) {
            close();
            return;
        }

        String request;
        try {
            request = reader.readLine();
        } catch (SocketTimeoutException e) {
            if (dataConnHandler.getDataConnections().isEmpty() && (System.currentTimeMillis() - lastUpdate) >= timeout)
                close();
            return;
        } catch (SocketException ex) {
            close();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (request == null) {
            close();
            return;
        }

        if (request.isEmpty()) return;

        System.out.println(request);
        processRequest(request);
    }

    private void processRequest(String request) {
        int firstSpace = request.indexOf(' ');

        if (firstSpace < 0) firstSpace = request.length();

        Command cmd = commands.get(request.substring(0, firstSpace).toUpperCase());

        if (cmd == null) {
            sendResponse(502, "Unknown command");
            return;
        }

        processCommand(cmd, request.length() != firstSpace ? request.substring(firstSpace + 1) : "");
    }

    private void processCommand(Command cmd, String args) {

        if (cmd.needAuthentication() && !cmdHandler.getAuthenticated()) {
            sendResponse(530, "Needs authentication");
            return;
        }

        try {
            cmd.getCmd().run(args);
        } catch (FileNotFoundException e) {
            sendResponse(550, e.getMessage());
        } catch (IOException e) {
            sendResponse(450, e.getMessage());
        } catch (Exception e) {
            sendResponse(451, e.getMessage());
            e.printStackTrace();
        }
    }

    /** Send a response to the client
     *
     * @param code The code of the response
     * @param response The response message */
    public void sendResponse(int code, String response) {
        if (conn.isClosed()) return;

        if (response == null || response.isEmpty())
            response = "Unknown";

        try {
            if (response.charAt(0) == '-')
                writer.write(code + response + "\r\n"); //multi-line
            else
                writer.write(code + " " + response + "\r\n"); //single-line
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "Response sent: " + code + " " + response);
    }

    public void onUpdate() {
        lastUpdate = System.currentTimeMillis();
    }

    public void registerCommand(String label, CommandFunction cmd, String helpText) {
        registerCommand(label, cmd, helpText, true);
    }

    public void registerCommand(String label, CommandFunction cmd, String helpText, boolean needAuth) {
        commands.put(label.toUpperCase(), new Command(cmd, helpText, needAuth));
    }

    public void registerCommand(String label, CommandFunctionNoArgs cmd, String helpText) {
        registerCommand(label, cmd, helpText, true);
    }

    public void registerCommand(String label, CommandFunctionNoArgs cmd, String helpText, boolean needAuth) {
        commands.put(label.toUpperCase(), new Command(cmd, helpText, needAuth));
    }

    public void registerCommand(String label, CommandFunctionMultiArgs cmd, String helpText) {
        registerCommand(label, cmd, helpText, true);
    }

    public void registerCommand(String label, CommandFunctionMultiArgs cmd, String helpText, boolean needAuth) {
        commands.put(label.toUpperCase(), new Command(cmd, helpText, needAuth));
    }

    public void registerFeature(String feat) {
        if (!features.contains(feat))
            features.add(feat);
    }

    public void registerOption(String option, String value) {
        options.put(option.toUpperCase(), value);
    }

    public String getOption(String option) {
        return options.get(option.toUpperCase());
    }

    public void resetConnection() {
        dataConnHandler.resetConnection();
        cmdHandler.resetConnection();
    }

    private void feat() {
        StringBuilder featList = new StringBuilder();
        featList.append("- Features list: ").append("\r\n");

        for (String feature : features) {
            featList.append(feature).append("\r\n");
        }

        sendResponse(211, featList.toString());
        sendResponse(211, "END");
    }

    private void opts(String[] data) {

        if (data.length == 0) sendResponse(501, "Missing parameters");

        String opts = data[0].toUpperCase();

        if (!options.containsKey(opts))
            sendResponse(501, "Missing option");
        else if (data.length < 2)
            sendResponse(200, options.get(opts));
        else
            options.put(opts, data[1].toUpperCase());
            sendResponse(200, "Option updated");
    }

    public String getStatus(String username) {

        String serverAddress = server.getAddress().getHostAddress();
        String user = username != null ? "as " + username : "anonymously";
        //long transferredByte = dataConnHandler.getTransferredByte();
        /*String transferredByteString = transferredByte != 0 ?
                "Byte transferred at the moment: " + transferredByte :
                "No data connection open";*/

        return  "Version: " + FTPServer.VERSION + "\r\n" +
                "Connected to " + serverAddress + "\r\n" +
                "Logged in " + user + "\r\n" +
                "TYPE: Binary; STRUcture: File; transfer MODE: Stream;" + "\r\n";
    }

    public Authenticator getAuthenticator(){
        return auth;
    }

    public FileHandler getFileHandler() {
        return fh;
    }

    public DataConnectionHandler getDataConnHandler() {
        return dataConnHandler;
    }

    public CommandHandler getCommandHandler() {
        return cmdHandler;
    }

    public FTPServer getServer() {
        return server;
    }

    public String getHelpMessage(String command) {
        Command cmd = commands.get(command);
        return cmd != null ? cmd.getHelpInfo() : null;
    }

    public String getCommandList() {

       StringBuilder builder = new StringBuilder();

        for (String command : commands.keySet()) {
            builder.append(command).append(", ");
        }

        return builder.toString();
    }

    public void stop() {

        if (!ctrlThread.isInterrupted()) {
            ctrlThread.interrupt();
        }

        isStopped = true;
        Log.i(TAG, "Control connection stopped");
    }

    @Override
    public void close() {

        if (!isStopped) stop();

        if (conn != null) {
            try {
                conn.close();
                conn = null;
            } catch (IOException ignored) {}
        }

        Log.i(TAG, "Control connection closed");
    }

    class ControlThread extends Thread {

        @Override
        public void run() {
            while (conn != null && !conn.isClosed()) {
                listen();
            }

            close();
        }
    }
}