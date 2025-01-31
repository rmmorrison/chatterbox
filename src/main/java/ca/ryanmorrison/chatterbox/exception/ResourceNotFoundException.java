package ca.ryanmorrison.chatterbox.exception;

public class ResourceNotFoundException extends Exception {

    private String resourceType;
    private String identifier;

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, String resourceType, String identifier) {
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
