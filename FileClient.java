import java.io.*; // Used for reading and writing data and files
import java.net.Socket; // Connects the client to the server
import java.util.Scanner; // Reads commands typed by the user
import java.util.concurrent.ExecutorService; // Runs file transfers in the background
import java.util.concurrent.Executors; // Creates the thread pool
import java.nio.charset.StandardCharsets; // Makes sure strings use UTF-8

// Client program for sending commands to the file server
public class FileClient {

    // Opens a new connection to the server for each command
    public static Socket connectToServer(String serverIP, int port)
            throws IOException {
        Socket socket = new Socket(serverIP, port);
        System.out.println("Successfully connected to server!");
        return socket;
    }

    // Asks the server for the names of the files it has stored
    public static void listFiles(String serverIP, int port)
            throws IOException {
        // try-with-resources closes the socket and streams when we're done
        try (
                Socket socket = connectToServer(serverIP, port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())
        ) {
            sendString(out, "LIST"); // Send the list command
            String response = receiveString(in); // Read the server's file list
            System.out.println(response);
        }
    }

    // Sends the old and new names so the server can rename a file
    public static void renameFile(String serverIP, int port,
                                  String oldName, String newName)
            throws IOException {
        try (
                Socket socket = connectToServer(serverIP, port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())
        ) {
            sendString(out, "RENA"); // Tell server which command to run
            sendString(out, oldName); // File to rename
            sendString(out, newName); // New name for that file
            String response = receiveString(in); // Server says whether it worked
            System.out.println(response);
        }
    }

    // Downloads a file from the server and saves it in ClientFiles
    public static void downloadFile(String serverIP, int port, String fileName)
            throws IOException {
        // Don't allow folder paths or blank filenames
        if (fileName.isBlank() ||
                !new File(fileName).getName().equals(fileName) ||
                fileName.equals(".") || fileName.equals("..") ||
                fileName.contains("/") || fileName.contains("\\")) {
            throw new IOException("Invalid filename.");
        }

        try (
                Socket socket = connectToServer(serverIP, port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())
        ) {
            sendString(out, "DOWN"); // Request a download
            sendString(out, fileName); // Tell the server which file we want

            // The server sends a status before sending the file
            String status = receiveString(in);
            if (!status.equals("S")) {
                System.out.println("Download failed: file not found on server.");
                return;
            }

            // Read how many file bytes the server is about to send
            long size = in.readLong();
            if (size < 0) {
                throw new IOException("Invalid file size.");
            }

            // Create ClientFiles if it doesn't already exist
            File folder = new File("ClientFiles");
            folder.mkdirs();

            // Save incoming bytes; this replaces a file with the same name
            try (FileOutputStream fileOut =
                         new FileOutputStream(new File(folder, fileName))) {
                byte[] buffer = new byte[4096]; // Read the file in small pieces
                long remaining = size; // Number of bytes still needed

                while (remaining > 0) {
                    // Don't read more bytes than the file has left
                    int n = in.read(buffer, 0,
                            (int) Math.min(buffer.length, remaining));
                    if (n == -1) {
                        throw new IOException("Connection closed early.");
                    }
                    fileOut.write(buffer, 0, n); // Write this piece to disk
                    remaining -= n;
                }
            }

            System.out.println("Download of " + fileName + " has completed.");
        }
    }

    // Uploads a file from ClientFiles to the server
    public static void uploadFile(String serverIP, int port, String fileName)
            throws IOException {
        // Only allow a regular filename, not a path to another folder
        if (fileName.isBlank() ||
                !new File(fileName).getName().equals(fileName) ||
                fileName.equals(".") || fileName.equals("..") ||
                fileName.contains("/") || fileName.contains("\\")) {
            throw new IOException("Invalid filename.");
        }

        // Find the file on this computer before trying to send it
        File file = new File("ClientFiles", fileName);
        if (!file.isFile()) {
            System.out.println("Upload failed: " + fileName + " not found in ClientFiles.");
            return;
        }

        try (
                Socket socket = connectToServer(serverIP, port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                FileInputStream fileIn = new FileInputStream(file)
        ) {
            sendString(out, "UPLD"); // Tell server we are uploading
            sendString(out, fileName); // Name the server should save it under
            out.writeLong(file.length()); // Send the file size before its contents

            // Send the file a chunk at a time instead of loading it all at once
            byte[] buffer = new byte[4096];
            int n;
            while ((n = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            out.flush(); // Make sure any buffered output is sent

            // For uploads, the server sends its status AFTER receiving the file
            String status = receiveString(in);
            if (status.equals("S")) {
                System.out.println("Upload of " + fileName + " has completed.");
            } else {
                System.out.println("Upload failed.");
            }
        }
    }

    public static void main(String[] args) {
        // The user must provide the server IP address and port
        if (args.length != 2) {
            System.out.println("Usage: java FileClient <IP> <Port>");
            return;
        }

        String serverIP = args[0];
        int port = Integer.parseInt(args[1]);

        // Four worker threads let uploads/downloads run while the menu stays usable
        ExecutorService executor = Executors.newFixedThreadPool(4);
        Scanner keyboard = new Scanner(System.in);
        System.out.println("File client ready.");
        boolean running = true;

        // Keep asking for commands until the user chooses Quit
        while (running) {
            System.out.print("Enter command: ");
            String command = keyboard.nextLine();

            switch (command.toLowerCase()) {
                case "1":
                case "list":
                    try {
                        listFiles(serverIP, port);
                    } catch (IOException e) {
                        System.out.println("Could not retrieve file list.");
                        e.printStackTrace();
                    }
                    break;

                case "2":
                case "delete":
                    System.out.print("Enter filename to delete: ");
                    String deleteName = keyboard.nextLine();
                    // Delete is short enough to run on the main thread
                    try (
                            Socket socket = connectToServer(serverIP, port);
                            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                            DataInputStream in = new DataInputStream(socket.getInputStream())
                    ) {
                        sendString(out, "DELE");
                        sendString(out, deleteName);
                        String response = receiveString(in);
                        System.out.println(response);
                    } catch (IOException e) {
                        System.out.println("Could not delete file.");
                        e.printStackTrace();
                    }
                    break;

                case "3":
                case "rename":
                    System.out.print("Enter current filename: ");
                    String oldName = keyboard.nextLine();
                    System.out.print("Enter new filename: ");
                    String newName = keyboard.nextLine();
                    try {
                        renameFile(serverIP, port, oldName, newName);
                    } catch (IOException e) {
                        System.out.println("Could not rename file.");
                        e.printStackTrace();
                    }
                    break;

                case "4":
                case "download":
                    System.out.print("Enter filename to download: ");
                    final String downloadName = keyboard.nextLine();
                    // Submit the download to a worker so the user can keep typing
                    executor.submit(() -> {
                        try {
                            downloadFile(serverIP, port, downloadName);
                        } catch (IOException e) {
                            System.out.println("Download of " + downloadName + " failed.");
                        }
                    });
                    break;

                case "5":
                case "upload":
                    System.out.print("Enter filename to upload: ");
                    final String uploadName = keyboard.nextLine();
                    // Uploads also run in the background
                    executor.submit(() -> {
                        try {
                            uploadFile(serverIP, port, uploadName);
                        } catch (IOException e) {
                            System.out.println("Upload of " + uploadName + " failed.");
                            System.out.println(e.getMessage());
                        }
                    });
                    break;

                case "6":
                case "quit":
                    running = false; // Leave the command loop
                    break;

                default:
                    System.out.println("Invalid command.");
            }
        }

        // Stop accepting new background tasks; existing transfers can finish
        executor.shutdown();
        keyboard.close();
        System.out.println("Client shutting down.");
    }

    // Send a string by first sending its length as a 4-byte integer
    private static void sendString(DataOutputStream out, String message)
            throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        out.writeInt(data.length); // Receiver reads this number first
        out.write(data); // Then it reads exactly this many bytes
        out.flush();
    }

    // Read a length-prefixed string sent by the server
    private static String receiveString(DataInputStream in)
            throws IOException {
        int length = in.readInt(); // First 4 bytes say how long the string is
        if (length < 0 || length > 1_000_000) {
            throw new IOException("Invalid message length.");
        }
        byte[] data = new byte[length];
        in.readFully(data); // Wait until the whole string has arrived
        return new String(data, StandardCharsets.UTF_8);
    }
}
