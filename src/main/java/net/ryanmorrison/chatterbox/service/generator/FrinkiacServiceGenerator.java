package net.ryanmorrison.chatterbox.service.generator;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class FrinkiacServiceGenerator {

    private static final String BASE_URL = "https://frinkiac.com/api/";

    private static final ObjectMapper objectMapper = JsonMapper.builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .build();

    private static final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(new OkHttpClient.Builder().build())
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build();

    public static <S> S createService(Class<S> serviceClass) {
        return retrofit.create(serviceClass);
    }
}
