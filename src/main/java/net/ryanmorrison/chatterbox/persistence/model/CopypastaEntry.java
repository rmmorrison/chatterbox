package net.ryanmorrison.chatterbox.persistence.model;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "copypasta")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CopypastaEntry {

    @Getter
    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private int id;

    @Getter
    @Setter
    @Column(name = "channel_id", nullable = false)
    private long channelId;

    @Getter
    @Setter
    @Column(nullable = false)
    private String trigger;

    @Getter
    @Setter
    @Column(nullable = false)
    private String copypasta;
}
