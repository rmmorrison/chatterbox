package ca.ryanmorrison.chatterbox.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "quote_history")
public class QuoteHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @ManyToOne
    @JoinColumn(name = "quote_id")
    private Quote quote;
    @CreationTimestamp
    private Instant emitted;

    protected QuoteHistory() {
    }

    public QuoteHistory(int id, Quote quote, Instant emitted) {
        this.id = id;
        this.quote = quote;
        this.emitted = emitted;
    }

    public int getId() {
        return id;
    }

    public Quote getQuote() {
        return quote;
    }

    public Instant getEmitted() {
        return emitted;
    }

    public static class Builder {
        private Quote quote;

        public Builder setQuote(Quote quote) {
            this.quote = quote;
            return this;
        }

        public QuoteHistory build() {
            QuoteHistory quoteHistory = new QuoteHistory();
            quoteHistory.quote = this.quote;
            return quoteHistory;
        }
    }
}
