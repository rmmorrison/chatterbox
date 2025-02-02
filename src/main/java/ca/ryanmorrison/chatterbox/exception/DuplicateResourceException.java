package ca.ryanmorrison.chatterbox.exception;

public class DuplicateResourceException extends Exception {

    private String resourceType;
    private String identifier;

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String message, String resourceType, String identifier) {
        super(message);
        this.resourceType = resourceType;
        this.identifier = identifier;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getIdentifier() {
        return identifier;
    }
}
