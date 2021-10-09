package ftpserver.file;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static tests.Test.*;

public class FileHandler {

    private final File rootDir;

    public FileHandler(File rootDir) {
        this.rootDir = rootDir;

        if(!rootDir.exists()) rootDir.mkdirs();
    }

    public File getRoot() {
        return rootDir;
    }

    public String getPath(File file) {
        return rootDir.toURI().relativize(file.toURI()).getPath();
    }

    public boolean delete(File file) {
        return file.delete();
    }

    public boolean exists(File file) {
        return file.exists();
    }

    public boolean isDirectory(File file) {
        return file.isDirectory();
    }

    public boolean isReadable(File file) {
        return file.canRead();
    }

    public boolean isWritable(File file) {
        return file.canWrite();
    }

    public boolean isExecutable(File file) {
        return file.canExecute();
    }

    public int getHardLinks(File file) {
        return file.isDirectory() ? 3 : 1;
    }

    public long size(File file) {
        return file.length();
    }

    public String getName(File file) {
        return file.getName();
    }

    public File getParent(File file) throws FileNotFoundException {
        if (file.equals(rootDir))
            throw new FileNotFoundException("No permission to access this file");

        return new File(file.getParent());
    }

    public long getLastModified(File file) {
        return file.lastModified();
    }

    public File[] getListFiles(File file) {
        return file.listFiles();
    }

    public boolean mkdirs(File file) {
        return file.mkdirs();
    }

    public void rename(File fileFrom, File fileTo) throws IOException {
        if (!fileFrom.renameTo(fileTo)) throw new IOException("Couldn't rename the file");
    }

    public File findFile(String path) throws IOException {
        File file = new File(rootDir, path);

        if (!isInside(rootDir, file))
            throw new IOException("No permission to access this file");

        return file;
    }

    public File findFile(File dir, String path) throws IOException {
        File file = new File(dir, path);

        if (!isInside(rootDir, file))
            throw new IOException("No permission to access this file");

        return file;
    }

    public boolean isInside(File dir, File file) {
        if (file.equals(dir)) return true;

        try {
            return file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator);
        } catch (IOException e) {
            return false;
        }

    }

    public InputStream getFileInputStream(File file, long start) throws IOException {
        if (start <= 0)
            return new FileInputStream(file);

        RandomAccessFile raf = new RandomAccessFile(file, "r");

        return new FileInputStream(raf.getFD()) {
            @Override
            public void close() throws IOException {
                super.close();
                raf.close();
            }
        };
    }

    public OutputStream getFileOutputStream(File file, long start) throws IOException {
        if (start <= 0)
            return new FileOutputStream(file, false);
        else if (start == file.length())
            return new FileOutputStream(file, true);

        RandomAccessFile raf = new RandomAccessFile(file, "rw");

        return new FileOutputStream(raf.getFD()) {
            @Override
            public void close() throws IOException {
                super.close();
                raf.close();
            }
        };
    }

    public String getFormat(File file) {
        return String.format("%s%s %3d %-9s %-9s %9d %s %s\r\n",
                (isDirectory(file) ? "d" : "-"),
                getPermissionsFormat(file),
                getHardLinks(file),
                getOwner(file),
                getGroup(),
                size(file),
                formatDate(getLastModified(file)),
                getName(file));
    }

    private String getPermissionsFormat(File file) {
        return (isReadable(file) ? "r" : "-") +
                (isWritable(file) ? "w" : "-") +
                (isExecutable(file) ? "x" : "-") +
                "-" +
                "-" +
                "-" +
                "-" +
                "-" +
                "-";
        //In windows is not possible to obtain posix file permissions
    }

    public String getOwner(File file) {
        try {
            Path path = Paths.get(file.getCanonicalPath());
            return Files.getOwner(path).getName();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getGroup() {
        return "-";
        //In windows is not possible to obtain group which the file is part of
    }

    public static SimpleDateFormat YEAR_FORMAT = new SimpleDateFormat("MMM dd yyyy");

    private String formatDate(long date) {
        return YEAR_FORMAT.format(new Date(date));
    }

    public String getFacts(File file, String[] options) {
        // Intended Format
        // modify=20170808052431;size=7045120;type=file;perm=rfadw; video.mp4
        // modify=20170526215012;size=380;type=file;perm=rfadw; data.txt
        // modify=20171012082146;size=0;type=dir;perm=elfpcm; directory

        StringBuilder facts = new StringBuilder();
        boolean dir = isDirectory(file);

        for(String opt : options) {
            opt = opt.toLowerCase();

            switch (opt) {
                case "modify":
                    facts.append("modify=").append(toMdtmTimestamp(getLastModified(file))).append(";");
                    break;
                case "size":
                    facts.append("size=").append(size(file)).append(";");
                    break;
                case "type":
                    facts.append("type=").append(dir ? "dir" : "file").append(";");
                    break;
                case "perm":
                    int perms = getPermissions(file);
                    String perm = "";

                    if (hasPermission(perms, CAT_OWNER + TYPE_READ)) {
                        perm += dir ? "el" : "r";
                    }
                    if (hasPermission(perms, CAT_OWNER + TYPE_WRITE)) {
                        perm += "f";
                        perm += dir ? "pcm" : "adw";
                    }

                    facts.append("perm=").append(perm).append(";");
                    break;
            }
        }

        facts.append(" ").append(getName(file)).append("\r\n");
        return facts.toString();
    }

    private static final SimpleDateFormat mdtmFormat = new SimpleDateFormat("YYYYMMddHHmmss", Locale.ENGLISH);

    public String toMdtmTimestamp(long time) {
        return mdtmFormat.format(new Date(time));
    }

    public String validateFileName(String path) {
        String[] invalidChar = new String[]{"\\", "/", ":", "<", ">", "|", "*", "?"};

        for (int i = 0; i < invalidChar.length; i++) {
            if (path.contains(invalidChar[i]))
                path = path.replace(invalidChar[i], "_");
        }

        return path;
    }
}
