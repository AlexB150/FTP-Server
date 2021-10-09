package ftpserver;

import ftpserver.command.TransferException;
import ftpserver.file.FileHandler;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;

/**
 *  Data Connection
 *
 *  Used to transfer file
 **/
public class DataConnectionHandler implements Closeable {

    private String activeClientAddress;
    private int clientPort = 0;

    private final ControlConnection conn;
    private final FileHandler fh;

    private ServerSocket passiveServer;
    private boolean passive = false;
    private final int bufferSize;

    private long startByte;

    private long transferredByte;

    private final ArrayDeque<Socket> dataConnections = new ArrayDeque<>();

    public DataConnectionHandler(ControlConnection conn, FileHandler handler) {
        this(conn, handler, 1024);
    }

    public DataConnectionHandler(ControlConnection conn, FileHandler handler, int bufferSize) {
        this.conn = conn;
        fh = handler;
        this.bufferSize = bufferSize;
    }

    public ArrayDeque<Socket> getDataConnections() {
        return dataConnections;
    }

    public long getTransferredByte() {
        return transferredByte;
    }

    private Socket createDataSocket() throws IOException {
        if (passive && passiveServer != null)
            return passiveServer.accept();

        return new Socket(activeClientAddress, clientPort);
    }

    public void resetConnection() {
        activeClientAddress = null;
        clientPort = 0;
        passiveServer = null;
        passive = false;
        transferredByte = 0;
    }

    public void registerCommands() {
        conn.registerCommand("PORT", this::port, "PORT <host-port>");
        conn.registerCommand("PASV", this::pasv, "PASV");
        conn.registerCommand("RETR", this::retr, "RETR <pathname>");
        conn.registerCommand("STOR", this::stor, "STOR <pathname>");
        conn.registerCommand("ABOR", this::abor, "ABOR");
        conn.registerCommand("REST", this::rest, "REST <byte-number>");
        conn.registerCommand("APPE", this::appe, "APPE <pathname>");
        conn.registerCommand("STOU", this::stou, "STOU [pathname]");
    }

    public void createSenderThread(File file) {
        new Thread(() -> {
            try {
                sendFile(file);
                conn.sendResponse(226, "File transferred successfully");
            } catch (TransferException e) {
                conn.sendResponse(e.getResponseCode(), e.getMessage());
            } catch (IOException e) {
                conn.sendResponse(450, e.getMessage());
            } catch (Exception e) {
                conn.sendResponse(421, e.getMessage());
            }
        }).start();
    }

    public void sendFile(File file) throws IOException {

        InputStream in = null;
        try {
            in = fh.getFileInputStream(file, startByte);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (in == null) return;

        sendData(in);
    }

    public void sendData(InputStream in) throws TransferException {

        Socket socket = null;
        try {
            socket = createDataSocket();
            dataConnections.add(socket);
            OutputStream out = socket.getOutputStream();

            byte[] buffer = new byte[bufferSize];

            int length;
            while ((length = in.read(buffer)) != -1) {
                write(out, buffer, length);
                transferredByte += length;
            }

            out.flush();
            in.close();
            out.close();
            socket.close();
        } catch (SocketException e) {
            throw new TransferException(426, "Connection closed, transfer aborted");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            conn.onUpdate();
            if (socket != null)
                dataConnections.remove(socket);
            transferredByte = 0;
        }
    }

    public void createReceiverThread(File file) {
        new Thread(() -> {
            try {
                receiveFile(file);
                conn.sendResponse(226, "File transferred successfully");
            } catch (TransferException e) {
                conn.sendResponse(e.getResponseCode(), e.getMessage());
            } catch (IOException e) {
                conn.sendResponse(450, e.getMessage());
            } catch (Exception e) {
                conn.sendResponse(421, e.getMessage());
            }
        }).start();
    }

    public void receiveFile(File file) throws IOException {

        OutputStream out = null;
        try {
            out = fh.getFileOutputStream(file, startByte);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (out == null) return;

        receiveData(out);
    }

    public void receiveData(OutputStream out) throws TransferException {

        Socket socket = null;
        try {
            socket = createDataSocket();
            dataConnections.add(socket);
            InputStream in = socket.getInputStream();

            byte[] buffer = new byte[bufferSize];

            int length;
            while ((length = in.read(buffer)) != -1) {
                write(out, buffer, length);
                transferredByte += length;
            }

            out.flush();
            in.close();
            out.close();
            socket.close();
        } catch (SocketException e) {
            throw new TransferException(426, "Connection closed, transfer aborted");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            conn.onUpdate();
            if (socket != null)
                dataConnections.remove(socket);
            transferredByte = 0;
        }
    }

    private void write(OutputStream out, byte[] data, int length) throws IOException {
        out.write(data, 0, length);
    }

    public void sendData(byte[] data) throws TransferException {

        Socket socket = null;
        try {
            socket = createDataSocket();
            dataConnections.add(socket);
            OutputStream out = socket.getOutputStream();

            write(out, data, data.length);
            transferredByte += data.length;

            out.close();
            socket.close();
        } catch (SocketException e) {
            throw new TransferException(426, "Connection closed, transfer aborted");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            conn.onUpdate();
            if (socket != null)
                dataConnections.remove(socket);
            transferredByte = 0;
        }
    }

    @Override
    public void close() throws IOException {
        if (passiveServer != null) {
            passiveServer.close();
            passiveServer = null;
        }
    }

    //Commands ---------------------------------------------------------------------------------------------------------
    private void port(String data) {
        String[] info = data.split(",");

        activeClientAddress = info[0] + "." + info[1] + "." +
                info[2] + "." + info[3];
        clientPort = Integer.parseInt(info[4]) * 256 + Integer.parseInt(info[5]);
        passive = false;

        if (passiveServer != null) {
            try {
                passiveServer.close();
            } catch (IOException ignored) {}
            passiveServer = null;
        }

        conn.sendResponse(200, "Active mode enabled");
    }

    private void pasv() throws IOException {
        passiveServer = new ServerSocket(0, 5, conn.getServer().getAddress());
        passive = true;

        String host = passiveServer.getInetAddress().getHostAddress();
        int port = passiveServer.getLocalPort();

        if (host.equals("0.0.0.0"))
            host = InetAddress.getLocalHost().getHostAddress();

        String[] addr = host.split("\\.");

        String address = addr[0] + "," + addr[1] + "," + addr[2] + "," + addr[3];
        String addressPort = port / 256 + "," + port % 256;

        conn.sendResponse(227, "Entering passive mode(" + address + "," + addressPort + ")");
    }

    private void retr(String path) throws IOException {
        File file = conn.getCommandHandler().getFile(path);

        conn.sendResponse(150, "About to send file");
        createSenderThread(file);
        startByte = 0;
    }

    private void stor(String path) throws IOException {
        File file;
        try {
            file = conn.getCommandHandler().getFile(path);
        } catch (IOException e) {
            file = conn.getCommandHandler().getFile(fh.validateFileName(path));
        }

        conn.sendResponse(150, "Ready to receive the file");
        createReceiverThread(file);
        startByte = 0;
    }

    private void abor() {
        while (!dataConnections.isEmpty()) {
            Socket socket = dataConnections.poll();
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private void rest(String startByte) {
        long bytes = Long.parseLong(startByte);
        if (bytes >= 0) {
            this.startByte = bytes;
            conn.sendResponse(350, "Restarting at " + bytes + ", waiting for STOR or RETR");
            return;
        }
        conn.sendResponse(501, "Number of bytes must be greater than 0");
    }

    private void appe(String path) throws IOException {
        File file = conn.getCommandHandler().getFile(path);

        if (file.exists())
            startByte = fh.size(file);
        else
            startByte = 0;

        conn.sendResponse(150, "Ready to append the file");
        createReceiverThread(file);
    }

    private void stou(String[] path) throws IOException {
        File file = null;
        String ext = ".tmp";

        if (path.length > 0) {
            file = conn.getCommandHandler().getFile(path[0]);
            int i = path[0].indexOf(".");
            ext = path[0].substring(i);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyHHmmss");
        int counter = 0;

        while (file != null && fh.exists(file)) {
            counter++;
            String name = "FTP" + sdf.format(new Date()) + counter;
            file = fh.findFile(name + ext);
        }

        conn.sendResponse(150, "Filename: " + fh.getName(file));
        startByte = 0;
        createReceiverThread(file);
    }
}
