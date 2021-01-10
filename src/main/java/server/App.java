package server;

import java.util.Scanner;

public class App {

    public Server server;
    public String filePath;

    public App(String path) {
        filePath = path;
        server = new Server(this);
    }

    public void display(String s) {
        System.out.println("\r> GERVER: " + s);
        System.out.print("\r> GERVER: ");
    }

    public static void main(String[] args) {
        // System.out.println("Usage: [Database Path]");
        String path = args.length == 1 ? args[0] : null;
        App app = new App(path);

        Scanner in = new Scanner(System.in);

        while (app.server.running()) {
            System.out.print("\r> GERVER: ");
            app.server.interpreteCommand(in.nextLine().trim());
        }

        in.close();
        System.out.println("Shutting down server...");
        System.exit(0);
    }
}
