import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FileServer {
    static ExecutorService executor = Executors.newFixedThreadPool(5);

    public static void main(String[] args) throws IOException {
        final int port = 3000;
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

    private static void sendString(DataOutputStream out, String message) throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        out.writeInt(data.length); // Client reads this number first
        out.write(data);           // Then client reads exactly this many bytes
        out.flush();
    }

    public static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            System.out.println("Client Connected: " + clientSocket.getInetAddress());
            try {
                InputStream in = clientSocket.getInputStream();
                OutputStream out = clientSocket.getOutputStream();

                DataInputStream dataIn = new DataInputStream(in);
                DataOutputStream dataOut = new DataOutputStream(out); // Wrap out in DataOutputStream

                // Read first length
                int length = dataIn.readInt();

                // Read command
                byte[] commandBytes = new byte[4];
                dataIn.readFully(commandBytes);
                String command = new String(commandBytes, StandardCharsets.UTF_8);

                switch (command) {
                    case "LIST":
                        System.out.println("Received LIST Command");
                        Future<String> futureFileList = executor.submit(new ListFiles());
                        String fileList = futureFileList.get();
                        sendString(dataOut, fileList); // Send the result back to the client
                        break;
                    case "DELE":
                        System.out.println("Received Delete Command");
                        length = dataIn.readInt();
                        byte[] fileNameBytes = new byte[length];
                        dataIn.readFully(fileNameBytes);
                        String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
                        Future<Boolean> futureReturnCode = executor.submit(new DeleteFile(fileName));
                        if (futureReturnCode.get()) {
                            sendString(dataOut, "Successfully Deleted File");
                            System.out.println("successfully deleted file: " + fileName);
                        } else {
                            sendString(dataOut, "Did not Successfully Delete File");
                            System.out.println("did not successfully delete file: " + fileName);
                        }
                        break;
                    case "UPLD":
                        System.out.println("Received Upload Command");
                        length = dataIn.readInt();
                        byte[] uploadFileNameBytes = new byte[length];
                        dataIn.readFully(uploadFileNameBytes);
                        String uploadFileName = new String(uploadFileNameBytes, StandardCharsets.UTF_8);
                        long uploadFileSize = dataIn.readLong();

                        Future<Boolean> uploadReturnCode = executor.submit(new UploadFile(uploadFileName,
                                uploadFileSize, dataIn));
                        if (uploadReturnCode.get()) {
                            sendString(dataOut, "S");
                            System.out.println("Successfully uploaded file: " + uploadFileName);
                        } else {
                            sendString(dataOut, "F");
                            System.out.println("Failed to upload file: " + uploadFileName);
                        }
                        break;
                    case "DOWN":
                        System.out.println("Received Download Command");
                        length = dataIn.readInt();
                        byte[] downloadFileNameBytes = new byte[length];
                        dataIn.readFully(downloadFileNameBytes);
                        String downloadFileName = new String(downloadFileNameBytes, StandardCharsets.UTF_8);

                        Future<Boolean> downloadReturnCode = executor.submit(new DownloadFile(downloadFileName,
                                dataOut));
                        if (downloadReturnCode.get()) {
                            System.out.println("Successfully sent file: " + downloadFileName);
                        } else {
                            System.out.println("Failed to send file: " + downloadFileName);
                        }
                        break;
                    case "RENA":
                        System.out.println("Received Rename Command");
                        length = dataIn.readInt();
                        byte[] originalFileNameBytes = new byte[length];
                        dataIn.readFully(originalFileNameBytes);
                        String originalFileName = new String(originalFileNameBytes, StandardCharsets.UTF_8);
                        length = dataIn.readInt();
                        byte[] newFileNameBytes = new byte[length];
                        dataIn.readFully(newFileNameBytes);
                        String newFileName = new String(newFileNameBytes, StandardCharsets.UTF_8);

                        Future<Boolean> renameReturnCode = executor.submit(new RenameFile(originalFileName,
                                newFileName));
                        if (renameReturnCode.get()) {
                            sendString(dataOut, "Successfully Renamed File: " + originalFileName + " to: " +
                                    newFileName);
                            System.out.println("successfully renamed file: " + originalFileName);
                        } else {
                            sendString(dataOut, "Did not Successfully Rename File");
                            System.out.println("did not successfully rename file: " + originalFileName);
                        }
                        break;
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    public static class UploadFile implements Callable<Boolean> {
        private String fileName;
        private long fileSize;
        private DataInputStream dataIn;

        public UploadFile(String fileName, long fileSize, DataInputStream dataIn) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.dataIn = dataIn;
        }

        public Boolean call() {
            File folder = new File("ServerFiles");
            folder.mkdirs();
            File targetFile = new File(folder, fileName);

            try (FileOutputStream fileOut = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[4096];
                long remaining = fileSize;

                while (remaining > 0) {
                    int n = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (n == -1) {
                        return false;
                    }
                    fileOut.write(buffer, 0, n);
                    remaining -= n;
                }
                return true;
            } catch (Exception exception) {
                exception.printStackTrace();
                return false;
            }
        }
    }

    public static class DownloadFile implements Callable<Boolean> {
        private String fileName;
        private DataOutputStream dataOut;

        public DownloadFile(String fileName, DataOutputStream dataOut) {
            this.fileName = fileName;
            this.dataOut = dataOut;
        }

        public Boolean call() {
            File selectedFile = new File("ServerFiles/" + fileName);
            try {
                if (!selectedFile.isFile()) {
                    sendString(dataOut, "F");
                    return false;
                }

                sendString(dataOut, "S");
                dataOut.writeLong(selectedFile.length());

                try (FileInputStream fileIn = new FileInputStream(selectedFile)) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = fileIn.read(buffer)) != -1) {
                        dataOut.write(buffer, 0, n);
                    }
                    dataOut.flush();
                }
                return true;
            } catch (Exception exception) {
                exception.printStackTrace();
                return false;
            }
        }
    }

    public static class DeleteFile implements Callable<Boolean> {
        private String fileName;

        public DeleteFile(String fileName) {
            this.fileName = fileName;
        }

        public Boolean call() {
            File selectedFile = new File("ServerFiles/" + fileName);
            try {
                if (selectedFile.delete()) {
                    System.out.println("Deleted File " + fileName + " successfully");
                    return true;
                } else {
                    System.out.println("Failed to delete file " + fileName);
                    return false;
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return false;
        }
    }

    public static class RenameFile implements Callable<Boolean> {
        private String oldFileName;
        private String newFileName;

        public RenameFile(String oldFileName, String newFileName) {
            this.oldFileName = oldFileName;
            this.newFileName = newFileName;
        }

        public Boolean call() {
            File selectedFile = new File("ServerFiles/" + oldFileName);
            File newFile = new File("ServerFiles/" + newFileName);
            try {
                if (selectedFile.renameTo(newFile)) {
                    return true;
                } else {
                    System.out.println("Failed to rename file " + oldFileName);
                    return false;
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return false;
        }
    }

    public static class ListFiles implements Callable<String> {
        public String call() {
            StringBuilder returnString = new StringBuilder();
            try {
                File serverFilesFolder = new File("ServerFiles");
                File[] files = serverFilesFolder.listFiles();
                if (files == null || files.length == 0) {
                    returnString.append("Directory is Empty");
                } else {
                    for (File file : files) {
                        returnString.append(file.getName()).append("\n"); // file.getName() avoids printing "ServerFiles\" before every name
                    }
                }
            } catch (Exception error) {
                error.printStackTrace();
            }
            System.out.println("Sending List: " + returnString);
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
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
