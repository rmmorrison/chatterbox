package ca.ryanmorrison.chatterbox.persistence.entity;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "quote")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private long messageId;
    private long authorId;
    private long channelId;
    private String content;
    @OneToMany(mappedBy = "quote", cascade = CascadeType.REMOVE)
    private List<QuoteHistory> histories;

    protected Quote() {
    }

    public Quote(int id, long messageId, long authorId, long channelId, String content, List<QuoteHistory> histories) {
        this.id = id;
        this.messageId = messageId;
        this.authorId = authorId;
        this.channelId = channelId;
        this.content = content;
        this.histories = histories;
    }

    public int getId() {
        return id;
    }

    public long getMessageId() {
        return messageId;
    }

    public long getAuthorId() {
        return authorId;
    }

    public long getChannelId() {
        return channelId;
    }

    public String getContent() {
        return content;
    }

    public List<QuoteHistory> getHistories() {
        return histories;
    }

    @Override
    public String toString() {
        return "Quote{" +
                "messageId=" + messageId +
                ", authorId=" + authorId +
                ", channelId=" + channelId +
                '}';
    }

    public static class Builder {
        private long messageId;
        private long authorId;
        private long channelId;
        private String content;

        public Builder setMessageId(long messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder setAuthorId(long authorId) {
            this.authorId = authorId;
            return this;
        }

        public Builder setChannelId(long channelId) {
            this.channelId = channelId;
            return this;
        }

        public Builder setContent(String content) {
            this.content = content;
            return this;
        }

        public Quote build() {
            Quote quote = new Quote();
            quote.messageId = this.messageId;
            quote.authorId = this.authorId;
            quote.channelId = this.channelId;
            quote.content = this.content;
            return quote;
        }
    }
}
