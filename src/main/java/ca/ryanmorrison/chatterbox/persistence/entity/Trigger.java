package ca.ryanmorrison.chatterbox.persistence.entity;

import ca.ryanmorrison.chatterbox.persistence.TriggerCacheInvalidator;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@EntityListeners(TriggerCacheInvalidator.class)
@Table(name = "trigger")
public class Trigger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private long channelId;
    private String challenge;
    private String response;
    @CreationTimestamp
    private Instant created;

    protected Trigger() {
    }

    public Trigger(int id, long channelId, String challenge, String response, Instant created) {
        this.id = id;
        this.channelId = channelId;
        this.challenge = challenge;
        this.response = response;
        this.created = created;
    }

    public int getId() {
        return id;
    }

    public long getChannelId() {
        return channelId;
    }

    public String getChallenge() {
        return challenge;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public Instant getCreated() {
        return created;
    }

    @Override
    public String toString() {
        return "Trigger{" +
                "channelId=" + channelId +
                ", challenge='" + challenge + '\'' +
                ", response='" + response + '\'' +
                '}';
    }

    public static class Builder {
        private long channelId;
        private String challenge;
        private String response;

        public Builder channelId(long channelId) {
            this.channelId = channelId;
            return this;
        }

        public Builder challenge(String challenge) {
            this.challenge = challenge;
            return this;
        }

        public Builder response(String response) {
            this.response = response;
            return this;
        }

        public Trigger build() {
            Trigger trigger = new Trigger();
            trigger.channelId = channelId;
            trigger.challenge = challenge;
            trigger.response = response;
            return trigger;
        }
    }
}
