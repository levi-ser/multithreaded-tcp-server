import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    public static void main(String[] args) {
        String hostname = "localhost";
        int port = 8080; // Ensure this matches the port your server is listening on

        try (Socket socket = new Socket(hostname, port)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Sending a simple HTTP GET request
            out.println("GET /index.html HTTP/1.1\r\nHost: " + hostname + "\r\n\r\n");

            // Reading and printing the response from the server
            String responseLine;
            while ((responseLine = in.readLine()) != null) {
                System.out.println(responseLine);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

