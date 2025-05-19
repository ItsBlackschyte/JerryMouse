package org.jerrymouse;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * JerryMouse: A lightweight HTTP/1.1 server for static content.
 */
public class JerryMouse {
    private static final Logger LOGGER = Logger.getLogger(JerryMouse.class.getName());
    private final int port;
    private final String webappDir;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;

    public JerryMouse(int port, String webappDir) {
        this.port = port;
        this.webappDir = webappDir;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        threadPool = Executors.newFixedThreadPool(10);
        LOGGER.info("JerryMouse started on port " + port);

        while (!serverSocket.isClosed()) {
            Socket clientSocket = serverSocket.accept();
            threadPool.execute(() -> handleRequest(clientSocket));
        }
    }

    private void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream();
             PrintWriter writer = new PrintWriter(out, true)) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] tokens = requestLine.split(" ");
            if (tokens.length < 2) {
                sendError(writer, out, 400, "Bad Request");
                return;
            }

            String method = tokens[0];
            String path = tokens[1].split("\\?")[0]; // Ignore query params for now

            if (method.equals("GET")) {
                serveStaticFile(path, writer, out);
            } else {
                sendError(writer, out, 405, "Method Not Allowed");
            }

        } catch (Exception e) {
            LOGGER.severe("Error handling request: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                LOGGER.severe("Error closing socket: " + e.getMessage());
            }
        }
    }

    private void serveStaticFile(String path, PrintWriter writer, OutputStream out) throws IOException {
        File file = new File(webappDir, path);
        if (!file.exists() || file.isDirectory()) {
            sendError(writer, out, 404, "Not Found");
            return;
        }

        String contentType = getContentType(file.getName());
        writer.println("HTTP/1.1 200 OK");
        writer.println("Content-Type: " + contentType);
        writer.println();
        writer.flush();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        out.flush();
    }

    private String getContentType(String fileName) {
        if (fileName.endsWith(".html")) return "text/html";
        if (fileName.endsWith(".css")) return "text/css";
        if (fileName.endsWith(".js")) return "application/javascript";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private void sendError(PrintWriter writer, OutputStream out, int statusCode, String message) throws IOException {
        writer.println("HTTP/1.1 " + statusCode + " " + message);
        writer.println("Content-Type: text/html");
        writer.println();
        writer.println("<h1>" + statusCode + " " + message + "</h1>");
        writer.flush();
        out.flush();
    }

    public void stop() throws IOException {
        serverSocket.close();
        threadPool.shutdown();
        LOGGER.info("JerryMouse stopped");
    }

    public static void main(String[] args) throws IOException {
        JerryMouse server = new JerryMouse(8080, "webapp");
        server.start();
    }
}