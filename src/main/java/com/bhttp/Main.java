package com.bhttp;

public final class Main {
    private Main() {}
    public static void main(String[] args) throws Exception {
        if (args.length == 0) { System.err.println("usage: server|client ..."); System.exit(2); return; }
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        if (args[0].equals("server")) BServe.main(rest);
        else if (args[0].equals("client")) BCurl.main(rest);
        else { System.err.println("usage: server|client ..."); System.exit(2); }
    }
}
