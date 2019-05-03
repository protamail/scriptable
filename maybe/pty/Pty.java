package model.extra;

import com.sun.jna.Native;
import com.sun.jna.StringArray;
import com.sun.jna.Structure;
import com.sun.jna.Library;
import java.lang.reflect.Constructor;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileInputStream;

import java.io.IOException;

public class Pty {

    public static class Termios extends Structure {
        public int c_iflag = 0; // input mode flags
        public int c_oflag = 0; // output mode flags
        public int c_cflag = 0; // control mode flags
        public int c_lflag = 0; // local mode flags
        public byte c_line = 0; // line discipline
        public byte[] c_cc = new byte[32]; // control characters
        public int c_ispeed = 0; // input speed
        public int c_ospeed = 0; // output speed

        public Termios() {
        }
    }

    public static class Winsize extends Structure {
        public short rows = 40; // number of rows
        public short cols = 80; // number of columns
        public short xpx = 0;   // width in pixels
        public short ypx = 0;   // height in pixels

        public Winsize() {
        }
    }

    public interface C extends Library {
        int execve(String command, StringArray argv, StringArray env);
        int ioctl(int fd, int cmd, Winsize arg);
    }

    public interface Util extends Library {
        int openpty(int[] amaster, int[] aslave, byte[] name, Termios termp, Winsize winp);
        int login_tty(int fd);
        //int forkpty(int[] amaster, byte[] name, Termios termp, Winsize winp);
    }

    public static final int TIOCGWINSZ = 0x5413;
    public static final int TIOCSWINSZ = 0x5414;

/*    public static final int ONLCR = 0x04;

    public static final int VINTR = 0;
    public static final int VQUIT = 1;
    public static final int VERASE = 2;
    public static final int VKILL = 3;
    public static final int VSUSP = 10;
    public static final int VREPRINT = 12;
    public static final int VWERASE = 14;

    public static final int ECHOCTL = 0x200;
    public static final int ECHOKE = 0x800;

    public static final int SIGHUP = 1;
    public static final int SIGINT = 2;
    public static final int SIGQUIT = 3;
    public static final int SIGILL = 4;
    public static final int SIGABORT = 6;
    public static final int SIGFPE = 8;
    public static final int SIGKILL = 9;
    public static final int SIGSEGV = 11;
    public static final int SIGPIPE = 13;
    public static final int SIGALRM = 14;
    public static final int SIGTERM = 15;

    public static final int WNOHANG = 1;
    public static final int WUNTRACED = 2;
*/

    private static final C c = (C) Native.loadLibrary("c", C.class);
    private static final Util util = (Util) Native.loadLibrary("util", Util.class);

    static int errno() {
        return Native.getLastError(); // native errno error code
    }

    /**
     * Replace calling process image with the new image and start it
     * 
     * @param argv the binary to execute followed by arguments
     * @param env list of environment variables (optional)
     * @return this function does not return on success
     */
    public static void exec(String[] argv, String[] env) {
        c.execve(argv[0], new StringArray(argv), new StringArray(env != null? env : new Scting[]));
        System.exit(-1); // normally should not reach here
    }

    /**
     * Make this process controlled by pty device
     * 
     * @param amaster pointer to integer descriptor of the master side of the pseudoterminal device
     * @param name of the slave side PTY file (optional)
     * @param termp terminal attributes of the slave side PTY file (optional)
     * @param winp window size (optional)
     * @return on success: 0 for the child process, positive PID number to the calling process
     */
    public static void openPty(int[] amaster, byte[] name, Termios termp, Winsize winp)
        throw IOException {
        int[] aslave = new int[1];
        // allocate pty device
        if (util.openpty(amaster, aslave, name, termp, winp) != 0)
            throw new IOException("openpty(): errno=" + errno);
        // attach this process' STDIO to slave end of the pty
        if (util.login_tty(aslave) != 0)
            throw new IOException("login_tty(): errno=" + errno);
        System.out.println("Hi there!");
        new FileInputStream(amaster[0]).readln();
    }

    public static FileDescriptor getFileDescriptor(int fd) {
        Constructor<FileDescriptor> ctor = FileDescriptor.class.getDeclaredConstructor(Integer.TYPE);
        ctor.setAccessible(true);
        FileDescriptor fd = ctor.newInstance(fd);
        ctor.setAccessible(false);
        return fd;
    }

    public static Winsize getWinSize(int fd) {
        Winsize ws = new Winsize();
        c.ioctl(fd, TIOCGWINSZ, ws);
        return ws;
    }

    public static int setWinSize(int fd, short rows, short cols, short xpx, short ypx) {
        Winsize ws = new Winsize();
        ws.rows = rows;
        ws.cols = cols;
        ws.xpx = xpx;
        ws.ypx = ypx;
        return c.ioctl(fd, TIOCSWINSZ, ws);
    }
}

