package net.ryanmorrison.chatterbox.model.frinkiac;

public record Frame(long id,
                    String episode,
                    long timestamp) {

    public String getImageURI() {
        return String.format("https://frinkiac.com/img/%s/%d.jpg", episode, timestamp);
    }

    public String getCaptureURI() {
        return String.format("https://frinkiac.com/caption/%s/%d", episode, timestamp);
    }
}
