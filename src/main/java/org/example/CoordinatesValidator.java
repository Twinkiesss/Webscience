package org.example;

public class CoordinatesValidator {
    
    private final double x;
    private final double y;
    private final double r;
    
    
    private static final double X_MIN = -5.0;
    private static final double X_MAX = 3.0;
    
   
    private static final double[] VALID_Y_VALUES = {-2.0, -1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0};
    
    private static final double[] VALID_R_VALUES = {1.0, 1.5, 2.0, 2.5, 3.0};
    
    public CoordinatesValidator(double x, double y, double r) {
        this.x = x;
        this.y = y;
        this.r = r;
    }
    
   
    public boolean checkData() {
        return checkX() && checkY() && checkR();
    }
    
    
    private boolean checkX() {
        return !Double.isNaN(x) && !Double.isInfinite(x) && 
               (x >= X_MIN && x <= X_MAX);
    }

    private boolean checkY() {
        for (double validY : VALID_Y_VALUES) {
            if (Math.abs(y - validY) < 1e-9) { 
                return true;
            }
        }
        return false;
    }
    
    private boolean checkR() {
            for (double validR : VALID_R_VALUES) {
                if (Math.abs(r - validR) < 1e-9) {
                    return true;
                }
            }
            return false;
        }
    
}
