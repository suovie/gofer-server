package server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import libgofer.Message;

public class Server implements Runnable {

    public App app;
    public Database db;
    public ServerSocket server = null;
    public Thread worker = null;
    public ClientThread clients[];
    public int clientCount = 0, port = 46337;
    public static final String SERVER_NAME = "GERVER";
    private volatile boolean running = false;

    public Server(App app) {
        startServer(app, this.port);
    }

    public Server(App app, int port) {
        startServer(app, port);
    }

    public void startServer(App app, int port) {
        clients = new ClientThread[50];
        this.app = app;
        db = new Database(app.filePath);
        try {
            server = new ServerSocket(port);
            port = server.getLocalPort();
            System.out.println(
                "Gerver started. IP : " + InetAddress.getLocalHost() + ", Port : " + server.getLocalPort()
            );
            start();
        } catch (IOException ioe) {
            System.out.println("Cannot bind to port: "+port+". Retrying...");
            startServer(app, 0);
        }
    }

    public boolean running() {
        return running;
    }

    public void run() {
        while (running()) {
            try {
                addThread(server.accept());
            } catch (Exception ioe) {
                app.display("Server accept error");
                ioe.printStackTrace();
            }
        }
    }

    public void stop() {
        running = false;
        if (worker != null) {
            // thread.join();
        }
    }

    public void start() {
        running = true;

        if (worker == null) {
            worker = new Thread(this);
            worker.start();
        }
    }

    private int findClient(int ID) {
        for (int i = 0; i < clientCount; i++) {
            if (clients[i].getID() == ID) {
                return i;
            }
        }
        return -1;
    }

    public synchronized void interpreteMessage(int ID, Message msg) {
        if (msg.type.equals(Message.TYPE_SIGNOUT)) {
            broadcast(Message.TYPE_SIGNOUT, SERVER_NAME, msg.sender);
            remove(ID);
        }

        else if (msg.type.equals(Message.TYPE_CONNECT)) {
            clients[findClient(ID)].send(
                new Message(Message.TYPE_CONNECT, SERVER_NAME, "Connection Okay", msg.sender));
        }

        else if (msg.type.equals(Message.TYPE_LOGIN)) {

            if (db.checkLogin(msg.sender, msg.content) &&
                findUserThread(msg.sender) == null)
            {
                clients[findClient(ID)].username = msg.sender;
                clients[findClient(ID)].send(
                    new Message(Message.TYPE_LOGIN, SERVER_NAME, "TRUE", msg.sender));

                broadcast(Message.TYPE_NEW_USER, SERVER_NAME, msg.sender);
                sendUserList(msg.sender);

                app.display("User " + msg.sender + " connected on port: " + ID);
            } else {
                clients[findClient(ID)].send(
                    new Message(Message.TYPE_LOGIN, SERVER_NAME, "FALSE", msg.sender));
            }
        }

        // upload request from a client
        else if (msg.type.equals(Message.TYPE_UPLOAD)) {

            if (msg.recipient.equals(Message.TO_ALL)) {
                clients[findClient(ID)].send(
                    new Message(Message.TYPE_SENDFAIL, SERVER_NAME, "Cannot broadcast a file.", msg.sender));

                return;
            }

            try {
                // ask recipient to start download thread and send port number
                findUserThread(msg.recipient).send(
                    new Message (Message.TYPE_DOWNLOAD, msg.sender, msg.content, msg.recipient)
                );
            } catch (Exception e) {
                clients[findClient(ID)].send(
                    new Message(Message.TYPE_SENDFAIL, SERVER_NAME, e.getMessage(), msg.sender));
            }
        }

        // upload ready response containing recipient's port number
        else if (msg.type.equals(Message.TYPE_DOWNLOAD)){
            try {
                String sender = findUserThread(msg.sender).socket.getInetAddress().getHostAddress();

                // ask sender to upload file to recipient's port number
                findUserThread(msg.recipient).send(
                        new Message (Message.TYPE_UPLOAD, sender, msg.content, msg.recipient)
                );
            } catch (Exception e) {
                clients[findClient(ID)].send(
                    new Message(Message.TYPE_SENDFAIL, SERVER_NAME, e.getMessage(), msg.sender));
            }
        }

        else if (msg.type.equals(Message.TYPE_MESSAGE)) {

            if (msg.recipient.equals(Message.TO_ALL)) {
                broadcast(Message.TYPE_MESSAGE, msg.sender, msg.content);
            } else {
                try {
                    findUserThread(msg.recipient).send(
                        new Message(Message.TYPE_MESSAGE, msg.sender, msg.content, msg.recipient));
                } catch (Exception e) {
                    clients[findClient(ID)].send(
                        new Message(Message.TYPE_MESSAGE, SERVER_NAME, "FALSE", msg.sender));
                }
            }

        } else if (msg.type.equals(Message.TYPE_WHOIS)) {
            clients[findClient(ID)].send(
                new Message(Message.TYPE_MESSAGE, SERVER_NAME, getAllClients(msg.sender), msg.sender));
        }

        else if (msg.type.equals(Message.TYPE_SIGNUP)) {
            if (findUserThread(msg.sender) == null) {

                if (!db.userExists(msg.sender)) {
                    db.addUser(msg.sender, msg.content);

                    clients[findClient(ID)].send(
                        new Message(Message.TYPE_SIGNUP, SERVER_NAME, "TRUE", msg.sender));

                    clients[findClient(ID)].username = msg.sender;

                    clients[findClient(ID)].send(
                        new Message(Message.TYPE_LOGIN, SERVER_NAME, "TRUE", msg.sender));

                    broadcast(Message.TYPE_NEW_USER, SERVER_NAME, msg.sender);
                    sendUserList(msg.sender);
                    app.display("User " + msg.sender + " connected on port: " + ID);
                } else {
                    clients[findClient(ID)].send(
                        new Message(Message.TYPE_SIGNUP, SERVER_NAME, "FALSE", msg.sender));
                }
            } else {
                clients[findClient(ID)].send(
                    new Message(Message.TYPE_SIGNUP, SERVER_NAME, "FALSE", msg.sender));
            }
        }
    }

    public void broadcast(String type, String sender, String content) {
        for (int i = 0; i < clientCount; i++) {
            if (!clients[i].username.equals(sender) &&
                !clients[i].username.equals(""))
            {
                clients[i].send(new Message(type, sender, content, "All"));
            }
        }
    }

    public void sendUserList(String toWhom) {
        for (int i = 0; i < clientCount; i++) {
            if (!clients[i].username.equalsIgnoreCase(toWhom) &&
                !clients[i].username.equals(""))
            {
                findUserThread(toWhom).send(
                    new Message(Message.TYPE_NEW_USER, SERVER_NAME, clients[i].username, toWhom));
            }
        }
    }

    public String getAllClients(String sender) {
        String users = "";
        for (int i = 0; i < clientCount; i++) {
            if (!clients[i].username.equalsIgnoreCase(sender))
                users += clients[i].username + " ";
        }
        return "{" + users.trim() + "}";
    }

    public ClientThread findUserThread(String usr) {
        for (int i = 0; i < clientCount; i++) {
            if (clients[i].username.equalsIgnoreCase(usr)) {
                return clients[i];
            }
        }
        return null;
    }

    public synchronized void remove(int ID) {
        int pos = findClient(ID);
        if (pos >= 0) {
            ClientThread thread = clients[pos];
            app.display("Removing client thread " + ID + " at " + pos);
            if (pos < clientCount - 1) {
                for (int i = pos + 1; i < clientCount; i++) {
                    clients[i - 1] = clients[i];
                }
            }
            clientCount--;
            try {
                thread.close();
            } catch (IOException ioe) {
                app.display("Error closing thread: " + ioe);
            }
        }
    }

    private void addThread(Socket socket) {
        System.out.println("Accepting socket: " + socket);
        if (clientCount < clients.length) {
            app.display("Gofer accepted: " + socket);
            clients[clientCount] = new ClientThread(this, socket);
            try {
                clients[clientCount].open();
                clients[clientCount].start();
                clientCount++;
            } catch (IOException ioe) {
                app.display("Error opening thread: " + ioe);
            }
        } else {
            app.display("Refused: maximum " + clients.length + " reached.");
        }
    }

    public void interpreteCommand(String text) {
        if (!text.isEmpty()) {

            if (text.equalsIgnoreCase("!whois")) {
                app.display(getAllClients(""));
            }

            else if (text.startsWith("!add")) {
                try {
                    String[] all = text.split(" ");

                    if (all.length != 3) {
                        throw new Exception("expecting [user] [password]");
                    }

                    String username = all[1];
                    String password = all[2];

                    if (username.isEmpty() || password.isEmpty()) {
                        throw new Exception("expecting [user] [password]");
                    }

                    if (!db.userExists(username.trim())) {
                        db.addUser(username.trim(), password.trim());
                        app.display("!add: New User \"" + username + "\" Added");
                    } else {
                        app.display("!add: User Already Exists");
                    }
                } catch (Exception ex) {
                    System.out.println("!add exception: "+ex.toString());
                }
            }

            else if (text.startsWith("!bc")) {
                try {
                    String[] all = text.split(" ", 2);

                    if (all.length != 2) {
                        throw new Exception("expecting [message]");
                    }

                    String msg = all[1];

                    broadcast(Message.TYPE_MESSAGE, SERVER_NAME, msg.trim());
                } catch (Exception ex) {
                    app.display("!bc exception: " + ex);
                }
            }

            else if (text.startsWith("!dm")) {
                String to = null;
                try {
                    String[] all = text.split(" ", 3);

                    if (all.length != 3) {
                        throw new Exception("expecting [user] [message]");
                    }

                    to = all[1];
                    String msg = all[2];

                    if (to.isEmpty()) {
                        throw new Exception("expecting [user] [message]");
                    }

                    findUserThread(to).send(
                        new Message(Message.TYPE_MESSAGE, SERVER_NAME, msg, to));
                } catch (NullPointerException ne) {
                    app.display("!dm exception: User \'" + to + "\' not on server");
                } catch (Exception ex) {
                    app.display("!dm exception: " + ex.toString());
                }
            }

            else if (text.equalsIgnoreCase("!help")) {
                app.display("!add !bc !exit !help !dm !setdb !whois !view [db|users|stat]");
            }

            else if (text.equalsIgnoreCase("!exit")) {
                app.server.stop();
            }

            else if (text.startsWith("!setdb")) {
                try {
                    String[] all = text.split(" ");

                    if (all.length != 2) {
                        throw new Exception("expecting [file]");
                    }

                    if (!new File(all[1]).exists()) {
                        throw new Exception("cannot find file");
                    } else {
                        db = new Database(all[1]);
                    }
                } catch (Exception ex) {
                    app.display("!setdb exception: " + ex.toString());
                }
            }

            else if (text.startsWith("!view")) {
                try {
                    String[] all = text.split(" ");

                    if (all.length != 2) {
                        throw new Exception("expecting [option]");
                    }

                    String fp = all[1];

                    if (fp.equalsIgnoreCase("users")) {
                        app.display(db.viewUsers());
                    } else if (fp.equalsIgnoreCase("db")) {
                        app.display("DB Path: " + db.filePath);
                    } else if (fp.equalsIgnoreCase("stat")) {
                        app.display("IP: " + InetAddress.getLocalHost()
                                + "; Port: " + port);
                    } else {
                        throw new Exception("option '" + fp + "' not supported");
                    }
                } catch (Exception ex) {
                    app.display("!view exception: " + ex.toString());
                }
            } else {
                app.display("'" + text + "' not supported");
            }
        }
    }
}
