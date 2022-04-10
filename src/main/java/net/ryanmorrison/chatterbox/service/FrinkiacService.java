package net.ryanmorrison.chatterbox.service;

import net.ryanmorrison.chatterbox.model.frinkiac.Frame;
import net.ryanmorrison.chatterbox.model.frinkiac.FramePreview;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

import java.util.List;

public interface FrinkiacService {

    @GET("random")
    Call<FramePreview> fetchRandom();

    @GET("search")
    Call<List<Frame>> search(@Query("q") String query);

    @GET("caption")
    Call<FramePreview> fetchCaption(@Query("e") String episode, @Query("t") long timestamp);
}
