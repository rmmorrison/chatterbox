package ca.ryanmorrison.chatterbox.integration.nhl.model;

public record TVBroadcast(int id,
                          String market,
                          String countryCode,
                          String network,
                          int sequenceNumber) {
}
