import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import static java.lang.System.out;

public class Server {
    public static final Map configMap = Server.loadConfiguraionFile("./config.ini");
    private static String CRLF = "\r\n";
    private static int CHUNK_SIZE = 1024;

    private static Map loadConfiguraionFile(String filePath){
        Properties config = new Properties();
        Map<String, String> hm = new HashMap<String, String>();
        try{
            config.load(new FileInputStream(filePath));
            hm.put("port", config.getProperty("port"));
            hm.put("root", config.getProperty("root"));
            hm.put("defaultPage", config.getProperty("defaultPage"));
            hm.put("maxThreads", config.getProperty("maxThreads"));
        }catch(IOException e){
            e.printStackTrace();
        }
        return hm;
    }
    public static void main(String[] args) {
        out.println("Server starting...");
        ServerSocket serverSocket = null;
        ExecutorService executor = Executors.newFixedThreadPool(Integer.parseInt((String)configMap.get("maxThreads")));

        try {
            serverSocket = new ServerSocket(Integer.parseInt((String)configMap.get("port")));
            out.println("Server is listening on port " + configMap.get("port"));

            while (true) {
                Socket client = serverSocket.accept(); // Accept the connection
                out.println("New client connected: " + client.getInetAddress().getHostAddress());

                ClientHandler clientHandler = new ClientHandler(client);
                executor.execute(clientHandler); // Use the executor to handle the client
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown(); // Shutdown the executor service
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        // Constructor
        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }
        OutputStream out = null;
        BufferedReader reader = null;
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = clientSocket.getOutputStream();

                String requestLine = reader.readLine(); // Read the request lin
                if (requestLine == null || requestLine.isEmpty()) {
                    System.out.println("Empty line was received");
                    return;
                }
                System.out.println("HTTP Request:");
                System.out.println(requestLine);

                // Parse requestLine to get method and path...
                String[] requestParts = requestLine.split(" ");
                String method = requestParts[0];
                String path = requestParts[1];

                String safePath = path.replaceAll("\\.\\./", "");
                if (!isValidPath(safePath)) {
                    sendErrorResponse(out, "400 Bad Request");
                    return;
                }
                // Based on the path, serve the appropriate file or error response
                // Use sendHttpResponse to send the file content or error message

                String rootDirectory = "";


                if (method.equals("GET")) {
                    rootDirectory = (String) configMap.get("root"); // Ensure this is "~/www/lab/html/"
                    String resolvedPath = safePath.equals("/") ? rootDirectory + configMap.get("defaultPage") : rootDirectory + path;
                    String filePath = URLDecoder.decode(resolvedPath, "UTF-8");
                    File file = new File(filePath);

                    if (!file.exists() || file.isDirectory()) {
                        sendErrorResponse(out, "404 Not Found");
                        return;
                    }

                    // Read the file content and determine the content type
                    byte[] content = Files.readAllBytes(file.toPath());
                    String contentType = getContentType(file);

                    boolean isChunked = false; // Default value

                    // Read the headers and find out if chunked encoding is requested
                    String headerLine;
                    while (!(headerLine = reader.readLine()).isEmpty()) {
                        if (headerLine.equalsIgnoreCase("Transfer-Encoding: chunked") ||
                                headerLine.equalsIgnoreCase("Chunked: yes")) {
                            isChunked = true;
                            break;
                        }
                    }
                    Server.sendHttpResponse(out, "200 OK", contentType, content, isChunked);
                }else if (method.equals("POST")){
                    String message = null;
                    int contentLength = 0;
                    String headerLine;
                    while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                        if (headerLine.startsWith("Content-Length:")) {
                            contentLength = Integer.parseInt(headerLine.substring(headerLine.indexOf(":") + 1).trim());
                        }
                    }

                    // Read the request body
                    StringBuilder requestBody = new StringBuilder();
                    int c;
                    for (int i = 0; i < contentLength; i++) {
                        c = reader.read();
                        requestBody.append((char) c);
                    }

                    // Extract form data from the request body

                    String[] formDataParts = requestBody.toString().split("&");
                    for (String formDataPart : formDataParts) {
                        String[] keyValue = formDataPart.split("=");
                        String key = keyValue[0];
                        String value = keyValue.length > 1 ? keyValue[1] : "";
                        System.out.println("Key: " + key + ", Value: " + value);
                        message = value;
                    }
                    // Construct HTML response containing the message
                    String htmlResponse = "<!DOCTYPE html>\r\n";
                    htmlResponse += "<html><head><title>Message Received</title></head><body>\r\n";
                    htmlResponse += "<h1>Message Received:</h1>\r\n";
                    htmlResponse += "<p>" + message + "</p>\r\n";
                    htmlResponse += "</body></html>\r\n";
                    String httpResponse = "HTTP/1.1 200 OK\r\n";
                    httpResponse += "Content-Type: text/html\r\n";
                    httpResponse += "Content-Length: " + htmlResponse.length() + "\r\n\r\n";
                    httpResponse += htmlResponse;

                    out.write(httpResponse.getBytes());
                    out.flush();

                    System.out.println("Received POST data:");
                    System.out.println(requestBody.toString());
                    rootDirectory = (String) configMap.get("root"); // Ensure this is "~/www/lab/html/"
                    String resolvedPath = path.equals("/") ? rootDirectory + configMap.get("defaultPage") : rootDirectory + path;
                    String filePath = URLDecoder.decode(resolvedPath, "UTF-8");
                    File file = new File(filePath);

                    if (!file.exists() || file.isDirectory()) {
                        sendErrorResponse(out, "404 Not Found");
                        return;
                    }

                    // Read the file content and determine the content type
                    byte[] content = Files.readAllBytes(file.toPath());
                    String contentType = getContentType(file);
                    Server.sendHttpResponse(out, "200 OK", contentType, content,false);
                }
            } catch (IOException e) {
                try{
                    Server.sendErrorResponse( out,"500 Internal Server Error");
                    e.printStackTrace();
                } catch (IOException innerException) {
                    throw new RuntimeException("Failed to send error response", innerException);
                }
            } finally {
                // Close resources: reader, out, clientSocket
                try{
                    reader.close();
                    out.close();
                    clientSocket.close();
                }catch (IOException e){
                    throw new RuntimeException("Failed to close Client thread", e);
                }
            }
        }
    }
    private static void sendHttpResponse(OutputStream out, String status, String contentType, byte[] content, boolean isChunked) throws IOException {
        PrintWriter headerWriter = new PrintWriter(out, true);

        // Prepare and print the headers to console
        String headers = "HTTP/1.1 " + status + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                (isChunked ? "Transfer-Encoding: chunked\r\n" : "Content-Length: " + content.length + "\r\n") +
                "\r\n";
        System.out.println("HTTP Response Header:");
        System.out.println(headers); // This will print the response headers to console

        // Write headers to output
        headerWriter.print(headers);
        headerWriter.flush();


        if (isChunked) {
            // Send the file content in chunks
            for (int i = 0; i < content.length; i += CHUNK_SIZE) {
                int chunkSize = Math.min(CHUNK_SIZE, content.length - i);
                // Convert chunk size to hex and send as the prefix
                headerWriter.print(Integer.toHexString(chunkSize) + CRLF);
                headerWriter.flush();
                out.write(content, i, chunkSize);
                out.write(CRLF.getBytes()); // CRLF after chunk
                out.flush();
            }
            // Send a final empty chunk to indicate the end
            headerWriter.print("0" + CRLF + CRLF);
            headerWriter.flush();
        } else {
            out.write(content);
            out.flush();
        }
    }


    private static void sendErrorResponse(OutputStream out, String status) throws IOException {
        System.out.println("Sending Error Response: " + status);
        String response = "HTTP/1.1 " + status + CRLF + "Content-Type: text/html" + CRLF + CRLF + "<html><body><h1>" + status + "</h1></body></html>";
        out.write(response.getBytes());
    }
    private static String getContentType(File file) {
        String fileName = file.getName();
        if (fileName.endsWith(".html")) return "text/html";
        else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        else if (fileName.endsWith(".png")) return "image/png";
            // Add other content types as needed
        else return "application/octet-stream"; // Default binary file type
    }
    private static boolean isValidPath(String path) {
        // Check if the path starts with a forward slash ("/") and contains valid characters
        return path.startsWith("/") && path.matches("^/[a-zA-Z0-9_/\\.\\-]*$");
    }
}