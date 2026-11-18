import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileServer {
    public static void main(String[] args) throws IOException {
        final int port = 3000;
        final int threads = 5;

        ExecutorService executor = Executors.newFixedThreadPool(4);
        ServerSocket listenSocket = new ServerSocket(port);

        try {
            executor.submit(new KeyboardMonitor(listenSocket));
            while (true) {
                Socket clientSocket = listenSocket.accept();
                executor.submit(new ClientHandler(clientSocket));
            }
        } catch (Exception e) {
            executor.shutdown();
        }
    }

    public static class  ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            System.out.println("Client Connected: " + clientSocket.getInetAddress());
            try {
                InputStream in = clientSocket.getInputStream();
                OutputStream out = clientSocket.getOutputStream();
                byte[] buffer = new byte[4];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    String message = new String(buffer, 0, bytesRead);


                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    public class DeleteFile implements Runnable {
        private String fileName;

        public DeleteFile(String fileName) {
            this.fileName = fileName;
        }

        public void run() {

        }
    }

    public class ListFiles implements Callable {
        public String call() {
            StringBuilder returnString = new StringBuilder();
            try {
                File serverFilesFolder = new File("ServerFiles");
                File[] files = serverFilesFolder.listFiles();
                for (File file : files) {
                    returnString.append(file.toString()).append("\n");
                }
            } catch (Exception error) {
                error.printStackTrace();
            }
            return returnString.toString();
        }
    }

    public static class KeyboardMonitor implements Runnable {
        private ServerSocket listenSocket;

        public KeyboardMonitor(ServerSocket listenSocket) {
            this.listenSocket = listenSocket;
        }

        public void run() {
            Scanner keyboard = new Scanner(System.in);
            while (true) {
                String input = keyboard.nextLine();
                if (input.equals("q")) {
                    try {
                        listenSocket.close();
                        return;
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
