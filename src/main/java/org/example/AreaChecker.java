package org.example;

import java.util.logging.Logger;

public class AreaChecker {
    private static final Logger logger = Logger.getLogger(AreaChecker.class.getName());

    public static boolean isInArea(double x, double y, double r) {
        logger.info(String.format("Проверка точки: x=%.3f, y=%.3f, r=%.3f", x, y, r));

        if (x >= 0 && y >= 0) {
            boolean inside = x * x + y * y <= r * r;
            return inside;
        }

        if (x >= 0 && y <= 0) {
            boolean inside = x <= r && y >= -r / 2;
            return inside;
        }

        if (x <= 0 && y <= 0) {
            boolean inside = -r / 2 <= (x + y);
            return inside;
        }

        return false;
    }
}
