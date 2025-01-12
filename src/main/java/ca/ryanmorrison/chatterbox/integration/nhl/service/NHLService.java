package ca.ryanmorrison.chatterbox.integration.nhl.service;

import ca.ryanmorrison.chatterbox.integration.nhl.model.Schedule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.AdapterCodec;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.github.mizosoft.methanol.adapter.jackson.JacksonAdapterFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;

@Component
public class NHLService {

    private final Methanol client;

    public NHLService(@Autowired ObjectMapper objectMapper) {
        this.client = Methanol.newBuilder()
                .baseUri("https://api-web.nhle.com")
                .defaultHeader("Accept", "application/json")
                .requestTimeout(Duration.ofSeconds(20))
                .headersTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .autoAcceptEncoding(true)
                .adapterCodec(AdapterCodec.newBuilder()
                        .encoder(JacksonAdapterFactory.createJsonEncoder(objectMapper))
                        .decoder(JacksonAdapterFactory.createJsonDecoder(objectMapper))
                        .build())
                .build();
    }

    public Schedule getCurrentWeekSchedule() throws IOException, InterruptedException {
        return client.send(MutableRequest.GET("/v1/schedule/" + LocalDate.now()), Schedule.class).body();
    }
}
