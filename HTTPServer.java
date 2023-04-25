import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class HTTPServer {

    private static final int PORT = 80;

    private static final int CAT_HTML_LENGTH = 58;

    private static final String ROOT_PATH = "/Users/sulli/Documents/INFO314/HTTPServer";

    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList("text/html", "text/plain"));

    private static ExecutorService exec = Executors.newFixedThreadPool(10);

    private static void startHTTPServer(ServerSocket httpServer) throws IOException {
        System.out.println("Server Starting...");
        Socket httpSocket;
        while ((httpSocket = httpServer.accept()) != null) {
            System.out.println("ACCEPTED");
            Socket currHttpSocket = httpSocket;
            Runnable httpRequest = () -> {
                try {
                    handleHTTPRequest(currHttpSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };
            exec.submit(httpRequest);
        }
    }

    private static void handleHTTPRequest(Socket httpSocket) throws IOException {
        System.out.println("HTTP SOCKET CONNECTION");
        BufferedReader in = new BufferedReader(new InputStreamReader(httpSocket.getInputStream()));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(httpSocket.getOutputStream()));
        if (in.ready()) {
            String[] requestLine = in.readLine().split(" ");
            String method = requestLine[0];
            String path = requestLine[1];
            System.out.println("Method: " + method);
            System.out.println("Path: " + path);
            HashMap<String, String> headers = processHeaders(in);
            determineMethod(method, path, in, out, headers);
        }
    }

    private static HashMap<String, String> processHeaders(BufferedReader in) throws IOException {
        HashMap<String, String> headers = new HashMap<String, String>();
        String line;
        while ((line = in.readLine()) != null && !line.equals("")) {
            String[] header = line.split(": ");
            headers.put(header[0], header[1]);
        }
        return headers;
    }

    private static void determineMethod(String method, String path, BufferedReader in, PrintWriter out,
            HashMap<String, String> headers) throws IOException {
        System.out.println("Method: " + method);
        File file = new File(ROOT_PATH + path);
        if (method.equals("GET")) {
            String mimeType = headers.get("Accept");
            if (ALLOWED_MIME_TYPES.contains(mimeType)) {
                handleGETRequest(file, out, mimeType);
            } else {
                sendError(406, "Not Acceptable", out, "text/html", CAT_HTML_LENGTH);
            }
        } else if (method.equals("POST")) {
            handlePOSTRequest(file, in, out, headers);
        } else if (method.equals("PUT")) {
            handlePUTRequest(file, in, out, headers);
        } else if (method.equals("DELETE")) {
            handleDELETERequest(file, out);
        } else {
            handleMethodNotImplemented(out);
        }
        in.close();
        out.close();
    }

    private static void handleGETRequest(File file, PrintWriter out, String mimeType)
            throws IOException, FileNotFoundException {
        if (file.exists()) {
            sendResponse(200, "OK", out, mimeType, (int) file.length());
            sendFile(file, out);
        } else {
            sendError(404, "Not Found", out, "text/html", CAT_HTML_LENGTH);
        }
    }

    private static void handlePOSTRequest(File file, BufferedReader in, PrintWriter out,
            HashMap<String, String> headers) throws IOException, FileNotFoundException {
        if (file.exists()) {
            PrintWriter writer = new PrintWriter(new FileWriter(file, true));
            String body = collectBody(in, headers);
            writer.println(body);
            writer.flush();
            writer.close();
            sendResponse(200, "OK", out, null, 0);
        } else {
            sendError(404, "Not Found", out, "text/html", CAT_HTML_LENGTH);
        }
    }

    private static void handlePUTRequest(File file, BufferedReader in, PrintWriter out, HashMap<String, String> headers)
            throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }
        PrintWriter writer = new PrintWriter(new FileWriter(file, false));
        String body = collectBody(in, headers);
        writer.println(body);
        writer.flush();
        writer.close();
        sendResponse(200, "OK", out, null, 0);
    }

    private static void handleDELETERequest(File file, PrintWriter out) throws IOException {
        if (file.exists()) {
            sendResponse(200, "OK", out, null, 0);
            file.delete();
        } else {
            sendError(404, "Not Found", out, "text/html", CAT_HTML_LENGTH);
        }
    }

    private static void handleMethodNotImplemented(PrintWriter out) throws IOException {
        sendError(501, "Not Implemented", out, "text/html", CAT_HTML_LENGTH);
    }

    private static void sendResponse(int statusCode, String statusMessage, PrintWriter out, String mimeType,
            int bodyLength) throws IOException {
        out.printf("HTTP/1.1 %d %s\r\n", statusCode, statusMessage);
        if (mimeType != null) {
            out.printf("Content-Type: %s\r\n", mimeType);
        }
        if (bodyLength != 0) {
            out.printf("Content-Length: %s\r\n", bodyLength);
        }
        out.printf("\r\n");
        out.flush();
    }

    private static void sendError(int statusCode, String statusMessage, PrintWriter out, String mimeType,
            int bodyLength) throws IOException {
        sendResponse(statusCode, statusMessage, out, mimeType, bodyLength);
        out.write(createCatErrorCodeHTML(statusCode));
        out.flush();
    }

    private static void sendFile(File file, PrintWriter out) throws IOException {
        FileInputStream reader = new FileInputStream(file);
        out.write(new String(reader.readAllBytes()));
        reader.close();
        out.flush();
    }

    private static String collectBody(BufferedReader in, HashMap<String, String> headers) throws IOException {
        String body = "";
        int i = 0;
        int contentLength = Integer.parseInt(headers.get("Content-Length"));
        while (i < contentLength) {
            body += (char) in.read();
            i++;
        }
        return body;
    }

    private static String createCatErrorCodeHTML(int statusCode) {
        return "<html><body><img src=\"https://http.cat/" + statusCode + "\"></body></html>";
    }

    public static void main(String[] args) {
        try {
            startHTTPServer(new ServerSocket(PORT));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}