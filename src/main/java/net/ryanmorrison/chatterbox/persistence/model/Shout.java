package net.ryanmorrison.chatterbox.persistence.model;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "shouts")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shout {

    @Getter
    @Setter
    @Id
    @Column(name = "message_id", nullable = false, unique = true)
    private long messageId;

    @Getter
    @Setter
    @Column(name = "channel_id", nullable = false)
    private long channelId;

    @Getter
    @Setter
    @Column(name = "author_id", nullable = false)
    private long authorId;

    @Getter
    @Setter
    @Column(name = "content", nullable = false)
    private String content;
}
