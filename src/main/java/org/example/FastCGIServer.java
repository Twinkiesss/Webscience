package org.example;

import com.fastcgi.FCGIInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FastCGIServer {
    private static final Logger logger = Logger.getLogger(FastCGIServer.class.getName());

    public static void main(String[] args) {
        logger.log(Level.INFO, "Starting FastCGI server...");

        var fcgiInterface = new FCGIInterface();
        while (fcgiInterface.FCGIaccept() >= 0) {
            var method = FCGIInterface.request.params.getProperty("REQUEST_METHOD");
            if (method == null) {
                System.out.println(errorResult("Unsupported HTTP method: null"));
                continue;
            }

            if (method.equals("POST")) {
                logger.info("Got new POST request");
                handlePostRequest();
                continue;
            }

            System.out.println(errorResult("Unsupported HTTP method: " + method));
        }

        logger.log(Level.INFO, "FastCGI server stopped");
    }

    /**
     * Обрабатывает POST запрос
     */
    private static void handlePostRequest() {
        var contentType = FCGIInterface.request.params.getProperty("CONTENT_TYPE");
        var scriptName = FCGIInterface.request.params.getProperty("SCRIPT_NAME");

        logger.info("SCRIPT_NAME: " + scriptName);
        logger.info("Content-Type: " + contentType);

        if (scriptName == null || !scriptName.equals("/fcgi-bin/app.jar")) {
            logger.info("Invalid SCRIPT_NAME: " + scriptName);
            System.out.println(errorResult("Not Found"));
            return;
        }

        if (contentType == null) {
            logger.info("Content-Type is null");
            System.out.println(errorResult("Content-Type is null"));
            return;
        }

        if (!contentType.equals("application/json")) {
            logger.info("Unsupported Content-Type: " + contentType);
            System.out.println(errorResult("Content-Type is not supported"));
            return;
        }

        String requestBody = readRequestBody();
        var json = parseJSON(requestBody);
        logger.info("Request: " + requestBody);
        var xStr = json.get("X");
        var yStr = json.get("Y");
        var rStr = json.get("R");

        if (xStr == null || yStr == null || rStr == null) {
            logger.info("Missing required parameters");
            System.out.println(errorResult("Missing required parameters"));
            return;
        }

        long startTime = System.nanoTime();

        double x, y, r;
        try {
            x = Double.parseDouble(xStr.replace(',', '.'));
            y = Double.parseDouble(yStr.replace(',', '.'));
            r = Double.parseDouble(rStr.replace(',', '.'));
        } catch (NumberFormatException e) {
            System.out.println(errorResult("Invalid number format"));
            return;
        }

        CoordinatesValidator validator = new CoordinatesValidator(x, y, r);
        if (!validator.checkData()) {
            System.out.println(errorResult("Invalid data, try again :)"));
            return;
        }

        boolean isInArea = AreaChecker.isInArea(x, y, r);

        double executionTime = (System.nanoTime() - startTime) / 1_000_000.0;

        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Создаем результат без привязки к сессии
        Map<String, Object> result = new HashMap<>();
        result.put("x", x);
        result.put("y", y);
        result.put("r", r);
        result.put("inArea", isInArea);
        result.put("currentTime", currentTime);
        result.put("executionTime", executionTime);

        // Возвращаем только один результат вместо списка
        List<Map<String, Object>> resultsList = new ArrayList<>();
        resultsList.add(result);

        String jsonResponse = buildJsonResponse(resultsList);
        System.out.println(successJsonResult(jsonResponse));

        logger.log(Level.INFO, String.format("Processed request: x=%s, y=%s, r=%s, result=%s, time=%sms",
                x, y, r, isInArea, executionTime));
    }

    private static String readRequestBody() {
        try {
            FCGIInterface.request.inStream.fill();
            int contentLength = Integer.parseInt(FCGIInterface.request.params.getProperty("CONTENT_LENGTH"));
            logger.info("Content-Length: " + contentLength);
            return new String(System.in.readNBytes(contentLength), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error reading request body", e);
            return "";
        }
    }

    private static Map<String, String> parseJSON(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isEmpty()) return params;

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(body);

            // Используем те же имена, что и во фронтенде
            if (jsonNode.has("X")) {
                params.put("X", jsonNode.get("X").asText());
            }
            if (jsonNode.has("Y")) {
                params.put("Y", jsonNode.get("Y").asText());
            }
            if (jsonNode.has("R")) {
                params.put("R", jsonNode.get("R").asText());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing JSON: " + e.getMessage());
        }

        return params;
    }

    private static String successJsonResult(String jsonBody) {
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: " + jsonBody.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
                "\r\n" +
                jsonBody;
    }

    /**
     * Строит JSON ответ с результатами
     */
    private static String buildJsonResponse(List<Map<String, Object>> results) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error serializing response to JSON: " + e.getMessage());
            return "{\"results\": []}";
        }
    }

    /**
     * Создает ответ с ошибкой в JSON формате
     */
    private static String errorResult(String message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> error = new HashMap<>();
            error.put("error", message);
            return "HTTP/1.1 400 Bad Request\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Content-Length: " + mapper.writeValueAsString(error).getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "\r\n" +
                    mapper.writeValueAsString(error);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error serializing error response to JSON: " + e.getMessage());
            return "HTTP/1.1 400 Bad Request\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Content-Length: 0\r\n" +
                    "\r\n";
        }
    }
}