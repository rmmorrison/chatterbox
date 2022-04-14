package net.ryanmorrison.chatterbox.persistence.model;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "shout_histories")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShoutHistory {

    @Getter
    @Setter
    @Id
    @Column(name = "channel_id", nullable = false, unique = true)
    private long channelId;

    @Getter
    @Setter
    @Column(name = "message_id", nullable = false, unique = true)
    private long messageId;
}
