package ca.ryanmorrison.chatterbox.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quotes")
public class Quote {

    @Id
    private long messageId;
    private long authorId;
    private long channelId;
    private String content;

    protected Quote() {
    }

    public Quote(long messageId, long authorId, long channelId, String content) {
        this.messageId = messageId;
        this.authorId = authorId;
        this.channelId = channelId;
        this.content = content;
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
            return new Quote(messageId, authorId, channelId, content);
        }
    }
}
