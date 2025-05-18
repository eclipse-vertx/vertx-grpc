package io.vertx.benchmarks.transcoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.transcoding.impl.MessageWeaver;
import io.vertx.grpc.transcoding.impl.config.HttpVariableBinding;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class MessageWeaverBenchmark {

    private Buffer simpleMessage;
    private Buffer complexMessage;
    private List<HttpVariableBinding> simpleBindings;
    private List<HttpVariableBinding> complexBindings;
    private String simpleTranscodingPath;
    private String complexTranscodingPath;

    @Setup
    public void setup() {
        simpleMessage = new JsonObject()
            .put("name", "John Doe")
            .put("age", 30)
            .toBuffer();

        JsonObject address = new JsonObject()
            .put("street", "123 Main St")
            .put("city", "Anytown")
            .put("state", "CA")
            .put("zip", "12345");

        JsonObject contact = new JsonObject()
            .put("email", "john.doe@example.com")
            .put("phone", "555-1234");

        complexMessage = new JsonObject()
            .put("id", "user123")
            .put("name", "John Doe")
            .put("age", 30)
            .put("address", address)
            .put("contact", contact)
            .put("tags", Arrays.asList("customer", "premium", "active"))
            .toBuffer();

        simpleBindings = new ArrayList<>();
        simpleBindings.add(createBinding("id", "user456"));

        complexBindings = new ArrayList<>();
        complexBindings.add(createBinding("id", "user789"));
        complexBindings.add(createBinding("address.city", "New City"));
        complexBindings.add(createBinding("contact.email", "new.email@example.com"));

        simpleTranscodingPath = "";
        complexTranscodingPath = "data.user";
    }

    private HttpVariableBinding createBinding(String path, String value) {
        List<String> fieldPath = new ArrayList<>(Arrays.asList(path.split("\\.")));
        return new HttpVariableBinding(fieldPath, value);
    }

    @Benchmark
    public void benchmarkWeaveRequestSimpleMessageNoBindings(Blackhole blackhole) {
        Buffer result = MessageWeaver.weaveRequestMessage(
            simpleMessage,
            null,
            null
        );
        blackhole.consume(result);
    }

    @Benchmark
    public void benchmarkWeaveRequestSimpleMessageWithBindings(Blackhole blackhole) {
        Buffer result = MessageWeaver.weaveRequestMessage(
            simpleMessage,
            simpleBindings,
            null
        );
        blackhole.consume(result);
    }

    @Benchmark
    public void benchmarkWeaveRequestComplexMessageWithBindings(Blackhole blackhole) {
        Buffer result = MessageWeaver.weaveRequestMessage(
            complexMessage,
            complexBindings,
            null
        );
        blackhole.consume(result);
    }

    @Benchmark
    public void benchmarkWeaveRequestWithTranscodingPath(Blackhole blackhole) {
        Buffer result = MessageWeaver.weaveRequestMessage(
            complexMessage,
            complexBindings,
            complexTranscodingPath
        );
        blackhole.consume(result);
    }

    @Benchmark
    public void benchmarkWeaveResponseSimpleMessage(Blackhole blackhole) {
        Buffer result = MessageWeaver.weaveResponseMessage(
            simpleMessage,
            simpleTranscodingPath
        );
        blackhole.consume(result);
    }

    @Benchmark
    public void benchmarkWeaveResponseComplexMessage(Blackhole blackhole) {
        Buffer result = MessageWeaver.weaveResponseMessage(
            complexMessage,
            complexTranscodingPath
        );
        blackhole.consume(result);
    }
}
