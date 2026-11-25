package emberfx.readiness;

/**
 * One line of the pre-match check.
 *
 * @param name   what was checked, e.g. "Battery"
 * @param status the outcome; SKIP never appears in an evaluated list
 * @param detail a short reason, e.g. "11.9 V, below 12.3 V"
 */
public record CheckResult(String name, Status status, String detail) {
    public enum Status { PASS, WARN, FAIL, SKIP }
}
