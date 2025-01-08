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
    private long channelId;
    @CreationTimestamp
    private Instant emitted;

    protected QuoteHistory() {
    }

    public QuoteHistory(int id, Quote quote, long channelId, Instant emitted) {
        this.id = id;
        this.quote = quote;
        this.channelId = channelId;
        this.emitted = emitted;
    }

    public int getId() {
        return id;
    }

    public Quote getQuote() {
        return quote;
    }

    public long getChannelId() {
        return channelId;
    }

    public Instant getEmitted() {
        return emitted;
    }

    public static class Builder {
        private Quote quote;
        private long channelId;

        public Builder setQuote(Quote quote) {
            this.quote = quote;
            return this;
        }

        public Builder setChannelId(long channelId) {
            this.channelId = channelId;
            return this;
        }

        public QuoteHistory build() {
            QuoteHistory quoteHistory = new QuoteHistory();
            quoteHistory.quote = this.quote;
            quoteHistory.channelId = this.channelId;
            return quoteHistory;
        }
    }
}
