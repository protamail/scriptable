  private boolean fork_nofork(String cmd, String args[], String env[]) {
    JFLog.log("fork:no fork version");
    String slaveName;
    master = c.posix_openpt(O_RDWR | O_NOCTTY);
    if (master == -1) return false;
    slaveName = c.ptsname(master);
    if (slaveName == null) {
      JFLog.log("LnxPty:slave pty == null");
      return false;
    }
    if (c.grantpt(master) != 0) {
      JFLog.log("LnxPty:grantpt() failed");
      return false;
    }
    if (c.unlockpt(master) != 0) {
      JFLog.log("LnxPty:unlockpt() failed");
      return false;
    }

    ArrayList<String> cmdline = new ArrayList<String>();
    cmdline.add("java");
    cmdline.add("-cp");
    cmdline.add("/usr/share/java/javaforce.jar:/usr/share/java/jna.jar");
    cmdline.add("javaforce.jna.LnxPty");
    cmdline.add(slaveName);
    cmdline.add(cmd);
    cmdline.add("" + (args.length-1));  //# args
    for(int a=0;a<args.length;a++) {
      if (args[a] == null) break;
      cmdline.add(args[a]);
    }
    for(int a=0;a<env.length;a++) {
      if (env[a] == null) break;
      cmdline.add(env[a]);
    }
    String cl[] = cmdline.toArray(new String[0]);
    try {
      ProcessBuilder pb = new ProcessBuilder(cl);
      pb.directory(new File("/home/" + System.getenv("USER")));
      p = pb.start();
    } catch (Exception e) {
      JFLog.log(e);
      return false;
    }

    writeBuf = Native.malloc(1024);
    readBuf = Native.malloc(1024);
    new Thread() {
      public void run() {
        try {p.waitFor();} catch (Exception e) {}
        close();
      }
    }.start();
    return true;
  }

  /** This becomes the child process. */
  public static void main(String args[]) {
    if (args == null || args.length < 3) {
      System.out.println("Usage : LnxPty slaveName, cmd, #args, [args...], [env...]");
      return;
    }
    init();

    String slaveName = args[0];
    String cmd = args[1];
    int noArgs = JF.atoi(args[2]);
    int p = 3;
    ArrayList<String> process_args = new ArrayList<String>();
    ArrayList<String> process_env = new ArrayList<String>();
    for(int a=0;a<noArgs;a++) {
      process_args.add(args[p++]);
    }
    while (p < args.length) {
      process_env.add(args[p++]);
    }

    termios attrs = new termios();

    try {
      int slave = c.open(slaveName, O_RDWR);  //should open this in child process
      if (slave == -1) {
        System.out.println("LnxPty:unable to open slave pty");
        System.exit(0);
      }
      if (c.setsid() == -1) {
        System.out.println("LnxPty:unable to setsid");
        System.exit(0);
      }
      c.tcgetattr(slave, attrs);
      // Assume input is UTF-8; this allows character-erase to be correctly performed in cooked mode.
      attrs.c_iflag |= IUTF8;
      // Humans don't need XON/XOFF flow control of output, and it only serves to confuse those who accidentally hit ^S or ^Q, so turn it off.
      attrs.c_iflag &= ~IXON;
      // ???
      attrs.c_cc[VERASE] = 127;
      c.tcsetattr(slave, TCSANOW, attrs);
      c.dup2(slave, STDIN_FILENO);
      c.dup2(slave, STDOUT_FILENO);
      c.dup2(slave, STDERR_FILENO);
      c.signal(SIGINT, SIG_DFL);
      c.signal(SIGQUIT, SIG_DFL);
      c.signal(SIGCHLD, SIG_DFL);
      c.execvpe(cmd, process_args.toArray(new String[0]), process_env.toArray(new String[0]));
      System.exit(0);  //should not happen
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(0);
    }
  }

