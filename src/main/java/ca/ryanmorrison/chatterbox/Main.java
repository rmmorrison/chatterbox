package ca.ryanmorrison.chatterbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            Bootstrap.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Startup interrupted.", e);
            System.exit(1);
        } catch (RuntimeException e) {
            log.error("Fatal startup error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private Main() {}
}
