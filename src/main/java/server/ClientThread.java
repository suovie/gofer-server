package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import libgofer.Message;

class ClientThread extends Thread {

    public Server server = null;
    public Socket socket = null;
    public int ID = -1;
    public String username = "";
    public ObjectInputStream streamIn = null;
    public ObjectOutputStream streamOut = null;
    private boolean running = true;

    public ClientThread(Server server, Socket socket) {
        super();
        this.server = server;
        this.socket = socket;
        ID = socket.getPort();
    }

    public void send(Message msg) {
        try {
            streamOut.writeObject(msg);
            streamOut.flush();
        } catch (IOException ex) {
            System.out.println("Exception [SocketClient : send(...)]");
        }
    }

    public int getID() {
        return ID;
    }

    public void run() {
        server.app.display("Server Thread " + ID + " running.");
        while (running) {
            try {
                Message message = (Message) streamIn.readObject();
                server.interpreteMessage(ID, message);
            } catch (IOException | ClassNotFoundException ioe) {
                server.app.display(ID + " ERROR reading: " + ioe.getMessage());
                server.broadcast("signout", "GERVER", username);
                server.remove(ID);
                running = false;
            }
        }
    }

    public void terminate() {
        this.running = false;
    }

    public void open() throws IOException {
        streamOut = new ObjectOutputStream(socket.getOutputStream());
        streamOut.flush();
        streamIn = new ObjectInputStream(socket.getInputStream());
    }

    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
        if (streamIn != null) {
            streamIn.close();
        }
        if (streamOut != null) {
            streamOut.close();
        }

        this.terminate();
    }
}
