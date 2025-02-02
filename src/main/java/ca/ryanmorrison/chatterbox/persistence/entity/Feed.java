package ca.ryanmorrison.chatterbox.persistence.entity;

import ca.ryanmorrison.chatterbox.persistence.RSSCacheInvalidator;
import jakarta.persistence.*;

@Entity
@EntityListeners(RSSCacheInvalidator.class)
@Table(name = "rss_feed")
public class Feed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private long channelId;
    private long userId;
    @Column(name = "feed_url")
    private String url;
    private String title;

    protected Feed() {
    }

    public Feed(int id, long channelId, long userId, String url, String title) {
        this.id = id;
        this.channelId = channelId;
        this.userId = userId;
        this.url = url;
        this.title = title;
    }

    public int getId() {
        return id;
    }

    public long getChannelId() {
        return channelId;
    }

    public long getUserId() {
        return userId;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return "Feed{" +
                "id=" + id +
                ", channelId=" + channelId +
                ", userId=" + userId +
                ", url='" + url + '\'' +
                ", title='" + title + '\'' +
                '}';
    }

    public static class Builder {
        private long channelId;
        private long userId;
        private String url;
        private String title;

        public Builder setChannelId(long channelId) {
            this.channelId = channelId;
            return this;
        }

        public Builder setUserId(long userId) {
            this.userId = userId;
            return this;
        }

        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Feed build() {
            Feed feed = new Feed();
            feed.channelId = this.channelId;
            feed.userId = this.userId;
            feed.url = this.url;
            feed.title = this.title;
            return feed;
        }
    }
}
