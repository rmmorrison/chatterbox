package ca.ryanmorrison.chatterbox.integration.nhl.model;

public record WinningGoalie(int playerId,
                            DefaultReference firstInitial,
                            DefaultReference lastName) {
}
