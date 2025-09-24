package org.example;

import com.fastcgi.FCGIInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;


public class FastCGIServer {
    
    private static final Logger logger = LoggerFactory.getLogger(FastCGIServer.class);
    
    public static void main(String[] args) throws IOException {
        logger.info("Starting FastCGI server...");
        
        var fcgiInterface = new FCGIInterface();
        while (fcgiInterface.FCGIaccept() >= 0) {
            var method = FCGIInterface.request.params.getProperty("REQUEST_METHOD");
            if (method == null) {
                System.out.println(errorResult("Unsupported HTTP method: null"));
                continue;
            }
 
            if (method.equals("GET")) {
                handleGetRequest();
                continue;
            }
 
            if (method.equals("POST")) {
                handlePostRequest();
                continue;
            }
 
            System.out.println(errorResult("Unsupported HTTP method: " + method));
        }
        
        logger.info("FastCGI server stopped");
    }
    
    
    /**
     * Обрабатывает GET запрос
     */
    private static void handleGetRequest() {
        var queryString = FCGIInterface.request.params.getProperty("QUERY_STRING");
        var scriptName = FCGIInterface.request.params.getProperty("SCRIPT_NAME");
        
        // Проверяем, что это запрос к нашему скрипту
        if (scriptName == null || !scriptName.equals("/fcgi-bin/app.jar")) {
            System.out.println(errorResult("Not Found"));
            return;
        }
        
        // Парсим параметры из query string
        Map<String, String> params = parseQueryString(queryString);
        String sessionId = params.get("sessionId");
        
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            var allResults = SessionManager.getResults(sessionId.trim());
            String jsonResponse = buildJsonResponse(allResults);
            System.out.println(successJsonResult(jsonResponse));
        } else {
            System.out.println(errorResult("Missing sessionId parameter"));
        }
    }
    
    /**
     * Обрабатывает POST запрос
     */
    private static void handlePostRequest() {
        var contentType = FCGIInterface.request.params.getProperty("CONTENT_TYPE");
        var scriptName = FCGIInterface.request.params.getProperty("SCRIPT_NAME");
        
        // Проверяем, что это запрос к нашему скрипту
        if (scriptName == null || !scriptName.equals("/fcgi-bin/app.jar")) {
            System.out.println(errorResult("Not Found"));
            return;
        }
        
        if (contentType == null) {
            System.out.println(errorResult("Content-Type is null"));
            return;
        }
        
        if (!contentType.equals("application/x-www-form-urlencoded")) {
            System.out.println(errorResult("Content-Type is not supported"));
            return;
        }
        
        var requestBody = parseFormUrlEncoded(readRequestBody());
        var xStr = requestBody.get("xVal");
        var yStr = requestBody.get("yVal");
        var rStr = requestBody.get("rVal");
        var sessionId = requestBody.get("sessionId");
        
        if (xStr == null || yStr == null || rStr == null) {
            System.out.println(errorResult("Missing required parameters"));
            return;
        }
        
        long startTime = System.nanoTime();
        
        // Парсим и валидируем координаты
        double x, y, r;
        try {
            x = Double.parseDouble(xStr.replace(',', '.'));
            y = Double.parseDouble(yStr.replace(',', '.'));
            r = Double.parseDouble(rStr.replace(',', '.'));
        } catch (NumberFormatException e) {
            System.out.println(errorResult("Invalid number format"));
            return;
        }
        
        // Валидируем координаты
        CoordinatesValidator validator = new CoordinatesValidator(x, y, r);
        if (!validator.checkData()) {
            System.out.println(errorResult("Invalid data, try again :)"));
            return;
        }
        
        // Проверяем попадание точки в область
        boolean isInArea = AreaChecker.isInArea(x, y, r);
        
        // Вычисляем время выполнения
        double executionTime = (System.nanoTime() - startTime) / 1_000_000.0;
        
        // Получаем текущее время
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        // Создаем результат
        SessionManager.CalculationResult result = new SessionManager.CalculationResult(
            x, y, r, isInArea, currentTime, executionTime
        );
        
        // Генерируем или извлекаем session ID
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "session_" + System.currentTimeMillis() + "_" + 
                       Integer.toHexString((int)(Math.random() * 10000));
        }
        
        // Добавляем в сессию
        SessionManager.addResult(sessionId.trim(), result);
        
        // Получаем все результаты для этой сессии
        var allResults = SessionManager.getResults(sessionId.trim());
        
        // Строим и отправляем JSON ответ
        String jsonResponse = buildJsonResponse(allResults);
        System.out.println(successJsonResult(jsonResponse));
        
        logger.info("Processed request: x={}, y={}, r={}, result={}, time={}ms", 
                   x, y, r, isInArea, executionTime);
    }
    
    /**
     * Читает тело запроса из System.in
     */
    private static String readRequestBody() {
        try {
            var contentLengthStr = FCGIInterface.request.params.getProperty("CONTENT_LENGTH");
            if (contentLengthStr == null) {
                return "";
            }
            
            int contentLength = Integer.parseInt(contentLengthStr);
            if (contentLength <= 0) {
                return "";
            }
            
            byte[] body = new byte[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = System.in.read(body, totalRead, contentLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            
            return new String(body, 0, totalRead, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Error reading request body", e);
            return "";
        }
    }
    
    /**
     * Парсит query string
     */
    private static Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return params;
        }
        
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                try {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception e) {
                    logger.warn("Error parsing parameter: {}", pair);
                }
            }
        }
        return params;
    }
    
    /**
     * Парсит form-urlencoded данные
     */
    private static Map<String, String> parseFormUrlEncoded(String body) {
        return parseQueryString(body);
    }
    
    
    /**
     * Создает успешный JSON ответ
     */
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
     * Создает ответ с ошибкой в JSON формате
     */
    private static String errorResult(String message) {
        String jsonBody = "{\"error\": \"" + message + "\"}";
        return "HTTP/1.1 400 Bad Request\r\n" +
               "Content-Type: application/json; charset=UTF-8\r\n" +
               "Content-Length: " + jsonBody.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
               "\r\n" +
               jsonBody;
    }
    
    /**
     * Строит JSON ответ с результатами
     */
    private static String buildJsonResponse(java.util.List<SessionManager.CalculationResult> results) {
        StringBuilder json = new StringBuilder();
        json.append("{\"results\": [");
        
        for (int i = 0; i < results.size(); i++) {
            SessionManager.CalculationResult result = results.get(i);
            if (i > 0) json.append(",");
            
            json.append("{");
            json.append("\"x\": ").append(result.getX()).append(",");
            json.append("\"y\": ").append(result.getY()).append(",");
            json.append("\"r\": ").append(result.getR()).append(",");
            json.append("\"isInArea\": ").append(result.isInArea()).append(",");
            json.append("\"currentTime\": \"").append(result.getCurrentTime()).append("\",");
            json.append("\"executionTime\": ").append(result.getExecutionTime());
            json.append("}");
        }
        
        json.append("]}");
        return json.toString();
    }
}