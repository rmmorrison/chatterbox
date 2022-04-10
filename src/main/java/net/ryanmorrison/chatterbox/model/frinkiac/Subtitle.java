package net.ryanmorrison.chatterbox.model.frinkiac;

public record Subtitle(long id,
                       long representativeTimestamp,
                       String episode,
                       int startTimestamp,
                       int endTimestamp,
                       String content,
                       String language) {
}
