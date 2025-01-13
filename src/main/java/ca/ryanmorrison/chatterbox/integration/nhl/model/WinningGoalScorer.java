package ca.ryanmorrison.chatterbox.integration.nhl.model;

public record WinningGoalScorer(int playerId,
                                 DefaultReference firstInitial,
                                 DefaultReference lastName) {
}
