/**
 * Test Service - Dummy JAR for Easy BPM Code Task Testing
 * 
 * This is a sample service with basic methods that can be executed
 * through the Easy BPM Code Task functionality.
 */
public class TestService {

    /**
     * Simple greeting method
     * @param name The name to greet
     * @return A greeting message
     */
    public static String greet(String name) {
        return "Hello, " + name + "! Welcome to Easy BPM Code Task execution.";
    }

    /**
     * Calculate sum of two numbers
     * @param a First number
     * @param b Second number
     * @return Sum of a and b
     */
    public static int add(int a, int b) {
        return a + b;
    }

    /**
     * Calculate product of two numbers
     * @param a First number
     * @param b Second number
     * @return Product of a and b
     */
    public static int multiply(int a, int b) {
        return a * b;
    }

    /**
     * Process order information
     * @param orderId The order ID
     * @param amount The order amount
     * @return A processed order message
     */
    public static String processOrder(String orderId, double amount) {
        return String.format("Order %s processed successfully. Amount: $%.2f", orderId, amount);
    }

    /**
     * Validate email format
     * @param email The email to validate
     * @return true if email is valid, false otherwise
     */
    public static boolean validateEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }
}
