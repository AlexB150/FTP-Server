package ftpserver.command;

import ftpserver.ControlConnection;
import ftpserver.access.Authenticator;
import ftpserver.file.FileHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CommandHandler {

    private String username;

    private final ControlConnection conn;
    private final FileHandler fh;

    private boolean authenticated = false;

    private File cwd;

    private boolean shouldStop = false;

    private File rnFile;

    public CommandHandler(ControlConnection ctrlConn) {
        conn = ctrlConn;
        fh = conn.getFileHandler();
        cwd = fh.getRoot();
    }

    public void registerCommand() {
        conn.registerCommand("NOOP", this::noop, "NOOP", false);
        conn.registerCommand("USER", this::user, "USER <username>", false);
        conn.registerCommand("PASS", this::pass, "PASS <password>", false);
        conn.registerCommand("ACCT", this::acct, "ACCT <account-info>", false);
        conn.registerCommand("SMNT", this::smnt, "SMNT <pathname>");
        conn.registerCommand("SYST", this::syst, "SYST");
        conn.registerCommand("CWD", this::cwd, "CWD <pathname>");
        conn.registerCommand("PWD", this::pwd, "PWD");
        conn.registerCommand("TYPE", this::type, "TYPE <type-code>");
        conn.registerCommand("MODE", this::mode, "MODE <mode-code>");
        conn.registerCommand("STRU", this::stru, "STRU <structure-code>");
        conn.registerCommand("LIST", this::list, "LIST [ <pathname>]");
        conn.registerCommand("NLST", this::nlst, "NLST [ <pathname>]");
        conn.registerCommand("QUIT", this::quit, "QUIT");
        conn.registerCommand("MKD", this::mkd, "MKD <pathname>");
        conn.registerCommand("DELE", this::dele, "DELE <pathname>");
        conn.registerCommand("RMD", this::rmd, "RMD <pathname>");
        conn.registerCommand("CDUP", this::cdup, "CDUP");
        conn.registerCommand("HELP", this::help, "HELP <command-name>", false);
        conn.registerCommand("RNFR", this::rnfr, "RNFR <pathname>");
        conn.registerCommand("RNTO", this::rnto, "RNTO <pathname>");
        conn.registerCommand("REIN", this::rein, "REIN");
        conn.registerCommand("STAT", this::stat, "STAT");
        conn.registerCommand("MLSD", this::mlsd, "MLSD");
    }

    public File getFile(String path) throws IOException {
        if (path.equals("...") || path.equals("..")) {
            return fh.getParent(cwd);
        } else if (path.equals("/")) {
            return fh.getRoot();
        } else if (path.startsWith("/")) {
            return fh.findFile(path.substring(1));
        }
        return fh.findFile(cwd, path);
    }

    public boolean getAuthenticated() {
        return authenticated;
    }

    public boolean shouldStop() {
        return shouldStop;
    }

    public boolean authenticate(Authenticator auth, String username, String password) {
        return auth.authenticate(conn, username, password);
    }

    public void resetConnection() {
        username = null;
        authenticated = false;
    }

    //Commands ---------------------------------------------------------------------------------------------------------
    private void noop() {
        conn.sendResponse(200, "OK");
    }

    private void user(String username) {
        Authenticator auth = conn.getAuthenticator();

        if (!auth.needPassword(conn, username) || authenticated) {
            conn.sendResponse(230, "Logged in");
            authenticated = true;
            return;
        }

        if (authenticate(auth, username, null)) {
            conn.sendResponse(331, "Username ok, need password");
            this.username = username;
            return;
        }

        conn.sendResponse(530, "Authentication failed");
        conn.close();
    }

    private void pass(String password) {
        Authenticator auth = conn.getAuthenticator();

        if (username == null) {
            conn.sendResponse(503, "Insert first the username");
            return;
        }

        boolean success = auth.authenticate(conn, username, password);

        if (success || !auth.needPassword(conn, username) || authenticated) {
            conn.sendResponse(230, "Logged in");
            authenticated = true;
            return;
        }

        conn.sendResponse(530, "Authentication failed");
        conn.close();
    }

    private void acct(String info) {
        if (authenticated)
            conn.sendResponse(230, "Logged in");

        conn.sendResponse(502, "Command not implemented");
    }

    private void smnt(String pathname) {
        conn.sendResponse(502,"Command not implemented");
    }

    private void syst() {
        conn.sendResponse(215, "UNIX Type: L8");
    }

    private void cwd(String path) throws IOException {
        File dir = getFile(path);

        if (dir.isDirectory()) {
            cwd = dir;
            conn.sendResponse(250, "Directory changed successfully");
        } else
            conn.sendResponse(550, "Not  valid directory");
    }

    private void pwd() {
        String currentPath = "/" + fh.getPath(cwd);
        conn.sendResponse(257, '"' + currentPath + '"' + " CWD Name");
    }

    private void type(String type) {
        if (type.toUpperCase().equals("I") || type.toUpperCase().equals("L")) {
            conn.sendResponse(200, "Type changed successfully to: " + type.toUpperCase());
            return;
        }
        //TODO implements other type
        conn.sendResponse(500, "Unknown type");
    }

    private void mode(String mode) {
        if (mode.equalsIgnoreCase("S"))
            conn.sendResponse(200, "Mode set to stream");
        else
            conn.sendResponse(504, "Mode not supported");
    }

    private void stru(String type) {
        if (type.equalsIgnoreCase("F"))
            conn.sendResponse(200, "Structure set to file");
        else
            conn.sendResponse(504, "Structure type not supported");
    }

    private void list(String[] args) throws IOException {
        conn.sendResponse(150, "About to send data");

        File dir = args.length > 0 ? getFile(args[0]) : cwd;

        if (!fh.isDirectory(dir)) {
            conn.sendResponse(550, "Not a directory");
            return;
        }

        StringBuilder builder = new StringBuilder();

        for (File file : fh.getListFiles(dir)) {
            builder.append(fh.getFormat(file));
        }

        conn.getDataConnHandler().sendData(builder.toString().getBytes(StandardCharsets.UTF_8));
        conn.sendResponse(226, "File send successfully");
    }

    private void nlst(String[] args) throws IOException {
        conn.sendResponse(150, "About to send data");

        File dir = args.length > 0 ? getFile(args[0]) : cwd;

        if (!fh.isDirectory(dir)) {
            conn.sendResponse(550, "Not a directory");
            return;
        }

        StringBuilder builder = new StringBuilder();

        for (File file : fh.getListFiles(dir)) {
            builder.append(file.getName()).append("\r\n");
        }

        conn.getDataConnHandler().sendData(builder.toString().getBytes(StandardCharsets.UTF_8));
        conn.sendResponse(226, "File send successfully");
    }

    private void quit() {
        conn.sendResponse(220, "Closing connection...");
        shouldStop = true;
    }

    private void mkd(String path) throws IOException {
        File file = getFile(path);

        fh.mkdirs(file);

        conn.sendResponse(257, '"' + path + '"' + " created");
    }

    private void dele(String path) throws IOException {
        File file = getFile(path);

        if (fh.isDirectory(file)) {
            conn.sendResponse(550, "Not a file");
            return;
        }

        if (file.delete()) {
            conn.sendResponse(250, '"' + path + '"' + " File deleted");
            return;
        }
        conn.sendResponse(450, "Requested file action not taken");
    }

    private void rmd(String path) throws IOException {
        File file = getFile(path);

        if (!fh.isDirectory(file)) {
            conn.sendResponse(550, "Not a directory");
            return;
        }

        if (file.delete()) {
            conn.sendResponse(250, '"' + path + '"' + " Directory deleted");
            return;
        }
        conn.sendResponse(450, "Requested action not taken");
    }

    private void cdup() {
        try {
            cwd = fh.getParent(cwd);
        } catch (FileNotFoundException e) {
            conn.sendResponse(550, "You have no access to this directory");
        }

        conn.sendResponse(200, "Directory changed successfully");
    }

    private void help(String[] cmd) {
        if (cmd[0].equals("")) {
            conn.sendResponse(211, conn.getCommandList());
            return;
        }

        String command = conn.getHelpMessage(cmd[0]);

        if (command == null) conn.sendResponse(501, "Command not implemented");

        conn.sendResponse(214, command);
    }

    private void rnfr(String path) throws IOException {
        rnFile = getFile(path);
        conn.sendResponse(350, "Rename request received");
    }

    private void rnto(String path) throws IOException {
        if (rnFile == null) {
            conn.sendResponse(503, "No rename request received");
            return;
        }

        fh.rename(rnFile, getFile(path));
        rnFile = null;

        conn.sendResponse(250, "File renamed");
    }

    private void rein() {
        conn.resetConnection();
        conn.sendResponse(220, "Ready for a new user");
    }

    private void stat() {
        conn.sendResponse(211, "-FTP server status:\r\n" + conn.getStatus(username));
        conn.sendResponse(211, "End of status");
    }

    private void mlsd(String[] args) throws IOException {
        File file = args.length > 0 ? getFile(args[0]) : cwd;

        if(!fh.isDirectory(file)) {
            conn.sendResponse(550, "Not a directory");
            return;
        }

        conn.sendResponse(150, "Sending file information list...");

        String[] options = conn.getOption("MLST").split(";");
        StringBuilder data = new StringBuilder();

        for(File f : fh.getListFiles(file)) {
            data.append(fh.getFacts(f, options));
        }

        conn.getDataConnHandler().sendData(data.toString().getBytes("UTF-8"));
        conn.sendResponse(226, "The file list was sent!");
    }
}
