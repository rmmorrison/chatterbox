package net.ryanmorrison.chatterbox.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Member;

@AllArgsConstructor
public class ShoutMemberCountDTO {

    @Getter
    private Member member;

    @Getter
    private long count;
}
