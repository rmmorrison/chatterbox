package ca.ryanmorrison.chatterbox.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "last_seen")
public class LastSeen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private long userId;
    private Instant lastSeen;

    protected LastSeen() {
    }

    public LastSeen(int id, long userId, Instant lastSeen) {
        this.id = id;
        this.userId = userId;
        this.lastSeen = lastSeen;
    }

    public int getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    @Override
    public String toString() {
        return "LastSeen{" +
                "id=" + id +
                ", userId=" + userId +
                ", lastSeen=" + lastSeen +
                '}';
    }

    public static class Builder {
        private long userId;
        private Instant lastSeen;

        public Builder setUserId(long userId) {
            this.userId = userId;
            return this;
        }

        public Builder setLastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
            return this;
        }

        public LastSeen build() {
            LastSeen object = new LastSeen();
            object.userId = this.userId;
            object.lastSeen = this.lastSeen;
            return object;
        }
    }
}
