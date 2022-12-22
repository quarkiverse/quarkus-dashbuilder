
package io.quarkiverse.dashbuilder.example;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/data")
@ApplicationScoped
public class DataSetResource {

    private static final int BOUND = 100;
    Random r;

    Map<Long, Long> data;

    @PostConstruct
    void init() {
        r = new Random();
        data = new HashMap<>();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Object[][] produceDataSet() {

        var _v = Math.round(r.nextGaussian() * BOUND);
        data.put(System.currentTimeMillis(), _v);

        var n = data.size();
        var dataset = new Object[n][];
        var i = new AtomicInteger();
        data.forEach((k, v) -> {
            dataset[i.getAndIncrement()] = new Object[]{k, v};
        });
        return dataset;
    }

}
