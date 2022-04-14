package net.ryanmorrison.chatterbox.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class ShoutUserCountDTO {

    @Getter
    long authorId;

    @Getter
    long count;
}
