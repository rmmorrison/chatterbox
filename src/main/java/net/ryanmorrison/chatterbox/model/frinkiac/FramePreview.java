package net.ryanmorrison.chatterbox.model.frinkiac;

import java.util.List;

public record FramePreview(Episode episode,
                           Frame frame,
                           List<Subtitle> subtitles,
                           List<Frame> nearby) {
}
