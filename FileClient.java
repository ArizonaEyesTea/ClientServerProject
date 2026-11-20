import java.io*;
///connects to server 
import java.net.Socket;
///reads user keyboard
import java.util.Scanner;
///manage background task
import java.util.concurrent.ExecutorService;
///thread pool creation
import java.util.concurrent.Executors;


public class FileClient {

    public static void main(String[] args) {

        // Check command-line arguments for 2
        if (args.length != 2) {
            System.out.println("Usage: java FileClient <IP> <Port>");
            return;
        }

        // Get users server information ex 127.0.0.1(local host) 3000 (port)
        String serverIP = args[0];
        int port = Integer.parseInt(args[1]);

        // makes the four worker threads to handle the file transfer
        ExecutorService executor =
        Executors.newFixedThreadPool(4);

        Scanner keyboard = new Scanner(System.in);
        System.out.println("File client ready.");
        boolean running = true;





        while (running) {
            System.out.print("Enter command: ");
            String command = keyboard.nextLine();

            switch (command.toLowerCase()) {

                case "1":
                case "list":
                    System.out.println("List selected");
                    break;

                case "2":
                case "delete":
                    System.out.println("Delete selected");
                    break;

                case "3":
                case "rename":
                    System.out.println("Rename selected");
                    break;

                case "4":
                case "download":
                    System.out.println("Download selected");
                    break;

                case "5":
                case "upload":
                    System.out.println("Upload selected");
                    break;

                case "6":
                case "quit":
                    running = false;
                    break;

                default:
                    System.out.println("Invalid command.");
            }
        }

        executor.shutdown();
        keyboard.close();
        System.out.println("Client shutting down.");

    }
}
