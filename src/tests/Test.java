package tests;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;

public class Test {

    // Permission Categories
    public static final int CAT_OWNER = 6;
    public static final int CAT_GROUP = 3;
    public static final int CAT_PUBLIC = 0;

    // Permission Types
    public static final int TYPE_READ = 2;
    public static final int TYPE_WRITE = 1;
    public static final int TYPE_EXECUTE = 0;

    public Test() throws IOException {
        //System.out.println(getPermission(new File("C:\\Users\\Public\\Documents\\test.txt")));

        File file = new File("C:\\Users\\Public\\Documents\\test.txt");
        Path path = Paths.get(file.getCanonicalPath());
        BasicFileAttributes fileAttributes = Files.readAttributes(path, BasicFileAttributes.class);
        System.out.println(fileAttributes.lastAccessTime());
    }

    public static void main(String[] args) throws IOException {
        new Test();
    }

    public static <F> String getPermission(File file) {
        // Intended Format
        // -rw-rw-rw-
        // -rwxrwxrwx
        // drwxrwxrwx

        String perm = "";
        int perms = getPermissions(file);

        perm += file.isDirectory() ? 'd' : '-';

        perm += hasPermission(perms, CAT_OWNER + TYPE_READ) ? 'r' : '-';
        perm += hasPermission(perms, CAT_OWNER + TYPE_WRITE) ? 'w' : '-';
        perm += hasPermission(perms, CAT_OWNER + TYPE_EXECUTE) ? 'x' : '-';

        perm += hasPermission(perms, CAT_GROUP + TYPE_READ) ? 'r' : '-';
        perm += hasPermission(perms, CAT_GROUP + TYPE_WRITE) ? 'w' : '-';
        perm += hasPermission(perms, CAT_GROUP + TYPE_EXECUTE) ? 'x' : '-';

        perm += hasPermission(perms, CAT_PUBLIC + TYPE_READ) ? 'r' : '-';
        perm += hasPermission(perms, CAT_PUBLIC + TYPE_WRITE) ? 'w' : '-';
        perm += hasPermission(perms, CAT_PUBLIC + TYPE_EXECUTE) ? 'x' : '-';

        return perm;
    }

    public static int getPermissions(File file) {
        int perms = 0;
        perms = setPermission(perms, CAT_OWNER + TYPE_READ, file.canRead());
        perms = setPermission(perms, CAT_OWNER + TYPE_WRITE, file.canWrite());
        perms = setPermission(perms, CAT_OWNER + TYPE_EXECUTE, file.canExecute());
        return perms;
    }

    public static int setPermission(int perms, int perm, boolean hasPermission) {
        perm = 1 << perm;
        return hasPermission ? perms | perm : perms & ~perm;
    }

    public static boolean hasPermission(int perms, int perm) {
        return (perms >> perm & 1) == 1;
    }

}
