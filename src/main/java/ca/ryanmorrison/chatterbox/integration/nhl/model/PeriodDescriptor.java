package ca.ryanmorrison.chatterbox.integration.nhl.model;

public record PeriodDescriptor(int number,
                               String periodType,
                               int maxRegulationPeriods) {
}
